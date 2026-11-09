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

import static tech.pegasys.teku.infrastructure.metrics.MetricsPublishCategories.SYSTEM;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class SystemMetricData extends BaseMetricData {
  @JsonProperty("cpu_cores")
  private final int cpuCores;

  @JsonProperty("cpu_threads")
  private final int cpuThreads;

  @JsonProperty("cpu_node_system_seconds_total")
  private final long cpuNodeSystemSecondsTotal;

  @JsonProperty("cpu_node_user_seconds_total")
  private final long cpuNodeUserSecondsTotal;

  @JsonProperty("cpu_node_iowait_seconds_total")
  private final long cpuNodeIowaitSecondsTotal;

  @JsonProperty("cpu_node_idle_seconds_total")
  private final long cpuNodeIdleSecondsTotal;

  @JsonProperty("memory_node_bytes_total")
  private final long memoryNodeBytesTotal;

  @JsonProperty("memory_node_bytes_free")
  private final long memoryNodeBytesFree;

  @JsonProperty("memory_node_bytes_cached")
  private final long memoryNodeBytesCached = 0L;

  @JsonProperty("memory_node_bytes_buffers")
  private final long memoryNodeBytesBuffers = 0L;

  @JsonProperty("disk_node_bytes_total")
  private final long diskNodeBytesTotal;

  @JsonProperty("disk_node_bytes_free")
  private final long diskNodeBytesFree;

  @JsonProperty("disk_node_io_seconds")
  private final long diskNodeIoSeconds = 0L;

  @JsonProperty("disk_node_reads_total")
  private final long diskNodeReadsTotal = 0L;

  @JsonProperty("disk_node_writes_total")
  private final long diskNodeWritesTotal = 0L;

  @JsonProperty("network_node_bytes_total_receive")
  private final long networkNodeBytesTotalReceive;

  @JsonProperty("network_node_bytes_total_transmit")
  private final long networkNodeBytesTotalTransmit;

  @JsonProperty("misc_node_boot_ts_seconds")
  private final long miscNodeBootTsSeconds;

  @JsonProperty("misc_os")
  private final String miscOs = getNormalizedOSVersion();

  public SystemMetricData(final long timestamp, final File beaconNodeDirectory) {
    super(timestamp, SYSTEM.getDisplayName());

    SystemInfo systemInfo = new SystemInfo();
    final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    this.cpuCores = hardware.getProcessor().getPhysicalProcessorCount();
    this.cpuThreads = hardware.getProcessor().getLogicalProcessorCount();
    this.memoryNodeBytesFree = hardware.getMemory().getAvailable();
    this.memoryNodeBytesTotal = hardware.getMemory().getTotal();
    this.miscNodeBootTsSeconds = systemInfo.getOperatingSystem().getSystemBootTime();

    final long[] processStats =
        calculateProcessLoadTicks(hardware.getProcessor().getProcessorCpuLoadTicks());
    this.cpuNodeUserSecondsTotal = processStats[0];
    this.cpuNodeSystemSecondsTotal = processStats[1];
    this.cpuNodeIdleSecondsTotal = processStats[2];
    this.cpuNodeIowaitSecondsTotal = processStats[3];

    this.networkNodeBytesTotalReceive =
        hardware.getNetworkIFs(false).stream().map(NetworkIF::getBytesRecv).reduce(0L, Long::sum);
    this.networkNodeBytesTotalTransmit =
        hardware.getNetworkIFs(false).stream().map(NetworkIF::getBytesSent).reduce(0L, Long::sum);

    this.diskNodeBytesTotal =
        beaconNodeDirectory.exists() ? beaconNodeDirectory.getTotalSpace() : 0L;
    this.diskNodeBytesFree = beaconNodeDirectory.exists() ? beaconNodeDirectory.getFreeSpace() : 0L;
  }

  private long[] calculateProcessLoadTicks(final long[][] processorCpuLoadTicks) {

    long idle = 0L;
    long system = 0L;
    long user = 0L;
    long iowait = 0L;
    for (long[] stats : processorCpuLoadTicks) {
      user += stats[0];
      system += stats[2];
      idle += stats[3];
      iowait += stats[4];
    }
    // load ticks are in millis, and we require seconds
    user /= 1000;
    system /= 1000;
    idle /= 1000;
    iowait /= 1000;
    // note: currently beaconcha.in expects system time to be everything
    return new long[] {user, (system + user + idle + iowait), idle, iowait};
  }

  private static String getNormalizedOSVersion() {
    String currentVersionInfo = VersionProvider.VERSION;
    if (currentVersionInfo.contains("linux")) {
      return "lin";
    } else if (currentVersionInfo.contains("osx")) {
      return "mac";
    }
    return "unk";
  }

  public int getCpuCores() {
    return cpuCores;
  }

  public int getCpuThreads() {
    return cpuThreads;
  }

  public long getCpuNodeSystemSecondsTotal() {
    return cpuNodeSystemSecondsTotal;
  }

  public long getCpuNodeUserSecondsTotal() {
    return cpuNodeUserSecondsTotal;
  }

  public long getCpuNodeIowaitSecondsTotal() {
    return cpuNodeIowaitSecondsTotal;
  }

  public long getCpuNodeIdleSecondsTotal() {
    return cpuNodeIdleSecondsTotal;
  }

  public long getMemoryNodeBytesTotal() {
    return memoryNodeBytesTotal;
  }

  public long getMemoryNodeBytesFree() {
    return memoryNodeBytesFree;
  }

  public long getMemoryNodeBytesCached() {
    return memoryNodeBytesCached;
  }

  public long getMemoryNodeBytesBuffers() {
    return memoryNodeBytesBuffers;
  }

  public long getDiskNodeBytesTotal() {
    return diskNodeBytesTotal;
  }

  public long getDiskNodeBytesFree() {
    return diskNodeBytesFree;
  }

  public long getDiskNodeIoSeconds() {
    return diskNodeIoSeconds;
  }

  public long getDiskNodeReadsTotal() {
    return diskNodeReadsTotal;
  }

  public long getDiskNodeWritesTotal() {
    return diskNodeWritesTotal;
  }

  public long getNetworkNodeBytesTotalReceive() {
    return networkNodeBytesTotalReceive;
  }

  public long getNetworkNodeBytesTotalTransmit() {
    return networkNodeBytesTotalTransmit;
  }

  public long getMiscNodeBootTsSeconds() {
    return miscNodeBootTsSeconds;
  }

  public String getMiscOs() {
    return miscOs;
  }
}
