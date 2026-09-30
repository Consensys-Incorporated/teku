/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class InclusionListPayloadAttributesUpdater {

  private static final Logger LOG = LogManager.getLogger();
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ProposersDataManager proposersDataManager;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;

  public InclusionListPayloadAttributesUpdater(
      final ForkChoiceNotifier forkChoiceNotifier,
      final ProposersDataManager proposersDataManager,
      final CombinedChainDataClient combinedChainDataClient,
      final Spec spec) {
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.proposersDataManager = proposersDataManager;
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "UnusedReturnValue"})
  public SafeFuture<Optional<Bytes8>> onInclusionListDue(final UInt64 slot) {
    final UInt64 proposerSlot = slot.increment();
    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenComposeChecked(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.warn(
                    "Ignoring payload attributes refresh with inclusion lists because state at slot {} is not available",
                    slot);
                return SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available"));
              }
              final BeaconState proposerState = spec.processSlots(maybeState.get(), proposerSlot);
              if (proposersDataManager.isProposerForSlot(proposerSlot, proposerState)) {
                return updatePayloadAttributes(slot, proposerState);
              } else {
                LOG.trace(
                    "Not a proposer for slot {}, no payload attributes to refresh with inclusion lists",
                    proposerSlot);
                return SafeFuture.completedFuture(Optional.empty());
              }
            })
        .exceptionally(
            error -> {
              LOG.error("Unable to prepare state for slot {}.", proposerSlot, error);
              return Optional.empty();
            });
  }

  private SafeFuture<Optional<Bytes8>> updatePayloadAttributes(
      final UInt64 slot, final BeaconState state) {
    LOG.info("Refreshing payload attributes with inclusion lists from slot {}", slot);
    final Bytes32 parentRoot = spec.getBlockRootAtSlot(state, slot);
    final UInt64 proposerSlot = slot.increment();
    final Optional<ForkChoiceNode> maybeParentForkChoiceNode = getParentForkChoiceNode(parentRoot);
    if (maybeParentForkChoiceNode.isEmpty()) {
      LOG.warn(
          "Unable to refresh payload attributes with inclusion lists because parent root {} is not the chain head",
          parentRoot);
      return SafeFuture.completedFuture(Optional.empty());
    }
    return forkChoiceNotifier
        .preparePayloadAttributes(maybeParentForkChoiceNode.get(), proposerSlot)
        .thenApply(
            maybeExecutionPayloadContext ->
                extractUpdatedPayloadId(
                    proposerSlot, slot, maybeExecutionPayloadContext, parentRoot))
        .exceptionally(
            error -> {
              LOG.error(
                  "Unable to get payloadId while refreshing payload attributes with inclusion lists (parentRoot: {}, slot {})",
                  parentRoot,
                  proposerSlot,
                  error);
              return Optional.empty();
            });
  }

  private Optional<Bytes8> extractUpdatedPayloadId(
      final UInt64 proposerSlot,
      final UInt64 inclusionListsSlot,
      final Optional<ExecutionPayloadContext> maybeExecutionPayloadContext,
      final Bytes32 parentRoot) {
    if (maybeExecutionPayloadContext.isEmpty()) {
      LOG.warn(
          "Unable to refresh payload attributes with inclusion lists. No execution payload context present for slot {} and parent root {}",
          proposerSlot,
          parentRoot);
      return Optional.empty();
    } else {
      final Optional<Bytes8> maybePayloadId =
          Optional.of(maybeExecutionPayloadContext.get().getPayloadId());
      LOG.info(
          "Refreshed payload attributes with inclusion lists from slot {}. PayloadId: {}",
          inclusionListsSlot,
          maybePayloadId);
      return maybePayloadId;
    }
  }

  private Optional<ForkChoiceNode> getParentForkChoiceNode(final Bytes32 parentRoot) {
    return combinedChainDataClient
        .getChainHead()
        .filter(chainHead -> chainHead.getRoot().equals(parentRoot))
        .map(ChainHead::getForkChoiceNode);
  }
}
