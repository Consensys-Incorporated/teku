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

package tech.pegasys.teku.storage.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

/** Store extension dedicated to keep unsafe updates package-private */
public abstract class CacheableStore implements UpdatableStore {

  abstract void cacheTimeMillis(UInt64 timeMillis);

  abstract void cacheGenesisTime(UInt64 genesisTime);

  abstract void cacheProposerBoostRoot(Optional<Bytes32> proposerBoostRoot);

  abstract void cacheBlocks(Collection<BlockAndCheckpoints> blockAndCheckpoints);

  abstract void cacheBlockStates(Map<Bytes32, StateAndBlockSummary> stateAndBlockSummaries);

  abstract void cacheBlobSidecars(Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsMap);

  abstract void cacheFinalizedOptimisticTransitionPayload(
      Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload);

  abstract void cleanupCheckpointStates(Predicate<SlotAndBlockRoot> removalCondition);

  abstract void setHighestVotedValidatorIndex(UInt64 highestVotedValidatorIndex);

  abstract void setVote(int index, VoteTracker voteTracker);

  abstract void cacheExecutionPayloads(
      Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloads);
}
