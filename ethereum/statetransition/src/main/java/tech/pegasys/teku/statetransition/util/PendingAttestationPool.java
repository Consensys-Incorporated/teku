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

package tech.pegasys.teku.statetransition.util;

import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class PendingAttestationPool implements SlotEventsChannel, FinalizedCheckpointChannel {
  private final PendingPool<ValidatableAttestation> attestationsWaitingForBlock;
  private final PendingPool<ValidatableAttestation> attestationsWaitingForFullPayload;

  PendingAttestationPool(
      final SettableLabelledGauge pendingPoolsSizeGauge,
      final Spec spec,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxAttestationsWaitingForBlock,
      final int maxAttestationsWaitingForFullPayload) {
    attestationsWaitingForBlock =
        new PendingPool<>(
            pendingPoolsSizeGauge,
            "attestations",
            spec,
            historicalSlotTolerance,
            futureSlotTolerance,
            maxAttestationsWaitingForBlock,
            ValidatableAttestation::hashTreeRoot,
            ValidatableAttestation::getDependentBlockRoots,
            ValidatableAttestation::getEarliestSlotForForkChoiceProcessing);
    attestationsWaitingForFullPayload =
        new PendingPool<>(
            pendingPoolsSizeGauge,
            "full_payload_attestations",
            spec,
            historicalSlotTolerance,
            futureSlotTolerance,
            maxAttestationsWaitingForFullPayload,
            ValidatableAttestation::hashTreeRoot,
            attestation -> Set.of(attestation.getData().getBeaconBlockRoot()),
            ValidatableAttestation::getEarliestSlotForForkChoiceProcessing);
  }

  public PendingPool<ValidatableAttestation> getAttestationsWaitingForBlock() {
    return attestationsWaitingForBlock;
  }

  public void addForMissingBlock(final ValidatableAttestation attestation) {
    attestationsWaitingForBlock.add(attestation);
  }

  public void addForMissingFullPayload(final ValidatableAttestation attestation) {
    attestationsWaitingForFullPayload.add(attestation);
  }

  public boolean contains(final ValidatableAttestation attestation) {
    return attestationsWaitingForBlock.contains(attestation)
        || attestationsWaitingForFullPayload.contains(attestation);
  }

  public List<ValidatableAttestation> removeAttestationsWaitingForBlock(
      final Bytes32 beaconBlockRoot) {
    return removeDependents(attestationsWaitingForBlock, beaconBlockRoot);
  }

  public List<ValidatableAttestation> removeAttestationsWaitingForFullPayload(
      final Bytes32 beaconBlockRoot) {
    return removeDependents(attestationsWaitingForFullPayload, beaconBlockRoot);
  }

  public void subscribeRequiredFullPayload(final RequiredFullPayloadSubscriber subscriber) {
    attestationsWaitingForFullPayload.subscribeRequiredBlockRoot(subscriber::onRequiredFullPayload);
  }

  private static List<ValidatableAttestation> removeDependents(
      final PendingPool<ValidatableAttestation> pool, final Bytes32 beaconBlockRoot) {
    final List<ValidatableAttestation> attestations =
        pool.getItemsDependingOn(beaconBlockRoot, false);
    attestations.forEach(pool::remove);
    return attestations;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    attestationsWaitingForBlock.onSlot(slot);
    attestationsWaitingForFullPayload.onSlot(slot);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    attestationsWaitingForBlock.onNewFinalizedCheckpoint(checkpoint, fromOptimisticBlock);
    attestationsWaitingForFullPayload.onNewFinalizedCheckpoint(checkpoint, fromOptimisticBlock);
  }

  public interface RequiredFullPayloadSubscriber {
    void onRequiredFullPayload(Bytes32 beaconBlockRoot);
  }
}
