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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;

public class GoodbyeMessageHandler implements LocalMessageHandler<GoodbyeMessage, GoodbyeMessage> {

  private final LabelledMetric<Counter> goodbyeCounter;

  public GoodbyeMessageHandler(final MetricsSystem metricsSystem) {
    goodbyeCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "peer_goodbye_total",
            "Total number of goodbye messages received from peers",
            "reason");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Optional<Eth2Peer> peer,
      final GoodbyeMessage message,
      final ResponseCallback<GoodbyeMessage> callback) {
    final Optional<DisconnectReason> disconnectReason =
        DisconnectReason.fromReasonCode(message.getReason());
    goodbyeCounter.labels(disconnectReason.map(DisconnectReason::name).orElse("UNKNOWN")).inc();
    peer.ifPresent(eth2Peer -> eth2Peer.disconnectImmediately(disconnectReason, false));
    callback.completeSuccessfully();
  }
}
