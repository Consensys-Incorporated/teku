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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientBootstrapRequestMessage;
import tech.pegasys.teku.statetransition.lightclient.LightClientBootstrapProvider;

public class LightClientBootstrapMessageHandler
    extends PeerRequiredLocalMessageHandler<
        LightClientBootstrapRequestMessage, LightClientBootstrap> {

  private final LightClientBootstrapProvider lightClientBootstrapProvider;

  public LightClientBootstrapMessageHandler(
      final LightClientBootstrapProvider lightClientBootstrapProvider) {
    this.lightClientBootstrapProvider = lightClientBootstrapProvider;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final LightClientBootstrapRequestMessage message,
      final ResponseCallback<LightClientBootstrap> callback) {
    if (!peer.approveRequest()) {
      return;
    }

    final Bytes32 blockRoot = message.getBlockRoot();
    lightClientBootstrapProvider
        .getLightClientBootstrap(blockRoot)
        .finish(
            maybeBootstrap ->
                maybeBootstrap.ifPresentOrElse(
                    callback::respondAndCompleteSuccessfully,
                    () ->
                        callback.completeWithErrorResponse(
                            new ResourceUnavailableException(
                                "No light client bootstrap available for block root "
                                    + blockRoot))),
            err -> handleError(err, callback, "light client bootstrap"));
  }
}
