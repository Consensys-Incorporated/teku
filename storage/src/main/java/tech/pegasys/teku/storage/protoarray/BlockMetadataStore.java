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

package tech.pegasys.teku.storage.protoarray;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public interface BlockMetadataStore {

  boolean contains(Bytes32 blockRoot);

  void processBeaconBlockChain(final Bytes32 head, BeaconBlockProcessor processor);

  void processBeaconBlockChainWhile(
      Bytes32 head, HaltableBeaconBlockProcessor beaconBlockProcessor);

  void processAllBeaconBlocksInOrder(BeaconBlockProcessor beaconBlockProcessor);

  Optional<UInt64> blockSlot(Bytes32 blockRoot);

  Optional<Bytes32> executionBlockHash(Bytes32 beaconBlockRoot);

  Optional<SlotAndBlockRoot> findCommonAncestor(Bytes32 root1, Bytes32 root2);

  void applyUpdate(
      Collection<BlockAndCheckpoints> addedBlocks,
      Map<Bytes32, ExecutionPayloadUpdate> executionPayloads,
      Collection<Bytes32> pulledUpBlocks,
      Map<Bytes32, UInt64> removedBlocks,
      Checkpoint finalizedCheckpoint);

  interface HaltableBeaconBlockProcessor {

    static HaltableBeaconBlockProcessor fromBeaconBlockProcessor(
        final BeaconBlockProcessor processor) {
      return (child, slot, parent) -> {
        processor.process(child, slot, parent);
        return true;
      };
    }

    /**
     * Process parent and child and return a status indicating whether to continue
     *
     * @param childRoot The child root
     * @param slot The child slot
     * @param parentRoot The parent root
     * @return True if processing should continue, false if processing should halt
     */
    boolean process(Bytes32 childRoot, UInt64 slot, Bytes32 parentRoot);
  }

  interface BeaconBlockProcessor {

    /**
     * Process parent and child roots
     *
     * @param childRoot The child root
     * @param slot The child slot
     * @param parentRoot The parent root
     */
    void process(Bytes32 childRoot, UInt64 slot, Bytes32 parentRoot);
  }
}
