/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

class VoluntaryExitDataProviderTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final KeyManager keyManager = mock(KeyManager.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100000);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private VoluntaryExitDataProvider provider;

  final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
  final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();
  final Signer signer = mock(Signer.class);
  final Validator validator = new Validator(publicKey1, signer, Optional::empty, false);
  final BLSSignature volExitSignature = dataStructureUtil.randomSignature();

  @BeforeEach
  void setUp() {
    final GenesisData genesisData = new GenesisData(UInt64.ZERO, dataStructureUtil.randomBytes32());
    final Map<BLSPublicKey, Integer> validatorIndices = Map.of(publicKey1, 0, publicKey2, 1);

    when(genesisDataProvider.getGenesisData()).thenReturn(SafeFuture.completedFuture(genesisData));
    when(validatorApiChannel.getValidatorIndices(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(validatorIndices)));
    when(keyManager.getValidatorByPublicKey(validator.getPublicKey()))
        .thenReturn(Optional.of(validator));
    when(signer.signVoluntaryExit(any(), any()))
        .thenReturn(SafeFuture.completedFuture(volExitSignature));

    provider =
        new VoluntaryExitDataProvider(
            spec, keyManager, validatorApiChannel, genesisDataProvider, timeProvider);
  }

  @Test
  void getSignedVoluntaryExit_shouldReturnExpectedResult() {
    final SafeFuture<SignedVoluntaryExit> result =
        provider.getSignedVoluntaryExit(publicKey1, Optional.of(UInt64.valueOf(1234)));

    final SignedVoluntaryExit expected =
        new SignedVoluntaryExit(
            new VoluntaryExit(UInt64.valueOf(1234), UInt64.ZERO), volExitSignature);
    assertThatSafeFuture(result).isCompletedWithValue(expected);
  }

  /**
   * An exit naming a past epoch must still be signed against the chain's current fork, otherwise
   * the beacon node - which validates exits against the state's fork - rejects an exit Teku itself
   * produced.
   */
  @Test
  void getSignedVoluntaryExit_shouldSignPastEpochAgainstCurrentFork() {
    final Spec staggeredSpec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(1000));
    final VoluntaryExitDataProvider staggeredProvider =
        new VoluntaryExitDataProvider(
            staggeredSpec, keyManager, validatorApiChannel, genesisDataProvider, timeProvider);
    final UInt64 currentEpoch = staggeredProvider.calculateCurrentEpoch(UInt64.ZERO);

    safeJoin(staggeredProvider.getSignedVoluntaryExit(publicKey1, Optional.of(UInt64.ZERO)));

    final ArgumentCaptor<ForkInfo> forkInfoCaptor = ArgumentCaptor.forClass(ForkInfo.class);
    verify(signer).signVoluntaryExit(any(), forkInfoCaptor.capture());
    assertThat(forkInfoCaptor.getValue().getFork())
        .isEqualTo(staggeredSpec.getForkSchedule().getFork(currentEpoch))
        .isNotEqualTo(staggeredSpec.getForkSchedule().getFork(UInt64.ZERO));
  }

  /**
   * A future epoch is permitted and must keep signing against the fork scheduled at that epoch, so
   * an exit can be pre-signed for an upcoming fork. Only past epochs are pulled forward.
   */
  @Test
  void getSignedVoluntaryExit_shouldSignFutureEpochAgainstThatEpochsFork() {
    final UInt64 futureForkEpoch = UInt64.valueOf(5000);
    final Spec staggeredSpec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), futureForkEpoch);
    final VoluntaryExitDataProvider staggeredProvider =
        new VoluntaryExitDataProvider(
            staggeredSpec, keyManager, validatorApiChannel, genesisDataProvider, timeProvider);
    final UInt64 currentEpoch = staggeredProvider.calculateCurrentEpoch(UInt64.ZERO);
    assertThat(currentEpoch).isLessThan(futureForkEpoch);

    safeJoin(staggeredProvider.getSignedVoluntaryExit(publicKey1, Optional.of(futureForkEpoch)));

    final ArgumentCaptor<ForkInfo> forkInfoCaptor = ArgumentCaptor.forClass(ForkInfo.class);
    verify(signer).signVoluntaryExit(any(), forkInfoCaptor.capture());
    assertThat(forkInfoCaptor.getValue().getFork())
        .isEqualTo(staggeredSpec.getForkSchedule().getFork(futureForkEpoch))
        .isNotEqualTo(staggeredSpec.getForkSchedule().getFork(currentEpoch));
  }

  @Test
  void getSignedVoluntaryExit_exceptionWhenValidatorNotEnabled() {
    final SafeFuture<SignedVoluntaryExit> result =
        provider.getSignedVoluntaryExit(publicKey2, Optional.of(UInt64.valueOf(1234)));
    assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(BadRequestException.class)
        .hasMessageMatching("Validator (.*) is not in the list of keys managed by this service.");
  }

  @Test
  void getSignedVoluntaryExit_exceptionWhenValidatorIndexDataIsUnavailable() {
    when(validatorApiChannel.getValidatorIndices(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<SignedVoluntaryExit> result =
        provider.getSignedVoluntaryExit(publicKey1, Optional.of(UInt64.valueOf(1234)));

    assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(NodeDataUnavailableException.class)
        .hasMessage("Beacon node returned no validator index data");
  }

  @Test
  void getSignedVoluntaryExit_exceptionWhenKeyNotInIndicesMap() {
    final SafeFuture<SignedVoluntaryExit> result =
        provider.getSignedVoluntaryExit(
            dataStructureUtil.randomPublicKey(), Optional.of(UInt64.valueOf(1234)));
    assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(BadRequestException.class)
        .hasMessageMatching("Public key (.*) is not found.");
  }

  @Test
  void getSignedVoluntaryExit_shouldReturnExpectedResultWithoutEpoch() {
    final SafeFuture<SignedVoluntaryExit> result =
        provider.getSignedVoluntaryExit(publicKey1, Optional.empty());

    final SignedVoluntaryExit expected =
        new SignedVoluntaryExit(
            new VoluntaryExit(UInt64.valueOf(2083), UInt64.ZERO), volExitSignature);
    assertThatSafeFuture(result).isCompletedWithValue(expected);
  }

  @Test
  void calculateCurrentEpoch_shouldReturnEpoch() {
    final UInt64 epoch = provider.calculateCurrentEpoch(UInt64.ZERO);
    final UInt64 expectedEpoch = UInt64.valueOf(2083);
    assertThat(epoch).isEqualTo(expectedEpoch);
  }

  @Test
  void createSignedVoluntaryExit_shouldReturnSignedVoluntaryExit() {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Validator ownedValidator = getValidator(signature);
    final int validatorIndex = 0;
    when(keyManager.getValidatorByPublicKey(ownedValidator.getPublicKey()))
        .thenReturn(Optional.of(ownedValidator));

    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

    final SignedVoluntaryExit signedVoluntaryExit =
        provider.createSignedVoluntaryExit(
            validatorIndex, ownedValidator.getPublicKey(), epoch, forkInfo);

    final SignedVoluntaryExit expected =
        new SignedVoluntaryExit(
            new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex)), signature);
    assertThat(signedVoluntaryExit).isEqualTo(expected);
  }

  @Test
  void createSignedVoluntaryExit_throwsExceptionWhenValidatorNotInList() {
    when(keyManager.getLocalValidatorKeys()).thenReturn(List.of());
    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

    assertThatThrownBy(
            () ->
                provider.createSignedVoluntaryExit(
                    0, dataStructureUtil.randomPublicKey(), epoch, forkInfo))
        .isInstanceOf(BadRequestException.class)
        .hasMessageMatching("Validator (.*) is not in the list of keys managed by this service.");
  }

  @Test
  void createExitForValidator_notFound() {
    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.empty());
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    assertThat(
            provider.getExitForValidator(
                dataStructureUtil.randomPublicKey(), mock(VoluntaryExit.class), forkInfo))
        .isEmpty();
  }

  private Validator getValidator(final BLSSignature signature) {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    Signer signer = mock(Signer.class);
    when(signer.signVoluntaryExit(any(), any())).thenReturn(SafeFuture.completedFuture(signature));
    return new Validator(keyPair1.getPublicKey(), signer, Optional::empty, true);
  }
}
