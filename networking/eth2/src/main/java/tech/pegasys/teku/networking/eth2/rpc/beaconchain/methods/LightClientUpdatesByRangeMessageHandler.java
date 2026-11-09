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

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientUpdatesByRangeRequestMessage;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;

public class LightClientUpdatesByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<
        LightClientUpdatesByRangeRequestMessage, LightClientUpdate> {

  private final LightClientUpdateStore lightClientUpdateStore;

  public LightClientUpdatesByRangeMessageHandler(
      final LightClientUpdateStore lightClientUpdateStore) {
    this.lightClientUpdateStore = lightClientUpdateStore;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final LightClientUpdatesByRangeRequestMessage message,
      final ResponseCallback<LightClientUpdate> callback) {
    if (!peer.approveRequest()) {
      return;
    }

    final UInt64 startPeriod = message.getStartPeriod();
    final int count = message.getMaximumResponseChunks();
    if (count == 0 || startPeriod.isGreaterThan(UInt64.MAX_VALUE.minus(count))) {
      callback.completeSuccessfully();
      return;
    }

    final Optional<RequestKey> maybeRequestKey =
        peer.approveLightClientUpdatesRequest(callback, count);
    if (maybeRequestKey.isEmpty()) {
      return;
    }

    final List<LightClientUpdate> updates =
        lightClientUpdateStore.getBestUpdatesInRange(startPeriod, count);
    if (updates.size() != count) {
      peer.adjustLightClientUpdatesRequest(maybeRequestKey.get(), updates.size());
    }

    SafeFuture<Void> future = SafeFuture.COMPLETE;
    for (final LightClientUpdate update : updates) {
      future = future.thenCompose(__ -> callback.respond(update));
    }
    future.finish(
        callback::completeSuccessfully,
        err -> handleError(err, callback, "light client updates by range"));
  }
}
