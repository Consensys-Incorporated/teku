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
import java.util.function.Supplier;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;

/**
 * Serves <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/altair/light-client/p2p-interface.md#getlightclientfinalityupdate">GetLightClientFinalityUpdate</a>
 * and <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/altair/light-client/p2p-interface.md#getlightclientoptimisticupdate">GetLightClientOptimisticUpdate</a>.
 */
public class LightClientLatestUpdateMessageHandler<T>
    extends PeerRequiredLocalMessageHandler<EmptyMessage, T> {

  private final String updateType;
  private final Supplier<Optional<T>> latestUpdate;

  public LightClientLatestUpdateMessageHandler(
      final String updateType, final Supplier<Optional<T>> latestUpdate) {
    this.updateType = updateType;
    this.latestUpdate = latestUpdate;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final EmptyMessage message,
      final ResponseCallback<T> callback) {
    if (!peer.approveRequest()) {
      return;
    }

    latestUpdate
        .get()
        .ifPresentOrElse(
            callback::respondAndCompleteSuccessfully,
            () ->
                callback.completeWithErrorResponse(
                    new ResourceUnavailableException("No " + updateType + " available")));
  }
}
