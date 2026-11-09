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
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class GeneralProcessMetricData extends BaseMetricData {

  @JsonProperty("cpu_process_seconds_total")
  private final long cpuProcessSecondsTotal;

  @JsonProperty("memory_process_bytes")
  private final long memoryProcessBytes;

  @JsonProperty("client_name")
  private final String clientName = VersionProvider.CLIENT_IDENTITY;

  @JsonProperty("client_version")
  private final String clientVersion = VersionProvider.IMPLEMENTATION_VERSION.substring(1);

  @JsonProperty("client_build")
  private final int clientBuild = 0;

  @JsonProperty("sync_eth2_fallback_configured")
  private final boolean eth2FallbackConfigured = false;

  @JsonProperty("sync_eth2_fallback_connected")
  private final boolean eth2FallbackConnected = false;

  public GeneralProcessMetricData(
      final long timestamp, final String process, final MetricsPublisherSource source) {
    super(timestamp, process);
    this.cpuProcessSecondsTotal = source.getCpuSecondsTotal();
    this.memoryProcessBytes = source.getMemoryProcessBytes();
  }

  public long getCpuProcessSecondsTotal() {
    return cpuProcessSecondsTotal;
  }

  public long getMemoryProcessBytes() {
    return memoryProcessBytes;
  }

  public String getClientName() {
    return clientName;
  }

  public String getClientVersion() {
    return clientVersion;
  }

  public boolean isEth2FallbackConfigured() {
    return eth2FallbackConfigured;
  }

  public boolean isEth2FallbackConnected() {
    return eth2FallbackConnected;
  }

  public int getClientBuild() {
    return clientBuild;
  }
}
