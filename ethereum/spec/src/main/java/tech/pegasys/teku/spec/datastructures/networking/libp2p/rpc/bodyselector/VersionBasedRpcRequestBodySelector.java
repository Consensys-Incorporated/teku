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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public class VersionBasedRpcRequestBodySelector<R extends RpcRequest>
    implements RpcRequestBodySelector<R> {

  private final Function<String, Optional<R>> versionToRequestFunction;

  public VersionBasedRpcRequestBodySelector(
      final Function<String, Optional<R>> versionToRequestFunction) {
    this.versionToRequestFunction = versionToRequestFunction;
  }

  @VisibleForTesting
  public VersionBasedRpcRequestBodySelector(final Map<String, R> requests) {
    checkNotNull(requests);
    this.versionToRequestFunction = (key) -> Optional.ofNullable(requests.getOrDefault(key, null));
  }

  @Override
  public Function<String, Optional<R>> getBody() {
    return versionToRequestFunction;
  }
}
