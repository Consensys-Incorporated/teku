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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import com.google.common.base.Supplier;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

@FunctionalInterface
public interface NodeIdToDataColumnSidecarSubnetsCalculator {

  Optional<SszBitvector> calculateSubnets(UInt256 nodeId, Optional<Integer> groupCount);

  NodeIdToDataColumnSidecarSubnetsCalculator NOOP = (nodeId, subnetCount) -> Optional.empty();

  static NodeIdToDataColumnSidecarSubnetsCalculator create(
      final Spec spec, final Supplier<Optional<UInt64>> currentSlotSupplier) {

    return (nodeId, groupCount) ->
        currentSlotSupplier
            .get()
            .flatMap(
                slot -> {
                  final SpecVersion version = spec.atSlot(slot);
                  if (version.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
                    final SpecConfigFulu config = SpecConfigFulu.required(version.getConfig());
                    final List<UInt64> subnets =
                        MiscHelpersFulu.required(version.miscHelpers())
                            .computeDataColumnSidecarBackboneSubnets(
                                nodeId, groupCount.orElse(config.getCustodyRequirement()));
                    return Optional.of(
                        SszBitvectorSchema.create(config.getDataColumnSidecarSubnetCount())
                            .ofBits(subnets.stream().map(UInt64::intValue).toList()));
                  }
                  return Optional.empty();
                });
  }
}
