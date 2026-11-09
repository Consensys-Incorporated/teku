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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public class PeerSyncExecutionPayloadListener
    implements RpcResponseListener<SignedExecutionPayloadEnvelope> {

  private final Map<UInt64, SignedExecutionPayloadEnvelope> executionPayloadsBySlot =
      new HashMap<>();

  private final UInt64 startSlot;
  private final UInt64 endSlot;

  public PeerSyncExecutionPayloadListener(final UInt64 startSlot, final UInt64 endSlot) {
    this.startSlot = startSlot;
    this.endSlot = endSlot;
  }

  @Override
  public SafeFuture<?> onResponse(final SignedExecutionPayloadEnvelope executionPayload) {
    final UInt64 slot = executionPayload.getSlot();
    if (slot.isLessThan(startSlot) || slot.isGreaterThan(endSlot)) {
      final String exceptionMessage =
          String.format(
              "Received execution payload with slot %s is not in the requested slot range (%s - %s)",
              slot, startSlot, endSlot);
      return SafeFuture.failedFuture(new IllegalArgumentException(exceptionMessage));
    }
    executionPayloadsBySlot.put(slot, executionPayload);
    return SafeFuture.COMPLETE;
  }

  public Optional<SignedExecutionPayloadEnvelope> getReceivedExecutionPayload(final UInt64 slot) {
    return Optional.ofNullable(executionPayloadsBySlot.get(slot));
  }

  public void clearReceivedExecutionPayloads() {
    executionPayloadsBySlot.clear();
  }
}
