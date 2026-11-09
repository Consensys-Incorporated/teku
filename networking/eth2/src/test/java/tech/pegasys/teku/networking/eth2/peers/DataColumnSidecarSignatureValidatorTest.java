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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.ForkSchedule;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarSignatureValidatorTest {
  private final Spec spec = Mockito.mock(Spec.class);
  private final SpecConfig specConfig = mock(SpecConfig.class);
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalFulu());

  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final BLSSignatureVerifier signatureVerifier = mock(BLSSignatureVerifier.class);

  private final UInt64 slot = UInt64.valueOf(42);
  private final UInt64 epoch = UInt64.valueOf(2);
  private final UInt64 proposerIndex = UInt64.valueOf(10);
  private final Bytes32 genesisValidatorsRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 domain = dataStructureUtil.randomBytes32();
  private final Bytes signingRoot = dataStructureUtil.randomBytes32();
  private final BLSPublicKey proposerPubKey = dataStructureUtil.randomPublicKey();
  private final BeaconState state = mock(BeaconState.class);

  private final SignedBeaconBlockHeader signedBeaconBlockHeader =
      dataStructureUtil.randomSignedBeaconBlockHeader(slot, proposerIndex);

  private final DataColumnSidecar dataColumnSidecar =
      dataStructureUtil.new RandomDataColumnSidecarBuilder()
          .signedBeaconBlockHeader(signedBeaconBlockHeader)
          .build();

  private final DataColumnSidecarSignatureValidator validator =
      new DataColumnSidecarSignatureValidator(spec, chainDataClient);

  @BeforeEach
  void setUp() {
    final ForkSchedule forkSchedule = mock(ForkSchedule.class);
    final Fork fork = mock(Fork.class);

    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(state.getGenesisValidatorsRoot()).thenReturn(genesisValidatorsRoot);

    when(spec.getForkSchedule()).thenReturn(forkSchedule);
    when(spec.computeEpochAtSlot(slot)).thenReturn(epoch);
    when(forkSchedule.getFork(epoch)).thenReturn(fork);
    when(spec.getDomain(Domain.BEACON_PROPOSER, epoch, fork, genesisValidatorsRoot))
        .thenReturn(domain);
    when(spec.getValidatorPubKey(state, proposerIndex)).thenReturn(Optional.of(proposerPubKey));
    when(spec.computeSigningRoot(signedBeaconBlockHeader.getMessage(), domain))
        .thenReturn(signingRoot);

    when(spec.getSpecConfig(any())).thenReturn(specConfig);
    when(specConfig.getBLSSignatureVerifier()).thenReturn(signatureVerifier);
    // valid by default
    when(signatureVerifier.verify(
            proposerPubKey, signingRoot, signedBeaconBlockHeader.getSignature()))
        .thenReturn(true);
  }

  @Test
  void shouldReturnTrueInGloasWhenNoHeaderPresent() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    DataColumnSidecar sidecar = dataStructureUtil.randomDataColumnSidecar();
    final SafeFuture<Boolean> result = validator.validateSignature(sidecar);

    assertThatSafeFuture(result).isCompletedWithValue(true);
    verify(chainDataClient, never()).getBestState();
    verifyNoInteractions(signatureVerifier);
  }

  @Test
  void shouldReturnTrueForValidSignature() {
    final SafeFuture<Boolean> result = validator.validateSignature(dataColumnSidecar);
    assertThatSafeFuture(result).isCompletedWithValue(true);
  }

  @Test
  void shouldReturnFalseForInvalidSignature() {

    when(signatureVerifier.verify(
            proposerPubKey, signingRoot, signedBeaconBlockHeader.getSignature()))
        .thenReturn(false);

    final SafeFuture<Boolean> result = validator.validateSignature(dataColumnSidecar);

    assertThatSafeFuture(result).isCompletedWithValue(false);
  }

  @Test
  void shouldReturnFalseForMissingPublicKey() {
    when(spec.getValidatorPubKey(state, proposerIndex)).thenReturn(Optional.empty());

    final SafeFuture<Boolean> result = validator.validateSignature(dataColumnSidecar);

    assertThatSafeFuture(result).isCompletedWithValue(false);
  }

  @Test
  void shouldCacheValidationForDifferentSidecarsWithSameSignedBlockHeader() {
    final SafeFuture<BeaconState> stateFuture = new SafeFuture<>();
    when(chainDataClient.getBestState()).thenReturn(Optional.of(stateFuture));

    DataColumnSidecar dataColumnSidecar2 =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .signedBeaconBlockHeader(signedBeaconBlockHeader)
            .build();

    final SafeFuture<Boolean> result1 = validator.validateSignature(dataColumnSidecar);
    final SafeFuture<Boolean> result2 = validator.validateSignature(dataColumnSidecar2);

    assertThat(result1).isSameAs(result2);
    assertThatSafeFuture(result1).isNotCompleted();

    stateFuture.complete(state);

    assertThat(result1).isCompletedWithValue(true);
    assertThat(result2).isCompletedWithValue(true);
  }

  @Test
  void shouldFailIfStateIsUnavailable() {
    when(chainDataClient.getBestState()).thenReturn(Optional.empty());
    assertThatSafeFuture(validator.validateSignature(dataColumnSidecar)).isCompletedExceptionally();
  }

  @Test
  void shouldFailIfStateRetrievalFails() {
    when(chainDataClient.getBestState())
        .thenReturn(Optional.of(SafeFuture.failedFuture(new RuntimeException("error"))));
    assertThatSafeFuture(validator.validateSignature(dataColumnSidecar)).isCompletedExceptionally();
  }
}
