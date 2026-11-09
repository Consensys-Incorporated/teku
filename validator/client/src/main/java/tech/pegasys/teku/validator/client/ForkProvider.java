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

package tech.pegasys.teku.validator.client;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class ForkProvider {
  private final Spec spec;
  private final GenesisDataProvider genesisDataProvider;

  public ForkProvider(final Spec spec, final GenesisDataProvider genesisDataProvider) {
    this.spec = spec;
    this.genesisDataProvider = genesisDataProvider;
  }

  public SafeFuture<ForkInfo> getForkInfo(final UInt64 slot) {
    return genesisDataProvider
        .getGenesisValidatorsRoot()
        .thenApply(
            genesisValidatorsRoot ->
                new ForkInfo(
                    spec.getForkSchedule().getFork(spec.computeEpochAtSlot(slot)),
                    genesisValidatorsRoot));
  }
}
