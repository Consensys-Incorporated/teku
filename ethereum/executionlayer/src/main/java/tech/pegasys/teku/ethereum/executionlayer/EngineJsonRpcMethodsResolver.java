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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Set;
import java.util.function.Supplier;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.spec.SpecMilestone;

public interface EngineJsonRpcMethodsResolver {

  <T> EngineJsonRpcMethod<T> getMethod(
      EngineApiMethod method, Supplier<SpecMilestone> milestoneSupplier, Class<T> resultType);

  /**
   * Get CL capabilities required for the <a
   * href="https://github.com/ethereum/execution-apis/blob/main/src/engine/common.md#engine_exchangecapabilities">engine_exchangeCapabilities</a>
   * request
   */
  Set<String> getCapabilities();
}
