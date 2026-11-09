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

package tech.pegasys.teku.ethereum.executionclient.methods;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface EngineJsonRpcMethod<T> {

  String getName();

  int getVersion();

  SafeFuture<T> execute(JsonRpcRequestParams params);

  /** Override this method, when an Engine API method has been deprecated */
  default boolean isDeprecated() {
    return false;
  }

  default String getVersionedName() {
    return getVersion() == 0 ? getName() : getName() + "V" + getVersion();
  }
}
