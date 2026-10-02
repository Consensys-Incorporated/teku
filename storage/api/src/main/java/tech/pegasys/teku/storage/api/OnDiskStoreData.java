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

package tech.pegasys.teku.storage.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public record OnDiskStoreData(
    UInt64 time,
    Optional<Checkpoint> anchor,
    UInt64 genesisTime,
    AnchorPoint latestFinalized,
    Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload,
    Checkpoint justifiedCheckpoint,
    Checkpoint bestJustifiedCheckpoint,
    Map<Bytes32, StoredBlockMetadata> blockInformation,
    Map<UInt64, VoteTracker> votes,
    Set<Bytes32> unsatisfiedInclusionListBlocks,
    Optional<Bytes32> latestCanonicalBlockRoot,
    Optional<UInt64> custodyGroupCount) {}
