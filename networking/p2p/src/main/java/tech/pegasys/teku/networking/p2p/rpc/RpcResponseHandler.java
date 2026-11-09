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

import java.util.Optional;

public interface RpcResponseHandler<T> extends RpcResponseListener<T> {

  /** This method is invoked when all responses have been received. */
  void onCompleted(Optional<? extends Throwable> error);

  default void onCompleted() {
    onCompleted(Optional.empty());
  }

  default void onCompleted(final Throwable t) {
    onCompleted(Optional.of(t));
  }
}
