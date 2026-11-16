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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

/** Where a data column sidecar sent in response to a by range or by root request came from. */
enum DataColumnSidecarSource {
  STORED("stored"),
  RECONSTRUCTED("reconstructed");

  private final String metricLabel;

  DataColumnSidecarSource(final String metricLabel) {
    this.metricLabel = metricLabel;
  }

  /** The value of the source label of the sent sidecars counters. */
  String metricLabel() {
    return metricLabel;
  }
}
