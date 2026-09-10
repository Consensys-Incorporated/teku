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

package tech.pegasys.teku.beacon.sync;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.OptionalInt;
import tech.pegasys.teku.networking.eth2.P2PConfig;

public class SyncConfig {

  public static final boolean DEFAULT_MULTI_PEER_SYNC_ENABLED = true;
  public static final boolean DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED = false;
  public static final boolean DEFAULT_FETCH_ALL_HISTORIC_BLOCKS = true;

  public static final int DEFAULT_HISTORICAL_SYNC_BATCH_SIZE = 50;
  public static final int DEFAULT_FORWARD_SYNC_BATCH_SIZE = 25;
  public static final int DEFAULT_FORWARD_SYNC_MAX_PENDING_BATCHES = 5;

  /**
   * Sized for the default linear forward-sync pipeline. Trackers still needed for import may exceed
   * this soft limit, up to the sampler's hard limit.
   */
  public static final int DEFAULT_MAX_RECENTLY_SAMPLED_BLOCKS = 128;

  public static final int DEFAULT_FORWARD_SYNC_MAX_BLOCKS_PER_MINUTE = 500;

  /** Aligned with {@link P2PConfig#DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT} */
  public static final int DEFAULT_FORWARD_SYNC_MAX_BLOB_SIDECARS_PER_MINUTE = 2000;

  private final boolean isEnabled;
  private final boolean isMultiPeerSyncEnabled;
  private final boolean reconstructHistoricStatesEnabled;
  private final boolean fetchAllHistoricBlocks;
  private final int historicalSyncBatchSize;
  private final int forwardSyncBatchSize;
  private final int forwardSyncMaxPendingBatches;
  private final int forwardSyncMaxBlocksPerMinute;
  private final int forwardSyncMaxBlobSidecarsPerMinute;
  private final int maxRecentlySampledBlocks;
  private final OptionalInt forwardSyncMaxDistanceFromHead;

  private SyncConfig(
      final boolean isEnabled,
      final boolean isMultiPeerSyncEnabled,
      final boolean reconstructHistoricStatesEnabled,
      final boolean fetchAllHistoricBlocks,
      final int historicalSyncBatchSize,
      final int forwardSyncBatchSize,
      final int forwardSyncMaxPendingBatches,
      final int forwardSyncMaxBlocksPerMinute,
      final int forwardSyncMaxBlobSidecarsPerMinute,
      final int maxRecentlySampledBlocks,
      final OptionalInt forwardSyncMaxDistanceFromHead) {
    this.isEnabled = isEnabled;
    this.isMultiPeerSyncEnabled = isMultiPeerSyncEnabled;
    this.reconstructHistoricStatesEnabled = reconstructHistoricStatesEnabled;
    this.fetchAllHistoricBlocks = fetchAllHistoricBlocks;
    this.historicalSyncBatchSize = historicalSyncBatchSize;
    this.forwardSyncBatchSize = forwardSyncBatchSize;
    this.forwardSyncMaxPendingBatches = forwardSyncMaxPendingBatches;
    this.forwardSyncMaxBlocksPerMinute = forwardSyncMaxBlocksPerMinute;
    this.forwardSyncMaxBlobSidecarsPerMinute = forwardSyncMaxBlobSidecarsPerMinute;
    this.maxRecentlySampledBlocks = maxRecentlySampledBlocks;
    this.forwardSyncMaxDistanceFromHead = forwardSyncMaxDistanceFromHead;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isSyncEnabled() {
    return isEnabled;
  }

  public boolean isMultiPeerSyncEnabled() {
    return isMultiPeerSyncEnabled;
  }

  public boolean isReconstructHistoricStatesEnabled() {
    return reconstructHistoricStatesEnabled;
  }

  public boolean fetchAllHistoricBlocks() {
    return fetchAllHistoricBlocks;
  }

  public int getHistoricalSyncBatchSize() {
    return historicalSyncBatchSize;
  }

  public int getForwardSyncBatchSize() {
    return forwardSyncBatchSize;
  }

  public int getForwardSyncMaxPendingBatches() {
    return forwardSyncMaxPendingBatches;
  }

  public int getForwardSyncMaxBlocksPerMinute() {
    return forwardSyncMaxBlocksPerMinute;
  }

  public int getForwardSyncMaxBlobSidecarsPerMinute() {
    return forwardSyncMaxBlobSidecarsPerMinute;
  }

  public int getMaxRecentlySampledBlocks() {
    return maxRecentlySampledBlocks;
  }

  public OptionalInt getForwardSyncMaxDistanceFromHead() {
    return forwardSyncMaxDistanceFromHead;
  }

  public static class Builder {
    private Boolean isEnabled;
    private Boolean isMultiPeerSyncEnabled = DEFAULT_MULTI_PEER_SYNC_ENABLED;
    private Boolean reconstructHistoricStatesEnabled = DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED;
    private boolean fetchAllHistoricBlocks = DEFAULT_FETCH_ALL_HISTORIC_BLOCKS;
    private Integer historicalSyncBatchSize = DEFAULT_HISTORICAL_SYNC_BATCH_SIZE;
    private Integer forwardSyncBatchSize = DEFAULT_FORWARD_SYNC_BATCH_SIZE;
    private Integer forwardSyncMaxPendingBatches = DEFAULT_FORWARD_SYNC_MAX_PENDING_BATCHES;
    private Integer forwardSyncMaxBlocksPerMinute = DEFAULT_FORWARD_SYNC_MAX_BLOCKS_PER_MINUTE;
    private Integer forwardSyncMaxBlobSidecarsPerMinute =
        DEFAULT_FORWARD_SYNC_MAX_BLOB_SIDECARS_PER_MINUTE;
    private Integer maxRecentlySampledBlocks = DEFAULT_MAX_RECENTLY_SAMPLED_BLOCKS;
    private OptionalInt forwardSyncMaxDistanceFromHead = OptionalInt.empty();

    private Builder() {}

    public SyncConfig build() {
      initMissingDefaults();
      return new SyncConfig(
          isEnabled,
          isMultiPeerSyncEnabled,
          reconstructHistoricStatesEnabled,
          fetchAllHistoricBlocks,
          historicalSyncBatchSize,
          forwardSyncBatchSize,
          forwardSyncMaxPendingBatches,
          forwardSyncMaxBlocksPerMinute,
          forwardSyncMaxBlobSidecarsPerMinute,
          maxRecentlySampledBlocks,
          forwardSyncMaxDistanceFromHead);
    }

    private void initMissingDefaults() {
      if (isEnabled == null) {
        isEnabled = true;
      }
    }

    public Builder isSyncEnabled(final Boolean enabled) {
      checkNotNull(enabled);
      isEnabled = enabled;
      return this;
    }

    public Builder isSyncEnabledDefault(final boolean enabled) {
      if (isEnabled == null) {
        isEnabled = enabled;
      }
      return this;
    }

    public Builder isMultiPeerSyncEnabled(final Boolean multiPeerSyncEnabled) {
      checkNotNull(multiPeerSyncEnabled);
      isMultiPeerSyncEnabled = multiPeerSyncEnabled;
      return this;
    }

    public Builder historicalSyncBatchSize(final Integer historicalSyncBatchSize) {
      checkNotNull(historicalSyncBatchSize);
      this.historicalSyncBatchSize = historicalSyncBatchSize;
      return this;
    }

    public Builder forwardSyncBatchSize(final Integer forwardSyncBatchSize) {
      checkNotNull(forwardSyncBatchSize);
      this.forwardSyncBatchSize = forwardSyncBatchSize;
      return this;
    }

    public Builder forwardSyncMaxPendingBatches(final Integer forwardSyncMaxPendingBatches) {
      checkNotNull(forwardSyncMaxPendingBatches);
      this.forwardSyncMaxPendingBatches = forwardSyncMaxPendingBatches;
      return this;
    }

    public Builder forwardSyncMaxDistanceFromHead(final Integer forwardSyncMaxDistanceFromHead) {
      if (forwardSyncMaxDistanceFromHead == null) {
        this.forwardSyncMaxDistanceFromHead = OptionalInt.empty();
      } else {
        this.forwardSyncMaxDistanceFromHead = OptionalInt.of(forwardSyncMaxDistanceFromHead);
      }
      return this;
    }

    public Builder forwardSyncMaxBlocksPerMinute(final Integer forwardSyncMaxBlocksPerMinute) {
      checkNotNull(forwardSyncMaxBlocksPerMinute);
      this.forwardSyncMaxBlocksPerMinute = forwardSyncMaxBlocksPerMinute;
      return this;
    }

    public Builder forwardSyncMaxBlobSidecarsPerMinute(
        final Integer forwardSyncMaxBlobSidecarsPerMinute) {
      checkNotNull(forwardSyncMaxBlobSidecarsPerMinute);
      this.forwardSyncMaxBlobSidecarsPerMinute = forwardSyncMaxBlobSidecarsPerMinute;
      return this;
    }

    public Builder reconstructHistoricStatesEnabled(
        final Boolean reconstructHistoricStatesEnabled) {
      checkNotNull(reconstructHistoricStatesEnabled);
      this.reconstructHistoricStatesEnabled = reconstructHistoricStatesEnabled;
      return this;
    }

    public Builder maxRecentlySampledBlocks(final Integer maxRecentlySampledBlocks) {
      checkNotNull(maxRecentlySampledBlocks);
      this.maxRecentlySampledBlocks = maxRecentlySampledBlocks;
      return this;
    }

    public Builder fetchAllHistoricBlocks(final boolean fetchAllHistoricBlocks) {
      this.fetchAllHistoricBlocks = fetchAllHistoricBlocks;
      return this;
    }
  }
}
