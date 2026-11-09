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

package tech.pegasys.teku.beaconrestapi.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {
  private final Integer status;
  private final String message;

  @JsonCreator
  public ErrorResponse(
      final @JsonProperty("status") Integer status, final @JsonProperty("message") String message) {
    this.status = status;
    this.message = message;
  }

  @JsonProperty("status")
  public final Integer getStatus() {
    return status;
  }

  @JsonProperty("message")
  public final String getMessage() {
    return message;
  }
}
