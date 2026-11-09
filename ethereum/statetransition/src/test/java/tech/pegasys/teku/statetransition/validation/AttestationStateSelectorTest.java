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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class AttestationStateSelectorTest {
  private final Spec spec =
      TestSpecFactory.createDefault(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final ChainUpdater chainUpdater = storageSystem.chainUpdater();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final MetricsSystem metricsSystem = new StubMetricsSystem();

  private final AttestationStateSelector selector =
      new AttestationStateSelector(spec, storageSystem.recentChainData(), metricsSystem);

  @BeforeEach
  void setUp() {
    chainUpdater.initializeGenesis(false);
    chainUpdater.updateBestBlock(chainUpdater.advanceChain(5));
  }

  @Test
  void shouldSelectChainHeadWhenBlockRootIsChainHeadRoot() {
    final SafeFuture<Optional<BeaconState>> result =
        selectStateFor(
            recentChainData.getHeadSlot(), recentChainData.getBestBlockRoot().orElseThrow());
    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(chainBuilder.getLatestBlockAndState().getState());
  }

  @Test
  void shouldRollChainHeadForwardWhenBlockRootIsChainHeadRootButSlotIsTooFarInFuture()
      throws Exception {
    final UInt64 attestationEpoch = spec.computeEpochAtSlot(recentChainData.getHeadSlot()).plus(2);
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(attestationEpoch).plus(3);
    final UInt64 requiredStateSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(attestationEpoch);
    final SafeFuture<Optional<BeaconState>> result =
        selectStateFor(attestationSlot, recentChainData.getBestBlockRoot().orElseThrow());

    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(
            spec.processSlots(chainBuilder.getLatestBlockAndState().getState(), requiredStateSlot));
  }

  @Test
  void shouldUseFinalizedStateIfWithinLookaheadPeriod() {
    // Finalized is currently genesis so can look ahead up to the end of epoch 1
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2)).minus(1);
    // Block root doesn't matter because to be valid the target block must descend from finalized
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final SafeFuture<Optional<BeaconState>> result = selectStateFor(attestationSlot, blockRoot);

    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(
            recentChainData.getStore().getLatestFinalized().getState());
  }

  @Test
  void shouldUseHeadStateIfAttestationInSameEpochAndBlockRootAncestorOfHead() {
    // Advance chain so finalized checkpoint isn't suitable
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(25));
    final SignedBlockAndState chainHead = chainBuilder.getLatestBlockAndState();
    final UInt64 attestationSlot =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(chainHead.getSlot()));
    final Bytes32 blockRoot = chainBuilder.getBlockAtSlot(attestationSlot).getRoot();

    final SafeFuture<Optional<BeaconState>> result = selectStateFor(attestationSlot, blockRoot);

    assertThatSafeFuture(result).isCompletedWithOptionalContaining(chainHead.getState());
  }

  @Test
  void shouldUseHeadStateWhenAttestationIsForAncestorFromEarlierEpochWithinHistoricVector() {
    // Finalized is also suitable but we should prefer using the chain head since it will have the
    // most up to date caches
    chainUpdater.updateBestBlock(
        chainUpdater.advanceChainUntil(spec.computeStartSlotAtEpoch(UInt64.valueOf(2))));
    final SignedBlockAndState chainHead = chainBuilder.getLatestBlockAndState();
    // Attestation slot is just before the chain head epoch
    final UInt64 attestationSlot =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(chainHead.getSlot())).minus(1);
    final Bytes32 blockRoot = chainBuilder.getBlockAtSlot(attestationSlot).getRoot();

    final SafeFuture<Optional<BeaconState>> result = selectStateFor(attestationSlot, blockRoot);

    final BeaconState selectedState = safeJoin(result).orElseThrow();
    assertThat(selectedState).isEqualTo(chainHead.getState());
  }

  @Test
  void shouldNotRegenerateStateForAttestationsWithTargetWhichIsAlreadyJustified()
      throws ExecutionException, InterruptedException {
    // Advance the chain far enough that the finalized checkpoint isn't usable
    chainUpdater.updateBestBlock(
        chainUpdater.advanceChainUntil(spec.computeStartSlotAtEpoch(UInt64.valueOf(3))));

    final ChainBuilder forkBuilder = chainBuilder.fork();

    final SignedBlockAndState forkBlock =
        forkBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(3)).plus(1));

    chainUpdater.saveBlock(forkBlock);

    // advance chain head to epoch 5 and justify epoch 4
    chainUpdater.advanceChainUntil(spec.computeStartSlotAtEpoch(UInt64.valueOf(5)));
    chainUpdater.justifyEpoch(4);

    // Attestation slot of the fork block is before an already justified checkpoint
    final UInt64 attestationSlot = forkBlock.getSlot();
    final Bytes32 blockRoot = forkBlock.getRoot();

    final SafeFuture<Optional<BeaconState>> resultWithCache =
        selectStateFor(attestationSlot, blockRoot);
    assertThatSafeFuture(resultWithCache).isCompleted();
    assertThat(resultWithCache.get().map(BeaconState::getSlot)).contains(UInt64.valueOf(40));
    assertThat(resultWithCache.get().map(BeaconState::hashTreeRoot))
        .isNotEqualTo(recentChainData.getBestState().get().get().hashTreeRoot());

    recentChainData.getStore().clearCaches();

    final SafeFuture<Optional<BeaconState>> resultWithNoCache =
        selectStateFor(attestationSlot, blockRoot);
    assertThatSafeFuture(resultWithNoCache).isCompletedWithEmptyOptional();
  }

  private SafeFuture<Optional<BeaconState>> selectStateFor(
      final UInt64 attestationSlot, final Bytes32 blockRoot) {
    final AttestationData attestationData = attestationFor(attestationSlot, blockRoot);
    final SafeFuture<Optional<BeaconState>> result = selector.getStateToValidate(attestationData);
    final Optional<BeaconState> maybeState = safeJoin(result);
    // Check the state is suitable for verifying the attestation
    maybeState.ifPresent(
        state -> {
          final UInt64 attestationEpoch = attestationData.getTarget().getEpoch();
          final UInt64 earliestSlot =
              spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(attestationEpoch);
          // Must be at or after the earliest slot
          assertThat(state.getSlot()).isGreaterThanOrEqualTo(earliestSlot);

          // State must be from within EPOCHS_PER_HISTORICAL_VECTOR epochs
          final UInt64 stateEpoch = spec.getCurrentEpoch(state);
          assertThat(
                  stateEpoch.minusMinZero(
                      spec.getSpecConfig(stateEpoch).getEpochsPerHistoricalVector()))
              .isLessThanOrEqualTo(attestationEpoch);

          // If the state isn't the finalized state, it must be a descendant of blockRoot
          // (Finalized state is assumed to always be an ancestor)
          if (!state.equals(recentChainData.getStore().getLatestFinalized().getState())) {
            if (state.getSlot().isGreaterThan(attestationSlot)) {
              // State is ahead so blockRoot must be ancestor of state
              assertThat(spec.getBlockRootAtSlot(state, attestationSlot)).isEqualTo(blockRoot);
            } else if (state.getSlot().isLessThan(attestationSlot)) {
              // State is behind so state block root must be ancestor of block root
              final Optional<Bytes32> attestationAncestorBlockRoot =
                  recentChainData
                      .getStore()
                      .getForkChoiceStrategy()
                      .getAncestor(blockRoot, state.getSlot());
              final Bytes32 stateBlockRoot = BeaconBlockHeader.fromState(state).getRoot();
              assertThat(attestationAncestorBlockRoot).contains(stateBlockRoot);
            } else {
              assertThat(BeaconBlockHeader.fromState(state).getRoot()).isEqualTo(blockRoot);
            }
          }
        });
    return result;
  }

  private AttestationData attestationFor(final UInt64 slot, final Bytes32 blockRoot) {
    return dataStructureUtil.randomAttestationData(slot, blockRoot);
  }
}
