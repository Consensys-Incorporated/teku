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

package tech.pegasys.teku.networking.p2p.rpc;

import io.netty.buffer.ByteBuf;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * Handles the inbound side of an RPC stream.
 *
 * <p>{@link #processData}, {@link #readComplete} and {@link #closed} are invoked on the stream's
 * event loop. {@link #closed} is invoked once when the stream's handler is removed, after {@link
 * #readComplete}, including after {@link RpcStream#closeAbruptly()}. Decoders and other per-stream
 * state must only be touched from those callbacks and released in {@link #closed}; other threads
 * may only close the stream.
 */
public interface RpcRequestHandler {

  void active(final NodeId nodeId, final RpcStream rpcStream);

  void processData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf data);

  void readComplete(final NodeId nodeId, final RpcStream rpcStream);

  void closed(final NodeId nodeId, final RpcStream rpcStream);

  /** Whether a request has been received on an inbound stream, for diagnostics only. */
  default boolean hasRequestBeenReceived() {
    return false;
  }
}
