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

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.BLSTestUtil.NegatedKeyPairs;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;

class SyncCommitteeMessagePoolTest {

  private static final NegatedKeyPairs KEYS = BLSTestUtil.negatedKeyPairs(1);

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SyncCommitteeMessageValidator validator = mock(SyncCommitteeMessageValidator.class);

  private final ValidatableSyncCommitteeMessage message =
      ValidatableSyncCommitteeMessage.fromValidator(dataStructureUtil.randomSyncCommitteeMessage());

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<ValidatableSyncCommitteeMessage> subscriber =
      mock(OperationAddedSubscriber.class);

  private final SyncCommitteeMessagePool pool = new SyncCommitteeMessagePool(spec, validator);

  @BeforeEach
  void setUp() {
    when(validator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    message.setSubcommitteeAssignments(SyncSubcommitteeAssignments.NONE);
  }

  @Test
  void shouldNotifySubscriberWhenLocalValidMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(ACCEPT));

    assertThat(pool.addLocal(message)).isCompletedWithValue(ACCEPT);
    verify(subscriber).onOperationAdded(message, ACCEPT, false);
  }

  @Test
  void shouldNotifySubscriberWhenRemoteValidMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(ACCEPT));

    assertThat(pool.addRemote(message, Optional.empty())).isCompletedWithValue(ACCEPT);
    verify(subscriber).onOperationAdded(message, ACCEPT, true);
  }

  @Test
  void shouldNotNotifySubscriberWhenInvalidMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(reject("Bad")));

    assertThat(pool.addLocal(message)).isCompletedWithValue(reject("Bad"));
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldNotNotifySubscriberWhenIgnoredMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(IGNORE));

    assertThat(pool.addLocal(message)).isCompletedWithValue(IGNORE);
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldCreateEmptyContributionWhenNoMessagesAvailable() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(slot, blockRoot, 0);

    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldCreateContributionFromSingleMatchingMessage() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(subcommitteeIndex, 3)
            .build());

    addValidLocal(message);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(message.getSlot(), message.getBeaconBlockRoot(), subcommitteeIndex);

    assertThat(contribution).contains(createContributionFrom(subcommitteeIndex, message));
  }

  @Test
  void shouldCreateContributionAggregatingMultipleMatchingMessages() {
    final int subcommitteeIndex = 3;
    final ValidatableSyncCommitteeMessage message1 =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    message1.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(subcommitteeIndex, 3)
            .build());
    addValidLocal(message1);
    final ValidatableSyncCommitteeMessage message2 =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(
                message1.getSlot(), message1.getBeaconBlockRoot()));
    message2.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(subcommitteeIndex, 2)
            .addAssignment(5, 1)
            .build());
    addValidLocal(message2);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(
            message1.getSlot(), message1.getBeaconBlockRoot(), subcommitteeIndex);

    assertThat(contribution)
        .contains(createContributionFrom(subcommitteeIndex, message1, message2));
  }

  @Test
  void shouldCreateCorrespondingContributionsForEqualMessagesFromDifferentSubnets() {
    final int subcommitteeIndex1 = 1;
    final int subcommitteeIndex2 = 2;
    final SyncCommitteeMessage syncCommitteeMessage =
        dataStructureUtil.randomSyncCommitteeMessage();
    final ValidatableSyncCommitteeMessage message1 =
        ValidatableSyncCommitteeMessage.fromNetwork(syncCommitteeMessage, subcommitteeIndex1);

    SyncSubcommitteeAssignments assignments =
        SyncSubcommitteeAssignments.builder()
            .addAssignment(subcommitteeIndex1, 0)
            .addAssignment(subcommitteeIndex2, 0)
            .build();
    message1.setSubcommitteeAssignments(assignments);
    addValidRemote(message1);

    final Optional<SyncCommitteeContribution> contribution1 =
        pool.createContribution(
            message1.getSlot(), message1.getBeaconBlockRoot(), subcommitteeIndex1);
    assertThat(contribution1).contains(createContributionFrom(subcommitteeIndex1, message1));

    final ValidatableSyncCommitteeMessage message2 =
        ValidatableSyncCommitteeMessage.fromNetwork(syncCommitteeMessage, subcommitteeIndex2);
    message2.setSubcommitteeAssignments(assignments);
    addValidRemote(message2);

    final Optional<SyncCommitteeContribution> contribution2 =
        pool.createContribution(
            message2.getSlot(), message2.getBeaconBlockRoot(), subcommitteeIndex2);
    assertThat(contribution2).contains(createContributionFrom(subcommitteeIndex2, message2));
  }

  @Test
  void shouldIncludeLocalMessageInContributionForAllApplicableSubnets() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(3, 3)
            .addAssignment(5, 3)
            .build());

    addValidLocal(message);

    final UInt64 slot = message.getSlot();
    final Bytes32 blockRoot = message.getBeaconBlockRoot();

    // One message but gets included for all three subnets.
    assertThat(pool.createContribution(slot, blockRoot, 1))
        .contains(createContributionFrom(1, message));
    assertThat(pool.createContribution(slot, blockRoot, 3))
        .contains(createContributionFrom(3, message));
    assertThat(pool.createContribution(slot, blockRoot, 5))
        .contains(createContributionFrom(5, message));
  }

  @Test
  void shouldIncludeRemoteMessageInContributionOnlyOnReceivedSubnet() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromNetwork(
            dataStructureUtil.randomSyncCommitteeMessage(), 1);
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(3, 3)
            .addAssignment(5, 3)
            .build());

    addValidRemote(message);

    final UInt64 slot = message.getSlot();
    final Bytes32 blockRoot = message.getBeaconBlockRoot();

    assertThat(pool.createContribution(slot, blockRoot, 1))
        .contains(createContributionFrom(1, message));
    assertThat(pool.createContribution(slot, blockRoot, 3)).isEmpty();
    assertThat(pool.createContribution(slot, blockRoot, 5)).isEmpty();
  }

  @Test
  public void
      shouldNotAggregateSignaturesFromRepeatedMessagesWhenSameSubcommitteeParticipationId() {
    final SyncCommitteeMessage syncCommitteeMessage =
        dataStructureUtil.randomSyncCommitteeMessage();

    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromNetwork(syncCommitteeMessage, 1);
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(1, 1).build());

    addValidRemote(message);
    // Duplicated message, should not aggregate signature a second time
    addValidRemote(message);

    final BLSSignature signature = message.getMessage().getSignature();
    // Single signature expected on aggregate
    final BLSSignature expectedAggregate = BLS.aggregate(List.of(signature));

    final UInt64 slot = message.getSlot();
    final Bytes32 blockRoot = message.getBeaconBlockRoot();

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(slot, blockRoot, 1);
    assertThat(contribution).isPresent();
    assertThat(contribution.orElseThrow().getSignature()).isEqualTo(expectedAggregate);
  }

  @Test
  void shouldAggregateSignatureMultipleTimesWhenValidatorInSameSubcommitteeMultipleTimes() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(1, 2)
            .addAssignment(1, 3)
            .build());

    addValidLocal(message);

    final BLSSignature signature = message.getMessage().getSignature();
    final BLSSignature expectedAggregate = BLS.aggregate(List.of(signature, signature, signature));

    final UInt64 slot = message.getSlot();
    final Bytes32 blockRoot = message.getBeaconBlockRoot();

    // One message but gets included for all three subnets.
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(slot, blockRoot, 1);
    assertThat(contribution).isPresent();
    assertThat(contribution.orElseThrow().getSignature()).isEqualTo(expectedAggregate);
  }

  @Test
  void shouldExcludeMessagesWhereSlotDoesNotMatch() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValidLocal(message);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(UInt64.ZERO, message.getBeaconBlockRoot(), subcommitteeIndex);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldExcludeMessagesWhereBlockRootDoesNotMatch() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValidLocal(message);

    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(message.getSlot(), blockRoot, subcommitteeIndex);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldExcludeMessagesWhereSubcommitteeIndexDoesNotMatch() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValidLocal(message);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(message.getSlot(), message.getBeaconBlockRoot(), 1);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldPruneMessagesFromOlderSlots() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final int subcommitteeIndex = 2;
    final ValidatableSyncCommitteeMessage message0 =
        createMessageInSlot(blockRoot, subcommitteeIndex, 0);
    final ValidatableSyncCommitteeMessage message1 =
        createMessageInSlot(blockRoot, subcommitteeIndex, 1);
    final ValidatableSyncCommitteeMessage message2 =
        createMessageInSlot(blockRoot, subcommitteeIndex, 2);

    addValidLocal(message0);
    addValidLocal(message1);
    addValidLocal(message2);

    pool.onSlot(UInt64.ZERO);
    assertMessagesPresentForSlots(blockRoot, subcommitteeIndex, 0, 1, 2);

    // Should keep current and previous slot
    pool.onSlot(UInt64.valueOf(2));
    assertMessagesPresentForSlots(blockRoot, subcommitteeIndex, 1, 2);
    assertMessagesAbsentForSlots(blockRoot, subcommitteeIndex, 0);

    // Should be able to remove all messages
    pool.onSlot(UInt64.valueOf(4));
    assertMessagesAbsentForSlots(blockRoot, subcommitteeIndex, 0, 1, 2);
  }

  @Test
  void shouldNotCreateContributionWithInfinitySignature() {
    final int subcommitteeIndex = 3;
    final UInt64 slot = UInt64.valueOf(5);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    addValidLocal(createSignedMessage(slot, blockRoot, subcommitteeIndex, 1, KEYS.p()));
    addValidLocal(createSignedMessage(slot, blockRoot, subcommitteeIndex, 2, KEYS.minusP()));

    assertThat(pool.createContribution(slot, blockRoot, subcommitteeIndex)).isEmpty();
  }

  @Test
  void shouldCreateContributionWhenSignatureIsNotInfinity() {
    final int subcommitteeIndex = 3;
    final UInt64 slot = UInt64.valueOf(5);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final ValidatableSyncCommitteeMessage messageP =
        createSignedMessage(slot, blockRoot, subcommitteeIndex, 1, KEYS.p());
    final ValidatableSyncCommitteeMessage messageMinusP =
        createSignedMessage(slot, blockRoot, subcommitteeIndex, 2, KEYS.minusP());
    final ValidatableSyncCommitteeMessage messageQ =
        createSignedMessage(slot, blockRoot, subcommitteeIndex, 4, KEYS.q());
    addValidLocal(messageP);
    addValidLocal(messageMinusP);
    addValidLocal(messageQ);

    final SyncCommitteeContribution expected =
        createContributionFrom(subcommitteeIndex, messageP, messageMinusP, messageQ);
    assertThat(pool.createContribution(slot, blockRoot, subcommitteeIndex)).contains(expected);
  }

  private void addValidLocal(final ValidatableSyncCommitteeMessage message0) {
    assertThat(pool.addLocal(message0)).isCompletedWithValue(ACCEPT);
  }

  private void addValidRemote(final ValidatableSyncCommitteeMessage message0) {
    assertThat(pool.addRemote(message0, Optional.empty())).isCompletedWithValue(ACCEPT);
  }

  private void assertMessagesPresentForSlots(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot ->
                assertThat(
                        pool.createContribution(UInt64.valueOf(slot), blockRoot, subcommitteeIndex)
                            .orElseThrow()
                            .getSignature()
                            .isInfinity())
                    .isFalse());
  }

  private void assertMessagesAbsentForSlots(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot ->
                assertThat(
                        pool.createContribution(UInt64.valueOf(slot), blockRoot, subcommitteeIndex))
                    .describedAs("Contribution at slot %s should be empty", slot)
                    .isEmpty());
  }

  private ValidatableSyncCommitteeMessage createMessageInSlot(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int slot) {
    return createMessage(
        UInt64.valueOf(slot), blockRoot, subcommitteeIndex, 1, dataStructureUtil.randomSignature());
  }

  private ValidatableSyncCommitteeMessage createMessage(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final int subcommitteeIndex,
      final int participationBit,
      final BLSSignature signature) {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            spec.getGenesisSchemaDefinitions()
                .toVersionAltair()
                .orElseThrow()
                .getSyncCommitteeMessageSchema()
                .create(slot, blockRoot, dataStructureUtil.randomUInt64(), signature));
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(subcommitteeIndex, participationBit)
            .build());
    return message;
  }

  private ValidatableSyncCommitteeMessage createSignedMessage(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final int subcommitteeIndex,
      final int participationBit,
      final BLSKeyPair keyPair) {
    // the pool never verifies signatures; any message shared by all participants will do
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), blockRoot);
    return createMessage(slot, blockRoot, subcommitteeIndex, participationBit, signature);
  }

  private SyncCommitteeContribution createContributionFrom(
      final int subnetId, final ValidatableSyncCommitteeMessage... messages) {
    checkArgument(messages.length > 0, "Must specify at least one message");
    final ValidatableSyncCommitteeMessage template = messages[0];
    final IntSet participantIds = new IntOpenHashSet();
    final List<BLSSignature> blsSignatures = new ArrayList<>();
    for (ValidatableSyncCommitteeMessage message : messages) {
      participantIds.addAll(
          message.getSubcommitteeAssignments().orElseThrow().getParticipationBitIndices(subnetId));
      blsSignatures.add(message.getMessage().getSignature());
    }
    return spec.getSyncCommitteeUtilRequired(template.getSlot())
        .createSyncCommitteeContribution(
            template.getSlot(),
            template.getBeaconBlockRoot(),
            UInt64.valueOf(subnetId),
            participantIds,
            BLS.aggregate(blsSignatures));
  }
}
