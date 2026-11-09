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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#executionpayloadenvelopesbyroot-v1">ExecutionPayloadEnvelopesByRoot</a>
 *
 * <p>Count validation is done as part of {@link ExecutionPayloadEnvelopesByRootRequestMessage}
 * schema limits
 */
public class ExecutionPayloadEnvelopesByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;

  public ExecutionPayloadEnvelopesByRootMessageHandler(
      final Spec spec,
      final CombinedChainDataClient combinedChainDataClient,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_root_requests_total",
            "Total number of execution payload envelopes by root requests received",
            "status");
    totalExecutionPayloadEnvelopesRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_root_requested_envelopes_total",
            "Total number of execution payload envelopes requested in accepted execution payload envelopes by root requests from peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final ExecutionPayloadEnvelopesByRootRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    LOG.trace(
        "Peer {} requested {} execution payload envelopes with roots: {}",
        peer.getId(),
        message.size(),
        message);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveExecutionPayloadEnvelopesRequest(callback, message.size());

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(message.size());

    final AtomicInteger sentExecutionPayloadEnvelopes = new AtomicInteger(0);
    final UInt64 currentEpoch =
        combinedChainDataClient.getRecentChainData().getCurrentEpoch().orElse(UInt64.ZERO);
    final UInt64 minServableEpoch =
        currentEpoch
            .minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests())
            .max(spec.getSpecConfig(currentEpoch).getGloasForkEpoch());

    SafeFuture<Void> future = SafeFuture.COMPLETE;

    for (final SszBytes32 beaconBlockRoot : message) {
      future =
          future.thenCompose(
              __ ->
                  respondToBlockRoot(
                      beaconBlockRoot.get(),
                      minServableEpoch,
                      callback,
                      sentExecutionPayloadEnvelopes));
    }

    future.finish(
        () -> {
          if (sentExecutionPayloadEnvelopes.get() != message.size()) {
            peer.adjustExecutionPayloadEnvelopesRequest(
                maybeRequestKey.get(), sentExecutionPayloadEnvelopes.get());
          }
          callback.completeSuccessfully();
        },
        err -> handleError(err, callback, "execution payload envelopes by root"));
  }

  /**
   * Resolves the slot for the requested root before retrieving the envelope. Retrieving an envelope
   * for a finalized block requires reading the blinded envelope from the database and unblinding it
   * with an execution layer round trip, so a root already known to be outside the servable range is
   * rejected up front rather than after that work has been done. A root whose slot can't be
   * resolved still goes through retrieval, where the range is enforced against the envelope itself.
   */
  private SafeFuture<Void> respondToBlockRoot(
      final Bytes32 beaconBlockRoot,
      final UInt64 minServableEpoch,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
      final AtomicInteger sentExecutionPayloadEnvelopes) {
    return combinedChainDataClient
        .getSlotByBlockRoot(beaconBlockRoot)
        .thenCompose(
            maybeSlot -> {
              if (maybeSlot.isPresent()
                  && !isWithinServableRange(maybeSlot.get(), minServableEpoch)) {
                return SafeFuture.COMPLETE;
              }
              return combinedChainDataClient
                  .getExecutionPayloadByBlockRoot(beaconBlockRoot)
                  .thenCompose(
                      maybeExecutionPayloadEnvelope ->
                          maybeExecutionPayloadEnvelope
                              .filter(
                                  envelope ->
                                      isWithinServableRange(
                                          envelope.getMessage().getSlot(), minServableEpoch))
                              .map(
                                  executionPayloadEnvelope ->
                                      callback
                                          .respond(executionPayloadEnvelope)
                                          .thenRun(sentExecutionPayloadEnvelopes::incrementAndGet))
                              .orElse(SafeFuture.COMPLETE));
            });
  }

  private boolean isWithinServableRange(final UInt64 slot, final UInt64 minServableEpoch) {
    return spec.computeEpochAtSlot(slot).isGreaterThanOrEqualTo(minServableEpoch);
  }
}
