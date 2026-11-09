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

package tech.pegasys.teku.validator.coordinator;

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ethereum.pow.merkletree.DepositTree;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DepositUtil;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DepositProvider implements Eth1EventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Eth1DataCache eth1DataCache;
  private final StorageUpdateChannel storageUpdateChannel;
  private final Eth1DepositStorageChannel eth1DepositStorageChannel;
  private DepositTree depositMerkleTree;

  private final NavigableMap<UInt64, DepositWithIndex> depositNavigableMap = new TreeMap<>();
  private final Counter depositCounter;
  private final Spec spec;
  private final DepositUtil depositUtil;

  public DepositProvider(
      final MetricsSystem metricsSystem,
      final RecentChainData recentChainData,
      final Eth1DataCache eth1DataCache,
      final StorageUpdateChannel storageUpdateChannel,
      final Eth1DepositStorageChannel eth1DepositStorageChannel,
      final Spec spec) {
    this.recentChainData = recentChainData;
    this.eth1DataCache = eth1DataCache;
    this.storageUpdateChannel = storageUpdateChannel;
    this.eth1DepositStorageChannel = eth1DepositStorageChannel;
    this.spec = spec;
    depositUtil = new DepositUtil(spec);
    depositMerkleTree = new DepositTree();
    depositCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "eth1_deposit_total",
            "Total number of received ETH1 deposits");
  }

  @Override
  public synchronized void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    event.getDeposits().stream()
        .map(depositUtil::convertDepositEventToOperationDeposit)
        .forEach(
            depositWithIndex -> {
              if (!recentChainData.isPreGenesis()) {
                LOG.debug("About to process deposit: {}", depositWithIndex.index());
              }

              depositNavigableMap.put(depositWithIndex.index(), depositWithIndex);
              depositMerkleTree.pushLeaf(depositWithIndex.deposit().getData().hashTreeRoot());
            });
    depositCounter.inc(event.getDeposits().size());
    eth1DataCache.onBlockWithDeposit(
        event.getBlockNumber(),
        new Eth1Data(
            depositMerkleTree.getRoot(),
            UInt64.valueOf(depositMerkleTree.getDepositCount()),
            event.getBlockHash()),
        event.getBlockTimestamp());
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    final BeaconState finalizedState = recentChainData.getStore().getLatestFinalized().getState();
    final UInt64 depositIndex = finalizedState.getEth1DepositIndex();
    pruneDeposits(depositIndex);
    synchronized (this) {
      if (depositIndex.isGreaterThanOrEqualTo(finalizedState.getEth1Data().getDepositCount())
          && depositMerkleTree.getDepositCount()
              >= finalizedState.getEth1Data().getDepositCount().longValue()
          && !depositMerkleTree.isFinalizedWithExecutionBlock(
              finalizedState.getEth1Data().getBlockHash())) {
        final Optional<UInt64> heightOptional =
            eth1DataCache
                .getEth1DataAndHeight(finalizedState.getEth1Data())
                .map(Eth1DataCache.Eth1DataAndHeight::getBlockHeight);
        if (heightOptional.isEmpty()) {
          LOG.debug("Eth1Data height not found in cache. Skipping DepositTree finalization");
          return;
        }
        depositMerkleTree.finalize(finalizedState.getEth1Data(), heightOptional.get());
        depositMerkleTree
            .getSnapshot()
            .ifPresent(
                depositTreeSnapshot -> {
                  LOG.debug("Storing DepositTreeSnapshot: {}", depositTreeSnapshot);
                  storageUpdateChannel
                      .onFinalizedDepositSnapshot(depositTreeSnapshot)
                      .thenCompose(storeResult -> eth1DepositStorageChannel.removeDepositEvents())
                      .finish(
                          throwable ->
                              LOG.error(
                                  "Failed to store snapshot and remove old deposit events",
                                  throwable));
                });
      }
    }
  }

  private synchronized void pruneDeposits(final UInt64 toIndex) {
    depositNavigableMap.headMap(toIndex, false).clear();
  }

  @Override
  public void onEth1Block(
      final UInt64 blockHeight, final Bytes32 blockHash, final UInt64 blockTimestamp) {
    eth1DataCache.onEth1Block(blockHeight, blockHash, blockTimestamp);
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {}

  public synchronized SszList<Deposit> getDeposits(
      final BeaconState state, final Eth1Data eth1Data) {
    final SszListSchema<Deposit, ?> depositsSchema =
        spec.atSlot(state.getSlot()).getSchemaDefinitions().getDepositsSchema();
    return getDepositsWithIndex(state, eth1Data).stream()
        .map(DepositWithIndex::deposit)
        .collect(depositsSchema.collector());
  }

  public synchronized List<DepositWithIndex> getDepositsWithIndex(
      final BeaconState state, final Eth1Data eth1Data) {
    final long maxDeposits = spec.getMaxDeposits(state);
    // no Eth1 deposits needed if already transitioned to the EIP-6110 mechanism
    if (spec.isFormerDepositMechanismDisabled(state)) {
      return emptyList();
    }
    final UInt64 eth1DepositCount;
    if (spec.isEnoughVotesToUpdateEth1Data(state, eth1Data, 1)) {
      eth1DepositCount = eth1Data.getDepositCount();
    } else {
      eth1DepositCount = state.getEth1Data().getDepositCount();
    }
    final UInt64 eth1DepositIndex = state.getEth1DepositIndex();

    final UInt64 eth1PendingDepositCount =
        state
            .toVersionElectra()
            .map(
                stateElectra -> {
                  // EIP-6110
                  final UInt64 eth1DepositIndexLimit =
                      eth1DepositCount.min(stateElectra.getDepositRequestsStartIndex());
                  return eth1DepositIndexLimit.minusMinZero(eth1DepositIndex).min(maxDeposits);
                })
            .orElseGet(
                () -> {
                  // Phase0
                  return eth1DepositCount.minusMinZero(eth1DepositIndex).min(maxDeposits);
                });

    // No deposits to include
    if (eth1PendingDepositCount.isZero()) {
      return emptyList();
    }

    // We need to have all the deposits that can be included in the state available to ensure
    // the generated proofs are valid
    checkRequiredDepositsAvailable(eth1DepositCount, eth1DepositIndex);

    final UInt64 toDepositIndex = eth1DepositIndex.plus(eth1PendingDepositCount);

    return getDepositsWithProof(eth1DepositIndex, toDepositIndex, eth1DepositCount);
  }

  protected synchronized List<DepositWithIndex> getAvailableDeposits() {
    return new ArrayList<>(depositNavigableMap.values());
  }

  protected synchronized Optional<DepositTreeSnapshot> getFinalizedDepositTreeSnapshot() {
    return depositMerkleTree.getSnapshot();
  }

  private void checkRequiredDepositsAvailable(
      final UInt64 eth1DepositCount, final UInt64 eth1DepositIndex) {
    // Note that eth1_deposit_index in the state is actually the number of deposits
    // included, so always one bigger than the index of the last included deposit,
    // hence lastKey().plus(ONE).
    final UInt64 maxPossibleResultingDepositIndex =
        depositNavigableMap.isEmpty() ? eth1DepositIndex : depositNavigableMap.lastKey().plus(ONE);
    if (maxPossibleResultingDepositIndex.isLessThan(eth1DepositCount)) {
      throw MissingDepositsException.missingRange(
          maxPossibleResultingDepositIndex.plus(UInt64.ONE), eth1DepositCount);
    }
  }

  public synchronized int getDepositMapSize() {
    return depositNavigableMap.size();
  }

  /**
   * @param fromDepositIndex inclusive
   * @param toDepositIndex exclusive
   * @param eth1DepositCount number of deposits in the merkle tree according to Eth1Data in state
   */
  private List<DepositWithIndex> getDepositsWithProof(
      final UInt64 fromDepositIndex, final UInt64 toDepositIndex, final UInt64 eth1DepositCount) {
    final AtomicReference<UInt64> expectedDepositIndex = new AtomicReference<>(fromDepositIndex);
    final SszBytes32VectorSchema<?> depositProofSchema = Deposit.SSZ_SCHEMA.getProofSchema();
    if (depositMerkleTree.getDepositCount() < eth1DepositCount.intValue()) {
      throw MissingDepositsException.missingRange(
          UInt64.valueOf(depositMerkleTree.getDepositCount()), eth1DepositCount);
    }
    final DepositTree merkleTree =
        depositMerkleTree.getTreeAtDepositIndex(eth1DepositCount.intValue());
    return depositNavigableMap
        .subMap(fromDepositIndex, true, toDepositIndex, false)
        .values()
        .stream()
        .map(
            depositWithIndex -> {
              if (!depositWithIndex.index().equals(expectedDepositIndex.get())) {
                throw MissingDepositsException.missingRange(
                    expectedDepositIndex.get(), depositWithIndex.index());
              }
              expectedDepositIndex.set(depositWithIndex.index().plus(ONE));
              SszBytes32Vector proof =
                  depositProofSchema.of(merkleTree.getProof(depositWithIndex.index().intValue()));
              return new DepositWithIndex(
                  new Deposit(proof, depositWithIndex.deposit().getData()),
                  depositWithIndex.index());
            })
        .toList();
  }

  @Override
  public synchronized void onInitialDepositTreeSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    this.depositMerkleTree = DepositTree.fromSnapshot(depositTreeSnapshot);
  }
}
