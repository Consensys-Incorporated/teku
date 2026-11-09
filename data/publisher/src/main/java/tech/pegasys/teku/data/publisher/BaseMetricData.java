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

package tech.pegasys.teku.data.publisher;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BaseMetricData {
  @JsonProperty("version")
  private final int version = 1;

  @JsonProperty("timestamp")
  private final long timestamp;

  @JsonProperty("process")
  private final String process;

  public BaseMetricData(final long timestamp, final String process) {
    this.timestamp = timestamp;
    this.process = process;
  }

  public int getVersion() {
    return version;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public String getProcess() {
    return process;
  }
}
