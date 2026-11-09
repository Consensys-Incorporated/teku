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
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;

public interface PerformanceTracker extends SlotEventsChannel {

  void start(UInt64 nodeStartSlot);

  void saveProducedAttestation(Attestation attestation);

  void saveProducedBlock(SlotAndBlockRoot slotAndBlockRoot);

  void reportBlockProductionAttempt(UInt64 epoch);

  void saveExpectedSyncCommitteeParticipant(
      int validatorIndex, IntSet syncCommitteeIndices, UInt64 periodEndEpoch);

  void saveProducedSyncCommitteeMessage(SyncCommitteeMessage message);
}
