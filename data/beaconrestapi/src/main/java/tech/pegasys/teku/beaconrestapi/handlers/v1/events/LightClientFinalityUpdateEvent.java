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
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;

public class LightClientFinalityUpdateEvent
    extends Event<LightClientFinalityUpdateEvent.VersionedLightClientFinalityUpdate> {

  record VersionedLightClientFinalityUpdate(
      SpecMilestone version, LightClientFinalityUpdate data) {}

  private static SerializableTypeDefinition<VersionedLightClientFinalityUpdate> buildTypeDef(
      final LightClientFinalityUpdate finalityUpdate) {
    return SerializableTypeDefinition.object(VersionedLightClientFinalityUpdate.class)
        .name("VersionedLightClientFinalityUpdate")
        .withField("version", MILESTONE_TYPE, VersionedLightClientFinalityUpdate::version)
        .withField(
            "data",
            finalityUpdate.getSchema().getJsonTypeDefinition(),
            VersionedLightClientFinalityUpdate::data)
        .build();
  }

  public LightClientFinalityUpdateEvent(
      final SpecMilestone milestone, final LightClientFinalityUpdate finalityUpdate) {
    super(
        buildTypeDef(finalityUpdate),
        new VersionedLightClientFinalityUpdate(milestone, finalityUpdate));
  }
}
