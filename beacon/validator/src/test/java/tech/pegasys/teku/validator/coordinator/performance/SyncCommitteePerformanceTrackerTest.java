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

package tech.pegasys.teku.validator.coordinator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessageSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class SyncCommitteePerformanceTrackerTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final SchemaDefinitionsAltair schemaDefinitionsAltair =
      SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions());
  private final SyncCommitteeMessageSchema messageSchema =
      schemaDefinitionsAltair.getSyncCommitteeMessageSchema();

  private SyncCommitteePerformanceTracker tracker =
      new SyncCommitteePerformanceTracker(spec, combinedChainDataClient);

  @BeforeEach
  void setUp() {
    when(combinedChainDataClient.getBlockAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldCalculatePerformanceWhenNoSyncCommitteeDutiesExpectedInEpoch() {
    UInt64 epoch = UInt64.ONE;
    assertThat(tracker.calculatePerformance(epoch))
        .isCompletedWithValue(new SyncCommitteePerformance(epoch, 0, 0, 0, 0));
    verifyNoInteractions(combinedChainDataClient);
  }

  @Test
  void shouldExpectMessagesForEachPositionInCommitteeAtEachSlot() {
    tracker.saveExpectedSyncCommitteeParticipant(1, IntSet.of(2, 5, 6), UInt64.valueOf(3));

    final int expected = 3 * spec.getSlotsPerEpoch(UInt64.ZERO);
    assertThat(calculatePerformance(UInt64.ONE).getNumberOfExpectedMessages()).isEqualTo(expected);
  }

  @Test
  void shouldCountNumberOfProducedMessages() {
    tracker.saveExpectedSyncCommitteeParticipant(1, IntSet.of(2, 7, 8), UInt64.valueOf(3));
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 1));
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 3));

    assertThat(calculatePerformance(UInt64.ZERO).getNumberOfProducedMessages()).isEqualTo(6);
  }

  @Test
  void shouldCountNumberOfCorrectMessages() {
    final Spec specSpy = spy(spec);
    tracker = new SyncCommitteePerformanceTracker(specSpy, combinedChainDataClient);
    final Bytes32 slot1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 wrongBlockRoot = dataStructureUtil.randomBytes32();
    final SignedBlockAndState chainHead = dataStructureUtil.randomSignedBlockAndState(3);
    when(combinedChainDataClient.getChainHead())
        .thenReturn(Optional.of(ChainHead.create(chainHead)));
    doReturn(dataStructureUtil.randomBytes32())
        .when(specSpy)
        .getBlockRootAtSlot(eq(chainHead.getState()), any());
    doReturn(slot1Hash).when(specSpy).getBlockRootAtSlot(chainHead.getState(), UInt64.ONE);

    tracker.saveExpectedSyncCommitteeParticipant(1, IntSet.of(2, 7, 8), UInt64.valueOf(3));
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 1, slot1Hash));
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 2, wrongBlockRoot));
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 3, wrongBlockRoot));
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 4, chainHead.getRoot()));

    // Slots 1 and 4 are correct, 2 and 3 are incorrect.
    assertThat(calculatePerformance(UInt64.ZERO).getNumberOfCorrectMessages()).isEqualTo(6);
  }

  @Test
  void shouldCountNumberOfIncludedMessages() {
    tracker.saveExpectedSyncCommitteeParticipant(1, IntSet.of(2, 7, 8), UInt64.valueOf(3));

    // Not included as no block was present
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 1));

    // Block produced, but this message not included
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 2));
    withSyncAggregate(3, 9, 12, 15);

    // Included (along with some other messages)
    tracker.saveProducedSyncCommitteeMessage(createMessage(1, 3));
    withSyncAggregate(4, 2, 7, 8, 9, 12, 15);

    assertThat(calculatePerformance(UInt64.ZERO).getNumberOfIncludedMessages()).isEqualTo(3);
  }

  private SyncCommitteeMessage createMessage(final int validatorIndex, final int slot) {
    return createMessage(validatorIndex, slot, Bytes32.ZERO);
  }

  private SyncCommitteeMessage createMessage(
      final int validatorIndex, final int slot, final Bytes32 blockRoot) {
    return messageSchema.create(
        UInt64.valueOf(slot), blockRoot, UInt64.valueOf(validatorIndex), BLSSignature.empty());
  }

  private SyncCommitteePerformance calculatePerformance(final UInt64 epoch) {
    final SafeFuture<SyncCommitteePerformance> result = tracker.calculatePerformance(epoch);
    assertThat(result).isCompleted();
    return safeJoin(result);
  }

  private void withSyncAggregate(final int slot, final Integer... includedCommitteeIndices) {
    final SignedBeaconBlock signedBlock = mock(SignedBeaconBlock.class);
    final BeaconBlock block = mock(BeaconBlock.class);
    final BeaconBlockBodyAltair blockBody = mock(BeaconBlockBodyAltair.class);
    @SuppressWarnings("UseFastutil")
    final SyncAggregate syncAggregate =
        ((BeaconBlockBodySchemaAltair) schemaDefinitionsAltair.getBeaconBlockBodySchema())
            .getSyncAggregateSchema()
            .create(List.of(includedCommitteeIndices), BLSSignature.empty());
    when(signedBlock.getMessage()).thenReturn(block);
    when(block.getBody()).thenReturn(blockBody);
    when(blockBody.toVersionAltair()).thenReturn(Optional.of(blockBody));
    when(blockBody.getSyncAggregate()).thenReturn(syncAggregate);
    when(combinedChainDataClient.getBlockAtSlotExact(UInt64.valueOf(slot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBlock)));
  }
}
