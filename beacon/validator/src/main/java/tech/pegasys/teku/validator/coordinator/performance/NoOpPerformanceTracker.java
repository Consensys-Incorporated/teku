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

import it.unimi.dsi.fastutil.ints.IntSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;

public class NoOpPerformanceTracker implements PerformanceTracker {

  @Override
  public void start(final UInt64 nodeStartSlot) {}

  @Override
  public void saveProducedAttestation(final Attestation attestation) {}

  @Override
  public void saveProducedBlock(final SlotAndBlockRoot slotAndBlockRoot) {}

  @Override
  public void reportBlockProductionAttempt(final UInt64 epoch) {}

  @Override
  public void saveExpectedSyncCommitteeParticipant(
      final int validatorIndex, final IntSet syncCommitteeIndices, final UInt64 periodEndEpoch) {}

  @Override
  public void saveProducedSyncCommitteeMessage(final SyncCommitteeMessage message) {}

  @Override
  public void onSlot(final UInt64 slot) {}
}
