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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record JsonRpcRequest(
    @JsonProperty("jsonrpc") String jsonrpc,
    @JsonProperty("method") String method,
    @JsonProperty("params") List<Object> params,
    @JsonProperty("id") long id) {

  public JsonRpcRequest(final String method, final List<Object> params, final long id) {
    this("2.0", method, params, id);
  }
}
