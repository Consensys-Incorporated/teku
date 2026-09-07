/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.builder.rest;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

public enum BuilderApiMethod {
  GET_EXECUTION_PAYLOAD_BID(
      "eth/v1/builder/execution_payload_bid/{slot}/{parent_hash}/{parent_root}/{proposer_pubkey}"),
  SUBMIT_BUILDER_PREFERENCES("eth/v1/builder/builder_preferences/{validator_pubkey}"),
  SUBMIT_SIGNED_BEACON_BLOCK("eth/v1/builder/beacon_blocks");

  private final String path;

  BuilderApiMethod(final String path) {
    this.path = path;
  }

  public String getPath(final Map<String, String> urlParams) {
    String result = path;
    for (final Map.Entry<String, String> param : urlParams.entrySet()) {
      result = result.replace("{" + param.getKey() + "}", encode(param.getValue(), UTF_8));
    }
    return result;
  }
}
