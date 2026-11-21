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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;

import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;

public class LightClientOptimisticUpdateEvent
    extends Event<LightClientOptimisticUpdateEvent.VersionedLightClientOptimisticUpdate> {

  record VersionedLightClientOptimisticUpdate(
      SpecMilestone version, LightClientOptimisticUpdate data) {}

  private static SerializableTypeDefinition<VersionedLightClientOptimisticUpdate> buildTypeDef(
      final LightClientOptimisticUpdate optimisticUpdate) {
    return SerializableTypeDefinition.object(VersionedLightClientOptimisticUpdate.class)
        .name("VersionedLightClientOptimisticUpdate")
        .withField("version", MILESTONE_TYPE, VersionedLightClientOptimisticUpdate::version)
        .withField(
            "data",
            optimisticUpdate.getSchema().getJsonTypeDefinition(),
            VersionedLightClientOptimisticUpdate::data)
        .build();
  }

  public LightClientOptimisticUpdateEvent(
      final SpecMilestone milestone, final LightClientOptimisticUpdate optimisticUpdate) {
    super(
        buildTypeDef(optimisticUpdate),
        new VersionedLightClientOptimisticUpdate(milestone, optimisticUpdate));
  }
}
