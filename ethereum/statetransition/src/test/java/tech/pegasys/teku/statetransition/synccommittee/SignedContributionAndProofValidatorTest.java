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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.signatures.SimpleSignatureVerificationService;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SignedContributionAndProofValidatorTest {
  private Spec spec;
  private SpecConfigAltair config;
  private DataStructureUtil dataStructureUtil;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;

  private SyncCommitteeStateUtils syncCommitteeStateUtils;
  private SignedContributionAndProofValidator validator;

  private SignedBlockAndState setupWithDefaultSpec() {
    return setupWithSpec(TestSpecFactory.createMinimalAltair());
  }

  private SignedBlockAndState setupWithSpec(final Spec spec) {
    this.spec = spec;
    config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
    dataStructureUtil = new DataStructureUtil(spec);
    storageSystem =
        InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(10).build();
    chainBuilder = storageSystem.chainBuilder();

    syncCommitteeStateUtils = new SyncCommitteeStateUtils(spec, storageSystem.recentChainData());
    validator =
        new SignedContributionAndProofValidator(
            spec,
            storageSystem.recentChainData(),
            syncCommitteeStateUtils,
            new SimpleSignatureVerificationService(),
            new GossipValidationHelper(
                spec, storageSystem.recentChainData(), storageSystem.getMetricsSystem()));
    return storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldAcceptWhenValid() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldAcceptWhenValidInSlotLastSlotOfSyncCommitteePeriod() {
    setupWithDefaultSpec();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(ZERO);
    final UInt64 period2StartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(ZERO);
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
    // Contributions from the last slot of the sync committee period should be valid according to
    // the next sync committee since that's when they'll be included in blocks
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder(lastSlotOfPeriod).build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreWhenContributionIsNotFromTheCurrentSlot() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    final GossipValidationHelper gossipValidationHelperMock = mock(GossipValidationHelper.class);
    when(gossipValidationHelperMock.isSlotCurrent(message.getMessage().getContribution().getSlot()))
        .thenReturn(false);
    final SignedContributionAndProofValidator signedContributionAndProofValidator =
        new SignedContributionAndProofValidator(
            spec,
            storageSystem.recentChainData(),
            syncCommitteeStateUtils,
            new SimpleSignatureVerificationService(),
            gossipValidationHelperMock);
    storageSystem
        .chainUpdater()
        .setCurrentSlot(message.getMessage().getContribution().getSlot().plus(1));

    assertThat(signedContributionAndProofValidator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldAcceptWhenContributionIsStillValidatingAfterSlotEnds() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    // When we check the current time it's in the right slot
    // But by the time we request the state it's one slot ahead.
    final SignedBlockAndState bestBlock =
        storageSystem
            .chainUpdater()
            .advanceChainUntil(message.getMessage().getContribution().getSlot().plus(1));
    storageSystem.chainUpdater().updateBestBlock(bestBlock);

    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldRejectWhenAggregationBitsAreEmpty() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().removeAllParticipants().build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldAcceptWhenValidButBeaconBlockRootIsUnknown() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder(ZERO, dataStructureUtil.randomBytes32())
            .build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldRejectWhenSubcommitteeIndexIsTooLarge() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .subcommitteeIndex(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT + 1)
            .build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldIgnoreWhenAlreadySeen() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldIgnoreWhenSubsetOfAlreadySeen() {
    setupWithDefaultSpec();
    final SignedContributionAndProof bigMessage =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    final UInt64 firstAggregator = bigMessage.getMessage().getAggregatorIndex();
    final SignedContributionAndProof smallMessage =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            // Probably not a valid aggregator but we should ignore without validating
            .aggregatorIndex(firstAggregator.plus(1))
            .removeAllParticipants()
            .addParticipant(firstAggregator, chainBuilder.getSigner(firstAggregator.intValue()))
            .build();
    assertThat(validator.validate(bigMessage)).isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(smallMessage))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldNotIgnoreWhenSubsetOfAlreadySeenForSameBlockRootInDifferentSlot() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    final UInt64 nextSlot = storageSystem.chainUpdater().getHeadSlot().plus(1);
    final SignedContributionAndProof nextSlotMessage =
        chainBuilder.createValidSignedContributionAndProofBuilder(nextSlot).build();

    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);
    assertThat(validator.validate(nextSlotMessage)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldNotIgnoreWhenSubsetOfAlreadySeenForSameBlockRootAndSlotInDifferentSubcommittee() {
    setupWithDefaultSpec();
    final SignedBlockAndState head = chainBuilder.getLatestBlockAndState();
    final SignedContributionAndProof message1 =
        chainBuilder
            .createValidSignedContributionAndProofBuilder(
                head.getSlot(), head.getRoot(), Optional.of(1))
            .addAllParticipants(validatorIndex -> chainBuilder.getSigner(validatorIndex.intValue()))
            .build();
    final SignedContributionAndProof message2 =
        chainBuilder
            .createValidSignedContributionAndProofBuilder(
                head.getSlot(), head.getRoot(), Optional.of(2))
            .addAllParticipants(validatorIndex -> chainBuilder.getSigner(validatorIndex.intValue()))
            .build();

    assertThat(validator.validate(message1)).isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(message2)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldRejectWhenAggregatorIndexIsUnknown() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .aggregatorIndex(UInt64.valueOf(10_000))
            .build();
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenAggregatorIsNotInSyncCommittee() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .aggregatorNotInSyncSubcommittee()
            .build();
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenSelectionProofIsInvalid() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .selectionProof(dataStructureUtil.randomSignature())
            .build();
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenSignedContributionAndProofSignatureIsInvalid() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .signedContributionAndProofSignature(dataStructureUtil.randomSignature())
            .build();
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectAggregateSignatureIsInvalid() {
    setupWithDefaultSpec();
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .addParticipantSignature(dataStructureUtil.randomSignature())
            .build();
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldHandleBeaconBlockRootBeingFromBeforeCurrentSyncCommitteePeriod() {
    setupWithDefaultSpec();
    final Bytes32 blockRoot = chainBuilder.getLatestBlockAndState().getRoot();
    final UInt64 slot =
        UInt64.valueOf(config.getEpochsPerSyncCommitteePeriod() * config.getSlotsPerEpoch() + 1);
    storageSystem.chainUpdater().advanceChain(slot);

    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .beaconBlockRoot(blockRoot)
            // So the signatures get updated for the new block root
            .resetParticipantsToOnlyAggregator()
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreWhenBeaconBlockRootFromBeforePreviousSyncCommitteePeriod() {
    setupWithDefaultSpec();
    // Would have to process too many empty slots to get a state we can use to validate so ignore
    final Bytes32 blockRoot = chainBuilder.getLatestBlockAndState().getRoot();
    final int slot = 2 * config.getEpochsPerSyncCommitteePeriod() * config.getSlotsPerEpoch() + 1;
    storageSystem.chainUpdater().advanceChain(slot);

    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .beaconBlockRoot(blockRoot)
            .build();
    assertThat(validator.validate(message))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldUseCorrectForkForSignatureVerificationWhenHeadStateIsBeforeNewMilestone() {
    final SignedBlockAndState genesis =
        setupWithSpec(
            TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(ZERO, UInt64.ONE));
    final UInt64 bellatrixStartSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    storageSystem.chainUpdater().setCurrentSlot(bellatrixStartSlot);

    final SignedContributionAndProof message =
        storageSystem
            .chainBuilder()
            .createValidSignedContributionAndProofBuilder(bellatrixStartSlot)
            .beaconBlockRoot(genesis.getRoot())
            // So the signatures get updated for the new block root
            .resetParticipantsToOnlyAggregator()
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldUseCorrectForkForSignatureVerificationWhenSlotIsJustBeforeNewMilestone() {
    final SignedBlockAndState genesis =
        setupWithSpec(
            TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(ZERO, UInt64.ONE));
    final UInt64 lastAltairSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).minus(1);
    storageSystem.chainUpdater().setCurrentSlot(lastAltairSlot);

    final SignedContributionAndProof message =
        storageSystem
            .chainBuilder()
            .createValidSignedContributionAndProofBuilder(lastAltairSlot)
            .beaconBlockRoot(genesis.getRoot())
            // So the signatures get updated for the new block root
            .resetParticipantsToOnlyAggregator()
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
  }
}
