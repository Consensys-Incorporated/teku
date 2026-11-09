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

package tech.pegasys.teku.test.data.publisher;

import tech.pegasys.teku.data.publisher.MetricsPublisherSource;

public class StubMetricsPublisherSource implements MetricsPublisherSource {
  private final long cpuSecondsTotal;
  private final long memoryProcessBytes;
  private final long headSlot;
  private final int validatorsTotal;
  private final int validatorsActive;
  private final boolean isBeaconNodePresent;
  private final boolean isEth2Synced;
  private final long gossipBytesSent;
  private final long gossipBytesReceived;

  private StubMetricsPublisherSource(
      final long cpuSecondsTotal,
      final long memoryProcessBytes,
      final int validatorsTotal,
      final int validatorsActive,
      final long headSlot,
      final boolean isBeaconNodePresent,
      final long gossipBytesReceived,
      final long gossipBytesSent,
      final boolean isEth2Synced) {
    this.cpuSecondsTotal = cpuSecondsTotal;
    this.memoryProcessBytes = memoryProcessBytes;
    this.validatorsTotal = validatorsTotal;
    this.validatorsActive = validatorsActive;
    this.headSlot = headSlot;
    this.isBeaconNodePresent = isBeaconNodePresent;
    this.gossipBytesReceived = gossipBytesReceived;
    this.gossipBytesSent = gossipBytesSent;
    this.isEth2Synced = isEth2Synced;
  }

  @Override
  public long getCpuSecondsTotal() {
    return cpuSecondsTotal;
  }

  @Override
  public long getMemoryProcessBytes() {
    return memoryProcessBytes;
  }

  @Override
  public int getValidatorsTotal() {
    return validatorsTotal;
  }

  @Override
  public int getValidatorsActive() {
    return validatorsActive;
  }

  @Override
  public long getHeadSlot() {
    return headSlot;
  }

  @Override
  public boolean isValidatorPresent() {
    return validatorsTotal > 0 || validatorsActive > 0;
  }

  @Override
  public boolean isBeaconNodePresent() {
    return isBeaconNodePresent;
  }

  @Override
  public boolean isEth2Synced() {
    return isEth2Synced;
  }

  @Override
  public boolean isEth1Connected() {
    return false;
  }

  @Override
  public int getPeerCount() {
    return 0;
  }

  @Override
  public long getGossipBytesTotalSent() {
    return gossipBytesSent;
  }

  @Override
  public long getGossipBytesTotalReceived() {
    return gossipBytesReceived;
  }

  public static Builder builder() {
    return new StubMetricsPublisherSource.Builder();
  }

  public static final class Builder {
    public long cpuSecondsTotal;
    public long memoryProcessBytes;
    public long headSlot;
    public int validatorsTotal;
    public int validatorsActive;
    private boolean isBeaconNodePresent;
    private boolean isEth2Synced;
    private long gossipBytesSent;
    private long gossipBytesReceived;

    public Builder cpuSecondsTotal(final long cpuSecondsTotal) {
      this.cpuSecondsTotal = cpuSecondsTotal;
      return this;
    }

    public Builder memoryProcessBytes(final long memoryProcessBytes) {
      this.memoryProcessBytes = memoryProcessBytes;
      return this;
    }

    public Builder headSlot(final long headSlot) {
      this.headSlot = headSlot;
      return this;
    }

    public Builder validatorsTotal(final int validatorsTotal) {
      this.validatorsTotal = validatorsTotal;
      return this;
    }

    public Builder validatorsActive(final int validatorsActive) {
      this.validatorsActive = validatorsActive;
      return this;
    }

    public Builder isBeaconNodePresent(final boolean isBeaconNodePresent) {
      this.isBeaconNodePresent = isBeaconNodePresent;
      return this;
    }

    public Builder isEth2Synced(final boolean isEth2Synced) {
      this.isEth2Synced = isEth2Synced;
      return this;
    }

    public Builder gossipBytesReceived(final long gossipBytesReceived) {
      this.gossipBytesReceived = gossipBytesReceived;
      return this;
    }

    public Builder gossipBytesSent(final long gossipBytesSent) {
      this.gossipBytesSent = gossipBytesSent;
      return this;
    }

    public StubMetricsPublisherSource build() {
      return new StubMetricsPublisherSource(
          cpuSecondsTotal,
          memoryProcessBytes,
          validatorsTotal,
          validatorsActive,
          headSlot,
          isBeaconNodePresent,
          gossipBytesReceived,
          gossipBytesSent,
          isEth2Synced);
    }
  }
}
