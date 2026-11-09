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

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

@FunctionalInterface
public interface RpcResponseListener<O> {
  static <T> RpcResponseListener<T> from(final Consumer<T> listener) {
    return (T response) -> SafeFuture.fromRunnable(() -> listener.accept(response));
  }

  /**
   * This method is invoked when a response is received
   *
   * @param response A received response
   * @return A future indicating when processing of the received message is finished
   */
  SafeFuture<?> onResponse(O response);
}
