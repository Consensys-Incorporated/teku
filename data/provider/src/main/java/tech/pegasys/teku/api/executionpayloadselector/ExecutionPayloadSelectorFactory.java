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

package tech.pegasys.teku.api.executionpayloadselector;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ExecutionPayloadSelectorFactory
    extends AbstractSelectorFactory<ExecutionPayloadSelector> {

  private final Spec spec;

  public ExecutionPayloadSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public ExecutionPayloadSelector blockRootSelector(final Bytes32 blockRoot) {
    return () -> client.getExecutionPayloadByBlockRoot(blockRoot).thenApply(this::addMetaData);
  }

  @Override
  public ExecutionPayloadSelector headSelector() {
    return () ->
        client
            .getChainHead()
            .map(
                chainHead ->
                    client
                        .getExecutionPayloadByBlockRoot(chainHead.getRoot())
                        .thenApply(
                            maybeExecutionPayload -> addMetaData(maybeExecutionPayload, chainHead)))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  // TODO-GLOAS: http://github.com/Consensys-Incorporated/teku/issues/9997
  @Override
  public ExecutionPayloadSelector genesisSelector() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public ExecutionPayloadSelector finalizedSelector() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public ExecutionPayloadSelector justifiedSelector() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public ExecutionPayloadSelector slotSelector(final UInt64 slot) {
    return () ->
        client
            .getChainHead()
            .map(
                chainHead ->
                    client
                        .getExecutionPayloadAtSlotExact(slot, chainHead.getRoot())
                        .thenApply(
                            maybeExecutionPayload -> addMetaData(maybeExecutionPayload, chainHead)))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private Optional<ExecutionPayloadAndMetaData> addMetaData(
      final Optional<SignedExecutionPayloadEnvelope> maybeExecutionPayload) {
    // Ensure we use the same chain head when calculating metadata to ensure a consistent view.
    return client
        .getChainHead()
        .flatMap(chainHead -> addMetaData(maybeExecutionPayload, chainHead));
  }

  private Optional<ExecutionPayloadAndMetaData> addMetaData(
      final Optional<SignedExecutionPayloadEnvelope> maybeExecutionPayload,
      final ChainHead chainHead) {
    if (maybeExecutionPayload.isEmpty()) {
      return Optional.empty();
    }
    final SignedExecutionPayloadEnvelope executionPayload = maybeExecutionPayload.get();
    return Optional.of(
        new ExecutionPayloadAndMetaData(
            executionPayload,
            spec.atSlot(executionPayload.getSlot()).getMilestone(),
            chainHead.isOptimistic()
                || client.isOptimisticBlock(executionPayload.getBeaconBlockRoot()),
            client.isFinalized(executionPayload.getSlot())));
  }
}
