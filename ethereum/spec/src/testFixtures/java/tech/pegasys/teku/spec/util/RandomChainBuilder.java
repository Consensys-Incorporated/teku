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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

/** Helper to build a random (not consistent with state-transition rules) sequence of blocks */
public class RandomChainBuilder {
  private final DataStructureUtil dataStructureUtil;
  private final NavigableMap<UInt64, SignedBlockAndState> chain = new TreeMap<>();
  private final Map<Bytes32, SignedBlockAndState> blocksByHash = new HashMap<>();

  public RandomChainBuilder(final DataStructureUtil dataStructureUtil) {
    this.dataStructureUtil = dataStructureUtil;
  }

  public Optional<SignedBlockAndState> getChainHead() {
    return Optional.ofNullable(chain.lastEntry()).map(Map.Entry::getValue);
  }

  public Optional<SignedBeaconBlock> getChainHeadBlock() {
    return getChainHead().map(SignedBlockAndState::getBlock);
  }

  public Optional<SignedBlockAndState> getBlockAndState(final int slot) {
    return getBlockAndState(UInt64.valueOf(slot));
  }

  public Optional<SignedBlockAndState> getBlockAndState(final UInt64 slot) {
    return Optional.ofNullable(chain.get(slot));
  }

  public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 hash) {
    return Optional.ofNullable(blocksByHash.get(hash));
  }

  public Optional<SignedBeaconBlock> getBlock(final int slot) {
    return getBlockAndState(slot).map(SignedBlockAndState::getBlock);
  }

  public Optional<SignedBeaconBlock> getBlock(final UInt64 slot) {
    return getBlockAndState(slot).map(SignedBlockAndState::getBlock);
  }

  public Optional<SignedBeaconBlock> getBlock(final Bytes32 hash) {
    return getBlockAndState(hash).map(SignedBlockAndState::getBlock);
  }

  public List<SignedBlockAndState> getChain() {
    return new ArrayList<>(chain.values());
  }

  public void generateBlocksUpToSlot(final int slot) {
    generateBlocksUpToSlot(UInt64.valueOf(slot));
  }

  public void generateBlocksUpToSlot(final UInt64 slot) {
    final SignedBlockAndState parent = getOrGenerateChainHead();
    checkArgument(slot.isGreaterThan(parent.getSlot()));

    final int count = slot.minusMinZero(parent.getSlot()).intValue();
    dataStructureUtil
        .randomSignedBlockAndStateSequence(parent.getBlock(), count, false)
        .forEach(this::putBlock);
  }

  public void generateBlockAtSlot(final int slot) {
    generateBlockAtSlot(UInt64.valueOf(slot));
  }

  public void generateBlockAtSlot(final UInt64 slot) {
    final SignedBlockAndState parent = getOrGenerateChainHead();
    checkArgument(slot.isGreaterThan(parent.getSlot()));

    final SignedBlockAndState newBlock =
        dataStructureUtil.randomSignedBlockAndState(slot, parent.getRoot());
    putBlock(newBlock);
  }

  private SignedBlockAndState getOrGenerateChainHead() {
    return getChainHead().orElseGet(this::generateGenesis);
  }

  private SignedBlockAndState generateGenesis() {
    final SignedBlockAndState genesis =
        dataStructureUtil.randomSignedBlockAndState(SpecConfig.GENESIS_SLOT);
    putBlock(genesis);
    return genesis;
  }

  private void putBlock(final SignedBlockAndState block) {
    chain.put(block.getSlot(), block);
    blocksByHash.put(block.getRoot(), block);
  }
}
