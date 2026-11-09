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

package tech.pegasys.teku.ethereum.executionclient;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

public enum BuilderApiMethod {
  REGISTER_VALIDATOR("eth/v1/builder/validators"),
  GET_HEADER("eth/v1/builder/header/:slot/:parent_hash/:pubkey"),
  GET_PAYLOAD("eth/v1/builder/blinded_blocks"),
  GET_PAYLOAD_V2("eth/v2/builder/blinded_blocks"),
  GET_STATUS("eth/v1/builder/status");

  private final String path;

  BuilderApiMethod(final String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  public String resolvePath(final Map<String, String> urlParams) {
    String result = path;
    for (final Map.Entry<String, String> param : urlParams.entrySet()) {
      result = result.replace(":" + param.getKey(), encode(param.getValue(), UTF_8));
    }
    return result;
  }
}
