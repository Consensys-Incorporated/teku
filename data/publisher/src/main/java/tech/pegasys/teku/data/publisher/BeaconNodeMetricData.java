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
import tech.pegasys.teku.infrastructure.metrics.MetricsPublishCategories;

public class BeaconNodeMetricData extends GeneralProcessMetricData {
  @JsonProperty("disk_beaconchain_bytes_total")
  private final long diskBytesTotal;

  @JsonProperty("network_libp2p_bytes_total_receive")
  private final long libp2pBytesReceived;

  @JsonProperty("network_libp2p_bytes_total_transmit")
  private final long libp2pBytesTotalTransmitted;

  @JsonProperty("network_peers_connected")
  private final int networkPeersConnected;

  @JsonProperty("sync_eth1_connected")
  private final boolean eth1Connected;

  @JsonProperty("sync_eth2_synced")
  private final boolean eth2Synced;

  @JsonProperty("sync_beacon_head_slot")
  private final long headSlot;

  @JsonProperty("sync_eth1_fallback_configured")
  private final boolean eth1FallbackConfigured;

  @JsonProperty("sync_eth1_fallback_connected")
  private final boolean eth1FallbackConnected;

  @JsonProperty("slasher_active")
  private final boolean slasherActive = false;

  public BeaconNodeMetricData(final long timestamp, final MetricsPublisherSource source) {
    super(timestamp, MetricsPublishCategories.BEACON_NODE.getDisplayName(), source);
    this.diskBytesTotal = 0L;
    this.libp2pBytesReceived = source.getGossipBytesTotalReceived();
    this.libp2pBytesTotalTransmitted = source.getGossipBytesTotalSent();
    this.networkPeersConnected = source.getPeerCount();
    this.eth1Connected = source.isEth1Connected();
    this.eth2Synced = source.isEth2Synced();
    this.headSlot = source.getHeadSlot();
    this.eth1FallbackConfigured = false;
    this.eth1FallbackConnected = false;
  }

  public long getDiskBytesTotal() {
    return diskBytesTotal;
  }

  public long getLibp2pBytesReceived() {
    return libp2pBytesReceived;
  }

  public long getLibp2pBytesTotalTransmitted() {
    return libp2pBytesTotalTransmitted;
  }

  public int getNetworkPeersConnected() {
    return networkPeersConnected;
  }

  public boolean isEth1Connected() {
    return eth1Connected;
  }

  public boolean isEth2Synced() {
    return eth2Synced;
  }

  public long getHeadSlot() {
    return headSlot;
  }

  public boolean isEth1FallbackConfigured() {
    return eth1FallbackConfigured;
  }

  public boolean isEth1FallbackConnected() {
    return eth1FallbackConnected;
  }

  public boolean isSlasherActive() {
    return slasherActive;
  }
}
