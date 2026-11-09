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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;

public class PayloadAttestationMessageEvent
    extends Event<PayloadAttestationMessageEvent.VersionedPayloadAttestationMessage> {

  record VersionedPayloadAttestationMessage(
      SpecMilestone version, PayloadAttestationMessage data) {}

  private static SerializableTypeDefinition<VersionedPayloadAttestationMessage> buildTypeDef(
      final PayloadAttestationMessage msg) {
    return SerializableTypeDefinition.object(VersionedPayloadAttestationMessage.class)
        .name("VersionedPayloadAttestationMessage")
        .withField("version", MILESTONE_TYPE, VersionedPayloadAttestationMessage::version)
        .withField(
            "data",
            msg.getSchema().getJsonTypeDefinition(),
            VersionedPayloadAttestationMessage::data)
        .build();
  }

  public PayloadAttestationMessageEvent(final PayloadAttestationMessage payloadAttestationMessage) {
    super(
        buildTypeDef(payloadAttestationMessage),
        new VersionedPayloadAttestationMessage(SpecMilestone.GLOAS, payloadAttestationMessage));
  }
}
