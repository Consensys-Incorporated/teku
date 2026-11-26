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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

/** State of an envelopes request being served: response stream and work charged to the peer. */
abstract class ExecutionPayloadEnvelopesRequestState {

  private final ResponseCallback<SignedExecutionPayloadEnvelope> callback;
  private final long requestedCount;
  private long chargedCount = 0;

  protected ExecutionPayloadEnvelopesRequestState(
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback, final long requestedCount) {
    this.callback = callback;
    this.requestedCount = requestedCount;
  }

  boolean isStreamClosed() {
    return callback.isClosed();
  }

  long getRequestedCount() {
    return requestedCount;
  }

  /** Charges for lookups done, whether or not they produced an envelope. */
  void charge(final long count) {
    chargedCount += count;
  }

  long getChargedCount() {
    return chargedCount;
  }

  SafeFuture<Void> sendExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelope executionPayloadEnvelope) {
    return callback.respond(executionPayloadEnvelope);
  }

  SafeFuture<Void> sendExecutionPayloadEnvelopes(
      final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes) {
    SafeFuture<Void> sent = SafeFuture.COMPLETE;
    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      sent = sent.thenCompose(__ -> sendExecutionPayloadEnvelope(executionPayloadEnvelope));
    }
    return sent;
  }
}
