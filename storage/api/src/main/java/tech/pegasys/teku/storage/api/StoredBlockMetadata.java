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

package tech.pegasys.teku.storage.api;

import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;

public class StoredBlockMetadata {
  private final UInt64 blockSlot;
  private final Bytes32 blockRoot;
  private final Bytes32 parentRoot;
  private final Bytes32 stateRoot;
  private final Optional<UInt64> executionBlockNumber;
  private final Optional<Bytes32> executionBlockHash;
  private final Optional<UInt64> executionGasLimit;
  private final Optional<BlockCheckpoints> checkpointEpochs;
  private final Optional<GloasForkChoiceRebuildData> gloasForkChoiceRebuildData;

  public StoredBlockMetadata(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash,
      final Optional<BlockCheckpoints> checkpointEpochs) {
    this(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        executionBlockNumber,
        executionBlockHash,
        Optional.empty(),
        checkpointEpochs,
        Optional.empty());
  }

  public StoredBlockMetadata(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash,
      final Optional<BlockCheckpoints> checkpointEpochs,
      final Optional<GloasForkChoiceRebuildData> gloasForkChoiceRebuildData) {
    this(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        executionBlockNumber,
        executionBlockHash,
        Optional.empty(),
        checkpointEpochs,
        gloasForkChoiceRebuildData);
  }

  public StoredBlockMetadata(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash,
      final Optional<UInt64> executionGasLimit,
      final Optional<BlockCheckpoints> checkpointEpochs,
      final Optional<GloasForkChoiceRebuildData> gloasForkChoiceRebuildData) {
    this.blockSlot = blockSlot;
    this.blockRoot = blockRoot;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.executionBlockNumber = executionBlockNumber;
    this.executionBlockHash = executionBlockHash;
    this.executionGasLimit = executionGasLimit;
    this.checkpointEpochs = checkpointEpochs;
    this.gloasForkChoiceRebuildData = gloasForkChoiceRebuildData;
  }

  public static StoredBlockMetadata fromBlockAndState(
      final Spec spec, final StateAndBlockSummary blockAndState) {
    final BlockCheckpoints epochs = spec.calculateBlockCheckpoints(blockAndState.getState());
    return new StoredBlockMetadata(
        blockAndState.getSlot(),
        blockAndState.getRoot(),
        blockAndState.getParentRoot(),
        blockAndState.getStateRoot(),
        blockAndState.getExecutionBlockNumber(),
        blockAndState.getExecutionBlockHash(),
        blockAndState.getExecutionGasLimit(),
        Optional.of(epochs),
        extractGloasForkChoiceRebuildData(blockAndState));
  }

  public UInt64 getBlockSlot() {
    return blockSlot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public Optional<UInt64> getExecutionBlockNumber() {
    return executionBlockNumber;
  }

  public Optional<Bytes32> getExecutionBlockHash() {
    return executionBlockHash;
  }

  public Optional<UInt64> getExecutionGasLimit() {
    return executionGasLimit;
  }

  public Optional<BlockCheckpoints> getCheckpointEpochs() {
    return checkpointEpochs;
  }

  public Optional<GloasForkChoiceRebuildData> getGloasForkChoiceRebuildData() {
    return gloasForkChoiceRebuildData;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StoredBlockMetadata that = (StoredBlockMetadata) o;
    return Objects.equals(blockSlot, that.blockSlot)
        && Objects.equals(blockRoot, that.blockRoot)
        && Objects.equals(parentRoot, that.parentRoot)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(executionBlockNumber, that.executionBlockNumber)
        && Objects.equals(executionBlockHash, that.executionBlockHash)
        && Objects.equals(executionGasLimit, that.executionGasLimit)
        && Objects.equals(checkpointEpochs, that.checkpointEpochs)
        && Objects.equals(gloasForkChoiceRebuildData, that.gloasForkChoiceRebuildData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        executionBlockNumber,
        executionBlockHash,
        executionGasLimit,
        checkpointEpochs,
        gloasForkChoiceRebuildData);
  }

  public static Optional<GloasForkChoiceRebuildData> extractGloasForkChoiceRebuildData(
      final StateAndBlockSummary blockAndState) {
    return blockAndState
        .getSignedBeaconBlock()
        .map(StoredBlockMetadata::extractGloasForkChoiceRebuildData)
        .orElseGet(() -> extractGloasForkChoiceRebuildData(blockAndState.getState()));
  }

  public static Optional<GloasForkChoiceRebuildData> extractGloasForkChoiceRebuildData(
      final SignedBeaconBlock block) {
    return extractGloasForkChoiceRebuildData(block, Optional.empty());
  }

  public static Optional<GloasForkChoiceRebuildData> extractGloasForkChoiceRebuildData(
      final SignedBeaconBlock block,
      final Optional<SignedBlindedExecutionPayloadEnvelope> maybeBlindedEnvelope) {
    return block
        .getMessage()
        .getBody()
        .getOptionalSignedExecutionPayloadBid()
        .map(SignedExecutionPayloadBid::getMessage)
        .map(
            bid ->
                new GloasForkChoiceRebuildData(
                    bid.getParentBlockHash(),
                    bid.getBlockHash(),
                    maybeBlindedEnvelope.map(
                        envelope -> getPayloadBlockNumber(block.getRoot(), bid, envelope)),
                    maybeBlindedEnvelope.map(
                        envelope -> getPayloadGasLimit(block.getRoot(), bid, envelope))));
  }

  // State-only fallback. latest_execution_payload_bid is the promised (parent_block_hash,
  // block_hash) committed by the beacon block, not proof of reveal — enough for BASE/EMPTY.
  // FULL is skipped: state lacks the EL block_number (only in the envelope), so it will be
  // attached later from a replayed/received envelope.
  private static Optional<GloasForkChoiceRebuildData> extractGloasForkChoiceRebuildData(
      final BeaconState state) {
    return state
        .toVersionGloas()
        .map(BeaconStateGloas::getLatestExecutionPayloadBid)
        .map(
            bid ->
                new GloasForkChoiceRebuildData(
                    bid.getParentBlockHash(),
                    bid.getBlockHash(),
                    Optional.empty(),
                    Optional.empty()));
  }

  private static UInt64 getPayloadBlockNumber(
      final Bytes32 blockRoot,
      final ExecutionPayloadBid bid,
      final SignedBlindedExecutionPayloadEnvelope envelope) {
    return getValidatedPayloadHeader(blockRoot, bid, envelope).getBlockNumber();
  }

  private static UInt64 getPayloadGasLimit(
      final Bytes32 blockRoot,
      final ExecutionPayloadBid bid,
      final SignedBlindedExecutionPayloadEnvelope envelope) {
    return getValidatedPayloadHeader(blockRoot, bid, envelope).getGasLimit();
  }

  private static ExecutionPayloadHeader getValidatedPayloadHeader(
      final Bytes32 blockRoot,
      final ExecutionPayloadBid bid,
      final SignedBlindedExecutionPayloadEnvelope envelope) {
    checkState(
        envelope.getBeaconBlockRoot().equals(blockRoot),
        "Blinded execution payload envelope block root %s does not match stored block root %s",
        envelope.getBeaconBlockRoot(),
        blockRoot);
    final ExecutionPayloadHeader payloadHeader = envelope.getMessage().getPayloadHeader();
    checkState(
        payloadHeader.getParentHash().equals(bid.getParentBlockHash()),
        "Blinded execution payload envelope parent block hash %s does not match GLOAS block bid parent block hash %s for block root %s",
        payloadHeader.getParentHash(),
        bid.getParentBlockHash(),
        blockRoot);
    checkState(
        payloadHeader.getBlockHash().equals(bid.getBlockHash()),
        "Blinded execution payload envelope block hash %s does not match GLOAS block bid block hash %s for block root %s",
        payloadHeader.getBlockHash(),
        bid.getBlockHash(),
        blockRoot);
    checkState(
        payloadHeader.getGasLimit().equals(bid.getGasLimit()),
        "Blinded execution payload envelope gas limit %s does not match GLOAS block bid gas limit %s for block root %s",
        payloadHeader.getGasLimit(),
        bid.getGasLimit(),
        blockRoot);
    return payloadHeader;
  }
}
