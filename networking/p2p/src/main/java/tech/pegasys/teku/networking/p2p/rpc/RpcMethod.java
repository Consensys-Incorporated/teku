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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public interface RpcMethod<
    TOutgoingHandler extends RpcRequestHandler,
    TRequest extends RpcRequest,
    RespHandler extends RpcResponseHandler<?>> {

  /**
   * Return a list of supported protocol ids. Protocols are prioritized by ordering, so that the
   * first protocol in the list is preferred over the next protocol.
   *
   * @return A non-empty list of supported protocol ids
   */
  List<String> getIds();

  /**
   * Encodes a request to be sent
   *
   * @param request An outgoing request payload
   * @return The serialized request
   */
  Bytes encodeRequest(TRequest request);

  /**
   * Create a request handler for the selected protocol id, which should be one of the values
   * returned from getId()
   *
   * @param protocolId The protocolId to be handled
   * @return A request handler for the given protocol id
   */
  RpcRequestHandler createIncomingRequestHandler(final String protocolId);

  TOutgoingHandler createOutgoingRequestHandler(
      String protocolId, TRequest request, RespHandler responseHandler);
}
