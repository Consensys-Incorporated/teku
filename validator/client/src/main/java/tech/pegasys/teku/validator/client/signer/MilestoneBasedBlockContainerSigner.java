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

package tech.pegasys.teku.validator.client.signer;

import com.google.common.base.Suppliers;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.client.Validator;

public class MilestoneBasedBlockContainerSigner implements BlockContainerSigner {

  private final Spec spec;
  private final Map<SpecMilestone, BlockContainerSigner> registeredSigners =
      new EnumMap<>(SpecMilestone.class);

  public MilestoneBasedBlockContainerSigner(final Spec spec) {
    this.spec = spec;

    final BlockContainerSignerPhase0 blockContainerSignerPhase0 =
        new BlockContainerSignerPhase0(spec);

    // Not needed for all milestones
    final Supplier<BlockContainerSignerDeneb> blockContainerSignerDeneb =
        Suppliers.memoize(() -> new BlockContainerSignerDeneb(spec));

    // Populate forks signers
    spec.getEnabledMilestones()
        .forEach(
            forkAndSpecMilestone -> {
              final SpecMilestone milestone = forkAndSpecMilestone.getSpecMilestone();
              if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
                // in Gloas, phase0 can be reused
                registeredSigners.put(milestone, blockContainerSignerPhase0);
              } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
                registeredSigners.put(milestone, blockContainerSignerDeneb.get());
              } else {
                registeredSigners.put(milestone, blockContainerSignerPhase0);
              }
            });
  }

  @Override
  public SafeFuture<SignedBlockContainer> sign(
      final BlockContainer unsignedBlockContainer,
      final Validator validator,
      final ForkInfo forkInfo) {
    final SpecMilestone milestone = spec.atSlot(unsignedBlockContainer.getSlot()).getMilestone();
    return registeredSigners.get(milestone).sign(unsignedBlockContainer, validator, forkInfo);
  }
}
