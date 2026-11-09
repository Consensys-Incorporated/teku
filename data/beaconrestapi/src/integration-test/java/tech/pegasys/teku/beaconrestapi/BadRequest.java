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

package tech.pegasys.teku.beaconrestapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BadRequest {
  private final Integer code;
  private final String message;

  @JsonCreator
  public BadRequest(
      @JsonProperty("code") final Integer code, @JsonProperty("message") final String message) {
    this.code = code;
    this.message = message;
  }

  @JsonProperty("code")
  public final Integer getCode() {
    return code;
  }

  @JsonProperty("message")
  public final String getMessage() {
    return message;
  }
}
