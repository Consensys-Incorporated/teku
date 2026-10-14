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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.statetransition.forkchoice.DataColumnSidecarAvailabilityChecker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerManager implements AvailabilityCheckerFactory<UInt64> {
  public static final AvailabilityCheckerFactory<UInt64> NOOP = NOOP_DATACOLUMN_SIDECAR;
  private final Supplier<DataAvailabilitySampler> dataAvailabilitySamplerSupplier;
  final Spec spec;
  private final RecentChainData recentChainData;

  public DasSamplerManager(
      final Supplier<DataAvailabilitySampler> dataAvailabilitySamplerSupplier,
      final Spec spec,
      final RecentChainData recentChainData) {
    this.dataAvailabilitySamplerSupplier = dataAvailabilitySamplerSupplier;
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  @Override
  public DataColumnSidecarAvailabilityChecker createAvailabilityChecker(
      final SignedBeaconBlock block) {
    return new DataColumnSidecarAvailabilityChecker(
        dataAvailabilitySamplerSupplier.get(), spec, recentChainData, block);
  }

  @Override
  public DataColumnSidecarAvailabilityChecker createAvailabilityChecker(
      final BeaconState state, final SignedExecutionPayloadEnvelope signedEnvelope) {
    return new DataColumnSidecarAvailabilityChecker(
        dataAvailabilitySamplerSupplier.get(), spec, recentChainData, state, signedEnvelope);
  }
}
