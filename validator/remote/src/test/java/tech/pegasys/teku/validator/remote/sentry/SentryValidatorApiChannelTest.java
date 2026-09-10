/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.remote.sentry;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@SuppressWarnings("FutureReturnValueIgnored")
class SentryValidatorApiChannelTest {

  private SentryValidatorApiChannel sentryValidatorApiChannel;
  private ValidatorApiChannel dutiesProviderChannel;
  private ValidatorApiChannel blockHandlerChannel;
  private ValidatorApiChannel attestationPublisherChannel;
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @BeforeEach
  void setUp() {
    dutiesProviderChannel = mock(ValidatorApiChannel.class);
    blockHandlerChannel = mock(ValidatorApiChannel.class);
    attestationPublisherChannel = mock(ValidatorApiChannel.class);

    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel,
            Optional.of(blockHandlerChannel),
            Optional.of(attestationPublisherChannel));
  }

  @AfterEach
  void tearDown() {
    reset(dutiesProviderChannel, blockHandlerChannel, attestationPublisherChannel);
  }

  @Test
  void getGenesisDataShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.getGenesisData();

    verify(dutiesProviderChannel).getGenesisData();
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void getValidatorIndicesShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.getValidatorIndices(Collections.emptyList());

    verify(dutiesProviderChannel).getValidatorIndices(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void getValidatorStatusesShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.getValidatorStatuses(Collections.emptyList());

    verify(dutiesProviderChannel).getValidatorStatuses(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void getAttestationDutiesShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.getAttestationDuties(UInt64.ZERO, IntArrayList.of(0));

    verify(dutiesProviderChannel).getAttestationDuties(eq(UInt64.ZERO), eq(IntArrayList.of(0)));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void getSyncCommitteeDutiesShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.getSyncCommitteeDuties(UInt64.ZERO, IntArrayList.of(0));

    verify(dutiesProviderChannel).getSyncCommitteeDuties(eq(UInt64.ZERO), eq(IntArrayList.of(0)));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void getProposerDutiesShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.getProposerDuties(UInt64.ZERO, false);

    verify(dutiesProviderChannel).getProposerDuties(eq(UInt64.ZERO), eq(false));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void createUnsignedBlockShouldUseBlockHandlerChannelWhenAvailable() {
    sentryValidatorApiChannel.createUnsignedBlock(
        UInt64.ZERO, BLSSignature.empty(), Optional.empty(), Optional.of(ONE));

    verify(blockHandlerChannel)
        .createUnsignedBlock(
            eq(UInt64.ZERO),
            eq(BLSSignature.empty()),
            eq(Optional.empty()),
            eq(false),
            eq(Optional.of(BuilderConfig.withBuilderBoostFactor(ONE))));
    verifyNoInteractions(dutiesProviderChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void createUnsignedBlockShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.empty(), Optional.of(attestationPublisherChannel));

    sentryValidatorApiChannel.createUnsignedBlock(
        UInt64.ZERO, BLSSignature.empty(), Optional.empty(), Optional.of(ONE));

    verify(dutiesProviderChannel)
        .createUnsignedBlock(
            eq(UInt64.ZERO),
            eq(BLSSignature.empty()),
            eq(Optional.empty()),
            eq(false),
            eq(Optional.of(BuilderConfig.withBuilderBoostFactor(ONE))));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void createAttestationDataShouldUseDutiesProviderChannel() {
    sentryValidatorApiChannel.createAttestationData(UInt64.ZERO, 0);

    verify(dutiesProviderChannel).createAttestationData(eq(UInt64.ZERO), eq(0));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void createAggregateShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.createAggregate(UInt64.ZERO, Bytes32.ZERO, Optional.of(ONE));

    verify(attestationPublisherChannel)
        .createAggregate(eq(UInt64.ZERO), eq(Bytes32.ZERO), eq(Optional.of(ONE)));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void createAggregateShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.createAggregate(UInt64.ZERO, Bytes32.ZERO, Optional.of(ONE));

    verify(dutiesProviderChannel)
        .createAggregate(eq(UInt64.ZERO), eq(Bytes32.ZERO), eq(Optional.of(ONE)));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void createSyncCommitteeContributionShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.createSyncCommitteeContribution(UInt64.ZERO, 0, Bytes32.ZERO);

    verify(attestationPublisherChannel)
        .createSyncCommitteeContribution(eq(UInt64.ZERO), eq(0), eq(Bytes32.ZERO));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void createSyncCommitteeContributionShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.createSyncCommitteeContribution(UInt64.ZERO, 0, Bytes32.ZERO);

    verify(dutiesProviderChannel)
        .createSyncCommitteeContribution(eq(UInt64.ZERO), eq(0), eq(Bytes32.ZERO));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void subscribeToBeaconCommitteeShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.subscribeToBeaconCommittee(Collections.emptyList());

    verify(attestationPublisherChannel).subscribeToBeaconCommittee(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void subscribeToBeaconCommitteeShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.subscribeToBeaconCommittee(Collections.emptyList());

    verify(dutiesProviderChannel).subscribeToBeaconCommittee(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void subscribeToSyncCommitteeSubnetsShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.subscribeToSyncCommitteeSubnets(Collections.emptyList());

    verify(attestationPublisherChannel)
        .subscribeToSyncCommitteeSubnets(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void subscribeToSyncCommitteeSubnetsShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.subscribeToSyncCommitteeSubnets(Collections.emptyList());

    verify(dutiesProviderChannel).subscribeToSyncCommitteeSubnets(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void subscribeToPersistentSubnetsShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.subscribeToPersistentSubnets(Collections.emptySet());

    verify(attestationPublisherChannel).subscribeToPersistentSubnets(eq(Collections.emptySet()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void subscribeToPersistentSubnetsShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.subscribeToPersistentSubnets(Collections.emptySet());

    verify(dutiesProviderChannel).subscribeToPersistentSubnets(eq(Collections.emptySet()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSignedAttestationsShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.sendSignedAttestations(Collections.emptyList());

    verify(attestationPublisherChannel).sendSignedAttestations(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void sendSignedAttestationsShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.sendSignedAttestations(Collections.emptyList());

    verify(dutiesProviderChannel).sendSignedAttestations(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendAggregateAndProofsShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.sendAggregateAndProofs(Collections.emptyList());

    verify(attestationPublisherChannel).sendAggregateAndProofs(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void sendAggregateAndProofsShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.sendAggregateAndProofs(Collections.emptyList());

    verify(dutiesProviderChannel).sendAggregateAndProofs(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSignedBlockShouldUseBlockHandlerChannelWhenAvailable() {
    final SignedBeaconBlock signedBeaconBlock = mock(SignedBeaconBlock.class);
    sentryValidatorApiChannel.sendSignedBlock(
        signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED);

    verify(blockHandlerChannel)
        .sendSignedBlock(eq(signedBeaconBlock), eq(BroadcastValidationLevel.NOT_REQUIRED));
    verifyNoInteractions(dutiesProviderChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSignedBlockFallbackToDutiesProviderChannel() {
    final SignedBeaconBlock signedBeaconBlock = mock(SignedBeaconBlock.class);
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.empty(), Optional.of(attestationPublisherChannel));

    sentryValidatorApiChannel.sendSignedBlock(
        signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED);

    verify(dutiesProviderChannel)
        .sendSignedBlock(eq(signedBeaconBlock), eq(BroadcastValidationLevel.NOT_REQUIRED));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSyncCommitteeMessagesShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.sendSyncCommitteeMessages(Collections.emptyList());

    verify(attestationPublisherChannel).sendSyncCommitteeMessages(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void sendSyncCommitteeMessagesShouldFallbackToDutiesProviderChannelChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.sendSyncCommitteeMessages(Collections.emptyList());

    verify(dutiesProviderChannel).sendSyncCommitteeMessages(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSignedContributionAndProofsShouldUseAttestationPublisherChannelWhenAvailable() {
    sentryValidatorApiChannel.sendSignedContributionAndProofs(Collections.emptyList());

    verify(attestationPublisherChannel)
        .sendSignedContributionAndProofs(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(dutiesProviderChannel);
  }

  @Test
  void sendSignedContributionAndProofsShouldFallbackToDutiesProviderChannelChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.of(blockHandlerChannel), Optional.empty());

    sentryValidatorApiChannel.sendSignedContributionAndProofs(Collections.emptyList());

    verify(dutiesProviderChannel).sendSignedContributionAndProofs(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void prepareBeaconProposerShouldUseBlockHandlerChannelWhenAvailable() {
    sentryValidatorApiChannel.prepareBeaconProposer(Collections.emptyList());

    verify(blockHandlerChannel).prepareBeaconProposer(eq(Collections.emptyList()));
    verifyNoInteractions(dutiesProviderChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void prepareBeaconProposerFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.empty(), Optional.of(attestationPublisherChannel));

    sentryValidatorApiChannel.prepareBeaconProposer(Collections.emptyList());

    verify(dutiesProviderChannel).prepareBeaconProposer(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void registerValidatorsShouldUseBlockHandlerChannelWhenAvailable() {
    final SszList<SignedValidatorRegistration> sszList = mock(SszList.class);
    sentryValidatorApiChannel.registerValidators(sszList);

    verify(blockHandlerChannel).registerValidators(eq(sszList));
    verifyNoInteractions(dutiesProviderChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void registerValidatorsShouldFallbackToDutiesProviderChannel() {
    final SszList<SignedValidatorRegistration> sszList = mock(SszList.class);
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.empty(), Optional.of(attestationPublisherChannel));

    sentryValidatorApiChannel.registerValidators(sszList);

    verify(dutiesProviderChannel).registerValidators(eq(sszList));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSignedProposerPreferencesShouldUseBlockHandlerChannelWhenAvailable() {
    sentryValidatorApiChannel.sendSignedProposerPreferences(Collections.emptyList());

    verify(blockHandlerChannel).sendSignedProposerPreferences(eq(Collections.emptyList()));
    verifyNoInteractions(dutiesProviderChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void sendSignedProposerPreferencesShouldFallbackToDutiesProviderChannel() {
    sentryValidatorApiChannel =
        new SentryValidatorApiChannel(
            dutiesProviderChannel, Optional.empty(), Optional.of(attestationPublisherChannel));

    sentryValidatorApiChannel.sendSignedProposerPreferences(Collections.emptyList());

    verify(dutiesProviderChannel).sendSignedProposerPreferences(eq(Collections.emptyList()));
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }

  @Test
  void checkValidatorsDoppelgangerShouldUseDutiesChannelWhenAvailable() {
    final List<UInt64> validatorIndices =
        List.of(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64());
    final UInt64 epoch = dataStructureUtil.randomEpoch();
    sentryValidatorApiChannel.getValidatorsLiveness(validatorIndices, epoch);
    verify(dutiesProviderChannel).getValidatorsLiveness(validatorIndices, epoch);
    verifyNoInteractions(blockHandlerChannel);
    verifyNoInteractions(attestationPublisherChannel);
  }
}
