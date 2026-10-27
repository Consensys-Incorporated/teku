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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

public class BeaconChainMethodIds {
  static final String STATUS = "/eth2/beacon_chain/req/status";
  static final String GOODBYE = "/eth2/beacon_chain/req/goodbye";

  static final String BEACON_BLOCKS_BY_ROOT = "/eth2/beacon_chain/req/beacon_blocks_by_root";
  static final String BEACON_BLOCKS_BY_RANGE = "/eth2/beacon_chain/req/beacon_blocks_by_range";

  static final String BLOB_SIDECARS_BY_ROOT = "/eth2/beacon_chain/req/blob_sidecars_by_root";
  static final String BLOB_SIDECARS_BY_RANGE = "/eth2/beacon_chain/req/blob_sidecars_by_range";

  static final String DATA_COLUMN_SIDECARS_BY_ROOT =
      "/eth2/beacon_chain/req/data_column_sidecars_by_root";
  static final String DATA_COLUMN_SIDECARS_BY_RANGE =
      "/eth2/beacon_chain/req/data_column_sidecars_by_range";

  static final String EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT =
      "/eth2/beacon_chain/req/execution_payload_envelopes_by_root";
  static final String EXECUTION_PAYLOAD_ENVELOPES_BY_RANGE =
      "/eth2/beacon_chain/req/execution_payload_envelopes_by_range";

  static final String GET_METADATA = "/eth2/beacon_chain/req/metadata";
  static final String PING = "/eth2/beacon_chain/req/ping";

  static final String LIGHT_CLIENT_BOOTSTRAP = "/eth2/beacon_chain/req/light_client_bootstrap";
  static final String LIGHT_CLIENT_UPDATES_BY_RANGE =
      "/eth2/beacon_chain/req/light_client_updates_by_range";
  static final String LIGHT_CLIENT_FINALITY_UPDATE =
      "/eth2/beacon_chain/req/light_client_finality_update";
  static final String LIGHT_CLIENT_OPTIMISTIC_UPDATE =
      "/eth2/beacon_chain/req/light_client_optimistic_update";

  public static String getMethodId(
      final String methodPrefix, final int version, final RpcEncoding encoding) {
    return methodPrefix + "/" + version + "/" + encoding.getName();
  }

  public static String getBlocksByRootMethodId(final int version, final RpcEncoding encoding) {
    return getMethodId(BEACON_BLOCKS_BY_ROOT, version, encoding);
  }

  public static String getBlocksByRangeMethodId(final int version, final RpcEncoding encoding) {
    return getMethodId(BEACON_BLOCKS_BY_RANGE, version, encoding);
  }

  public static String getBlobSidecarsByRootMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(BLOB_SIDECARS_BY_ROOT, version, encoding);
  }

  public static String getBlobSidecarsByRangeMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(BLOB_SIDECARS_BY_RANGE, version, encoding);
  }

  public static String getDataColumnSidecarsByRootMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(DATA_COLUMN_SIDECARS_BY_ROOT, version, encoding);
  }

  public static String getDataColumnSidecarsByRangeMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(DATA_COLUMN_SIDECARS_BY_RANGE, version, encoding);
  }

  public static String getExecutionPayloadEnvelopesByRootMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT, version, encoding);
  }

  public static String getExecutionPayloadEnvelopesByRangeMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(EXECUTION_PAYLOAD_ENVELOPES_BY_RANGE, version, encoding);
  }

  public static String getStatusMethodId(final int version, final RpcEncoding encoding) {
    return getMethodId(STATUS, version, encoding);
  }

  public static String getLightClientBootstrapMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(LIGHT_CLIENT_BOOTSTRAP, version, encoding);
  }

  public static String getLightClientUpdatesByRangeMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(LIGHT_CLIENT_UPDATES_BY_RANGE, version, encoding);
  }

  public static String getLightClientFinalityUpdateMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(LIGHT_CLIENT_FINALITY_UPDATE, version, encoding);
  }

  public static String getLightClientOptimisticUpdateMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(LIGHT_CLIENT_OPTIMISTIC_UPDATE, version, encoding);
  }

  public static int extractBeaconBlocksByRootVersion(final String methodId) {
    return extractVersion(methodId, BEACON_BLOCKS_BY_ROOT);
  }

  public static int extractBeaconBlocksByRangeVersion(final String methodId) {
    return extractVersion(methodId, BEACON_BLOCKS_BY_RANGE);
  }

  public static int extractGetMetadataVersion(final String methodId) {
    return extractVersion(methodId, GET_METADATA);
  }

  public static int extractStatusVersion(final String methodId) {
    return extractVersion(methodId, STATUS);
  }

  @VisibleForTesting
  static int extractVersion(final String methodId, final String methodPrefix) {
    final String versionAndEncoding = methodId.replace(methodPrefix + "/", "");
    final String version = versionAndEncoding.substring(0, versionAndEncoding.indexOf("/"));
    return Integer.parseInt(version, 10);
  }
}
