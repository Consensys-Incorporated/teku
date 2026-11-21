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

package tech.pegasys.teku.statetransition.lightclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
class NewerLightClientUpdateFilterTest {

  private static final UInt64 ATTESTED_SLOT = UInt64.valueOf(9);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  void finalityUpdates_shouldDeliverFirstUpdate() {
    final List<LightClientFinalityUpdate> delivered = new ArrayList<>();
    final NewerLightClientUpdateFilter<LightClientFinalityUpdate> filter =
        NewerLightClientUpdateFilter.forFinalityUpdates(delivered::add);
    final LightClientFinalityUpdate update = finalityUpdate(5, lowParticipation());

    filter.accept(update);

    assertThat(delivered).containsExactly(update);
  }

  @TestTemplate
  void finalityUpdates_shouldOnlyDeliverWhenFinalizedSlotIncreases() {
    final List<LightClientFinalityUpdate> delivered = new ArrayList<>();
    final NewerLightClientUpdateFilter<LightClientFinalityUpdate> filter =
        NewerLightClientUpdateFilter.forFinalityUpdates(delivered::add);
    final LightClientFinalityUpdate first = finalityUpdate(5, fullParticipation());
    final LightClientFinalityUpdate sameFinalizedSlot = finalityUpdate(5, fullParticipation());
    final LightClientFinalityUpdate lowerFinalizedSlot = finalityUpdate(4, fullParticipation());
    final LightClientFinalityUpdate higherFinalizedSlot = finalityUpdate(6, lowParticipation());

    filter.accept(first);
    filter.accept(sameFinalizedSlot);
    filter.accept(lowerFinalizedSlot);
    filter.accept(higherFinalizedSlot);

    assertThat(delivered).containsExactly(first, higherFinalizedSlot);
  }

  @TestTemplate
  void finalityUpdates_shouldDeliverSameFinalizedSlotWhenNewlyReachingSupermajority() {
    final List<LightClientFinalityUpdate> delivered = new ArrayList<>();
    final NewerLightClientUpdateFilter<LightClientFinalityUpdate> filter =
        NewerLightClientUpdateFilter.forFinalityUpdates(delivered::add);
    final LightClientFinalityUpdate withoutSupermajority = finalityUpdate(5, lowParticipation());
    final LightClientFinalityUpdate withSupermajority = finalityUpdate(5, fullParticipation());
    final LightClientFinalityUpdate withSupermajorityAgain = finalityUpdate(5, fullParticipation());

    filter.accept(withoutSupermajority);
    filter.accept(withSupermajority);
    filter.accept(withSupermajorityAgain);

    assertThat(delivered).containsExactly(withoutSupermajority, withSupermajority);
  }

  @TestTemplate
  void optimisticUpdates_shouldOnlyDeliverWhenAttestedSlotIncreases() {
    final List<LightClientOptimisticUpdate> delivered = new ArrayList<>();
    final NewerLightClientUpdateFilter<LightClientOptimisticUpdate> filter =
        NewerLightClientUpdateFilter.forOptimisticUpdates(delivered::add);
    final LightClientOptimisticUpdate first =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.valueOf(5));
    final LightClientOptimisticUpdate sameSlot =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.valueOf(5));
    final LightClientOptimisticUpdate lowerSlot =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.valueOf(4));
    final LightClientOptimisticUpdate higherSlot =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.valueOf(6));

    filter.accept(first);
    filter.accept(sameSlot);
    filter.accept(lowerSlot);
    filter.accept(higherSlot);

    assertThat(delivered).containsExactly(first, higherSlot);
  }

  private LightClientFinalityUpdate finalityUpdate(
      final long finalizedSlot, final SyncAggregate syncAggregate) {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(
            ATTESTED_SLOT, UInt64.valueOf(finalizedSlot));
    return SchemaDefinitionsAltair.required(spec.atSlot(ATTESTED_SLOT).getSchemaDefinitions())
        .getLightClientFinalityUpdateSchema()
        .create(
            update.getAttestedHeader(),
            update.getFinalizedHeader(),
            update.getFinalityBranch(),
            syncAggregate,
            SszUInt64.of(ATTESTED_SLOT.increment()));
  }

  private SyncAggregate fullParticipation() {
    final int syncCommitteeSize =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getSyncCommitteeSize();
    return dataStructureUtil.randomSyncAggregate(IntStream.range(0, syncCommitteeSize).toArray());
  }

  private SyncAggregate lowParticipation() {
    return dataStructureUtil.randomSyncAggregate();
  }
}
