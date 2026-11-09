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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

class SingleRpcRequestBodySelectorTest {

  @Test
  public void anyKeyAppliedToBodySelectionFunctionShouldMapToSingleRequestObject() {
    final RpcRequest request = mock(RpcRequest.class);
    final SingleRpcRequestBodySelector<RpcRequest> singleRpcRequestBodySelector =
        new SingleRpcRequestBodySelector<>(request);

    assertThat(singleRpcRequestBodySelector.getBody().apply("foo")).contains(request);
    assertThat(singleRpcRequestBodySelector.getBody().apply("bar")).contains(request);
    assertThat(singleRpcRequestBodySelector.getBody().apply("_")).contains(request);
    assertThat(singleRpcRequestBodySelector.getBody().apply("")).contains(request);
    assertThat(singleRpcRequestBodySelector.getBody().apply(null)).contains(request);
  }

  @Test
  public void nullRequestObjectWillMapToOptionalEmpty() {
    final SingleRpcRequestBodySelector<RpcRequest> singleRpcRequestBodySelector =
        new SingleRpcRequestBodySelector<>(null);

    assertThat(singleRpcRequestBodySelector.getBody().apply("any")).isEmpty();
  }
}
