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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SyncCommitteeMessageValidatorTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private Spec spec;
  private DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private RecentChainData recentChainData;
  private MetricsSystem metricsSystem;
  private GossipValidationHelper gossipValidationHelper;

  private SyncCommitteeMessageValidator validator;

  private void setupWithDefaultSpec() {
    setupWithSpec(TestSpecFactory.createMinimalAltair());
  }

  private SignedBlockAndState setupWithSpec(final Spec spec) {
    this.spec = spec;

    dataStructureUtil = new DataStructureUtil(spec);
    storageSystem =
        InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(17).build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
    metricsSystem = storageSystem.getMetricsSystem();
    gossipValidationHelper = new GossipValidationHelper(spec, recentChainData, metricsSystem);

    validator =
        new SyncCommitteeMessageValidator(
            spec,
            recentChainData,
            new SyncCommitteeStateUtils(spec, recentChainData),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE),
            gossipValidationHelper);

    return storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldAcceptWhenValid() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidatableSyncCommitteeMessage validatableMessage =
        ValidatableSyncCommitteeMessage.fromNetwork(message, validSubnetId);

    assertThat(validator.validate(validatableMessage)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validatableMessage.getSubcommitteeAssignments()).contains(assignments);
  }

  @Test
  void shouldAcceptWhenValidInSlotLastSlotOfSyncCommitteePeriod() {
    setupWithDefaultSpec();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
    final UInt64 period2StartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 period3StartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period2StartEpoch);
    final UInt64 period2StartSlot = spec.computeStartSlotAtEpoch(period2StartEpoch);
    final UInt64 period3StartSlot = spec.computeStartSlotAtEpoch(period3StartEpoch);
    final UInt64 lastSlotOfPeriod = period3StartSlot.minus(1);

    // The first two sync committees are the same so advance the chain into the second period
    // so we can test going into the third period which is actually different
    final SignedBlockAndState chainHead =
        storageSystem.chainUpdater().advanceChainUntil(period2StartSlot);
    storageSystem.chainUpdater().setCurrentSlot(lastSlotOfPeriod);
    storageSystem.chainUpdater().updateBestBlock(chainHead);

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(lastSlotOfPeriod, chainHead.getRoot());
    final SyncSubcommitteeAssignments assignments =
        syncCommitteeUtil.getSubcommitteeAssignments(
            chainHead.getState(),
            syncCommitteeUtil.getEpochForDutiesAtSlot(lastSlotOfPeriod),
            message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidatableSyncCommitteeMessage validatableMessage =
        ValidatableSyncCommitteeMessage.fromNetwork(message, validSubnetId);
    timeProvider.advanceTimeByMillis(
        spec.computeTimeMillisAtSlot(
                lastSlotOfPeriod, secondsToMillis(recentChainData.getGenesisTime()))
            .longValue());

    assertThat(validator.validate(validatableMessage)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validatableMessage.getSubcommitteeAssignments()).contains(assignments);
  }

  @Test
  void shouldRejectWhenAltairIsNotActiveAtSlot() {
    setupWithDefaultSpec();
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    final SyncCommitteeMessageValidator validator =
        new SyncCommitteeMessageValidator(
            phase0Spec,
            recentChainData,
            new SyncCommitteeStateUtils(phase0Spec, recentChainData),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE),
            gossipValidationHelper);
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenNotForTheCurrentSlot() {
    setupWithDefaultSpec();
    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            latestBlockAndState.getSlot().plus(1), latestBlockAndState.getRoot());

    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessages() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldAllowDuplicateMessagesForDistinctSubnets() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 2, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForSameSubnet() {
    setupWithDefaultSpec();

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            storageSystem.recentChainData().getHeadSlot(),
            storageSystem.recentChainData().getBestBlockRoot().get());

    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(1, 2))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForLocalValidatorsInMultipleSubnets() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromValidatorSpy(message, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromValidatorSpy(message, IntSet.of(2, 1))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForLocalValidatorsWhenReceivedAgainFromAnySubnet() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromValidatorSpy(message, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(2, 1))))
        .isCompletedWithValue(IGNORE);
    assertThat(validator.validate(fromNetworkSpy(message, 2, IntSet.of(2, 1))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldAcceptBetterMessageIfNotTheFirst() {
    setupWithDefaultSpec();
    // slot 2
    storageSystem.chainUpdater().setCurrentSlot(UInt64.valueOf(2));
    timeProvider.advanceTimeByMillis(spec.getSlotDurationMillis(UInt64.ONE) * 2L);

    storageSystem.chainUpdater().advanceChain();
    storageSystem.chainUpdater().advanceChain();

    final SyncCommitteeMessage message =
        chainBuilder.createValidSyncCommitteeMessageAtParentBlockRoot();

    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromNetwork(message, 0)))
        .isCompletedWithValue(ACCEPT);

    final SyncCommitteeMessage message2 = chainBuilder.createValidSyncCommitteeMessage();
    assertThat(validator.validate(fromValidatorSpy(message2, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldAcceptWhenValidButBeaconBlockIsUnknown() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            chainBuilder.getLatestSlot(), dataStructureUtil.randomBytes32());
    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldRejectWhenValidatorIsNotInSyncCommittee() {
    setupWithSpec(
        TestSpecFactory.createAltair(
            SpecConfigLoader.loadConfig(
                "minimal",
                phase0Builder ->
                    phase0Builder
                        .altairForkEpoch(UInt64.ZERO)
                        .altairBuilder(altairBuilder -> altairBuilder.syncCommitteeSize(16)))));
    final SignedBlockAndState target = chainBuilder.getLatestBlockAndState();
    final BeaconStateAltair state = BeaconStateAltair.required(target.getState());
    final List<SszPublicKey> committeePubkeys =
        state.getCurrentSyncCommittee().getPubkeys().asList();
    // Find a validator key that isn't in the sync committee
    final BLSPublicKey validatorPublicKey =
        chainBuilder.getValidatorKeys().stream()
            .map(BLSKeyPair::getPublicKey)
            .filter(publicKey -> !committeePubkeys.contains(new SszPublicKey(publicKey)))
            .findAny()
            .orElseThrow();

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            target.getSlot(), target.getRoot(), state, validatorPublicKey);

    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenReceivedOnIncorrectSubnet() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();
    // 9 is never a valid subnet
    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromNetwork(message, 9)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenValidatorIsUnknown() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage template = chainBuilder.createValidSyncCommitteeMessage();
    final SyncCommitteeMessage message =
        template
            .getSchema()
            .create(
                template.getSlot(),
                template.getBeaconBlockRoot(),
                // There's only 16 validators
                UInt64.valueOf(25),
                template.getSignature());
    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenSignatureIsInvalid() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage template = chainBuilder.createValidSyncCommitteeMessage();
    final SyncCommitteeMessage message =
        template
            .getSchema()
            .create(
                template.getSlot(),
                template.getBeaconBlockRoot(),
                template.getValidatorIndex(),
                dataStructureUtil.randomSignature());
    assertThat(validator.validate(ValidatableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldUseCorrectForkForSignatureVerificationWhenHeadStateIsBeforeNewMilestone() {
    final SignedBlockAndState genesis =
        setupWithSpec(
            TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(UInt64.ZERO, UInt64.ONE));
    final UInt64 bellatrixStartSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    storageSystem.chainUpdater().setCurrentSlot(bellatrixStartSlot);
    timeProvider.advanceTimeBySeconds(
        spec.computeTimeAtSlot(genesis.getState(), bellatrixStartSlot)
            .minus(timeProvider.getTimeInSeconds())
            .longValue());

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(bellatrixStartSlot, genesis.getRoot());
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidatableSyncCommitteeMessage validatableMessage =
        ValidatableSyncCommitteeMessage.fromNetwork(message, validSubnetId);
    assertThat(validator.validate(validatableMessage)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldUseCorrectForkForSignatureVerificationWhenSlotIsJustBeforeNewMilestone() {
    final SignedBlockAndState genesis =
        setupWithSpec(
            TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(UInt64.ZERO, UInt64.ONE));
    final UInt64 lastAltairSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).minus(1);
    storageSystem.chainUpdater().setCurrentSlot(lastAltairSlot);
    timeProvider.advanceTimeBySeconds(
        spec.computeTimeAtSlot(genesis.getState(), lastAltairSlot)
            .minus(timeProvider.getTimeInSeconds())
            .longValue());

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(lastAltairSlot, genesis.getRoot());
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidatableSyncCommitteeMessage validatableMessage =
        ValidatableSyncCommitteeMessage.fromNetwork(message, validSubnetId);
    assertThat(validator.validate(validatableMessage)).isCompletedWithValue(ACCEPT);
  }

  private ValidatableSyncCommitteeMessage fromValidatorSpy(
      final SyncCommitteeMessage message, final IntSet subcommitteeIds) {
    final ValidatableSyncCommitteeMessage validatableMessage =
        ValidatableSyncCommitteeMessage.fromValidator(message);
    return createSpy(validatableMessage, subcommitteeIds);
  }

  private ValidatableSyncCommitteeMessage fromNetworkSpy(
      final SyncCommitteeMessage message,
      final int receivedSubnetId,
      final IntSet subcommitteeIds) {
    final ValidatableSyncCommitteeMessage validatableMessage =
        ValidatableSyncCommitteeMessage.fromNetwork(message, receivedSubnetId);
    return createSpy(validatableMessage, subcommitteeIds);
  }

  private ValidatableSyncCommitteeMessage createSpy(
      final ValidatableSyncCommitteeMessage validatableMessage, final IntSet subcommitteeIds) {
    // Create spies
    final ValidatableSyncCommitteeMessage validatableMessageSpy = spy(validatableMessage);
    validatableMessage.calculateAssignments(spec, chainBuilder.getLatestBlockAndState().getState());
    SyncSubcommitteeAssignments assignments =
        validatableMessage.getSubcommitteeAssignments().orElseThrow();
    SyncSubcommitteeAssignments assignmentsSpy = spy(assignments);

    // Overwrite some functionality
    doReturn(assignmentsSpy).when(validatableMessageSpy).calculateAssignments(any(), any());
    doReturn(Optional.of(assignmentsSpy)).when(validatableMessageSpy).getSubcommitteeAssignments();
    doReturn(subcommitteeIds).when(assignmentsSpy).getAssignedSubcommittees();

    return validatableMessageSpy;
  }
}
