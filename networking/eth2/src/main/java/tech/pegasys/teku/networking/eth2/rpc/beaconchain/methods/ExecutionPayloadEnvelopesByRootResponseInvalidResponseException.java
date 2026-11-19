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

import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class ExecutionPayloadEnvelopesByRootResponseInvalidResponseException
    extends InvalidResponseException {

  public ExecutionPayloadEnvelopesByRootResponseInvalidResponseException(
      final Peer peer, final InvalidResponseType invalidResponseType) {
    super(
        String.format(
            "Received invalid response from peer %s: %s", peer, invalidResponseType.describe()));
  }

  public enum InvalidResponseType {
    ENVELOPE_UNEXPECTED_BEACON_BLOCK_ROOT(
        "Envelope beacon block root is not within requested roots");

    private final String description;

    InvalidResponseType(final String description) {
      this.description = description;
    }

    public String describe() {
      return description;
    }
  }
}
