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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class LightClientForkDigestPayloadContextTest {
  private static final UInt64 ATTESTED_SLOT = UInt64.valueOf(9);
  private static final UInt64 FINALIZED_SLOT = UInt64.ONE;
  private static final UInt64 SIGNATURE_SLOT = UInt64.valueOf(10);

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitions schemaDefinitions =
      spec.forMilestone(SpecMilestone.ALTAIR).getSchemaDefinitions();

  @Test
  void bootstrap_usesHeaderSlot() {
    final LightClientBootstrap bootstrap =
        dataStructureUtil.randomLightClientBoostrap(ATTESTED_SLOT);

    assertThat(ForkDigestPayloadContext.LIGHT_CLIENT_BOOTSTRAP.getSlotFromPayload(bootstrap))
        .isEqualTo(bootstrap.getLightClientHeader().getBeacon().getSlot());
    assertThat(
            ForkDigestPayloadContext.LIGHT_CLIENT_BOOTSTRAP.getSchemaFromSchemaDefinitions(
                schemaDefinitions))
        .isEqualTo(bootstrap.getSchema());
  }

  @Test
  void update_usesAttestedSlotNotSignatureSlot() {
    final LightClientUpdate update =
        dataStructureUtil
            .createRandomLightClientUpdateBuilder(ATTESTED_SLOT)
            .attestedSlot(ATTESTED_SLOT)
            .signatureSlot(SIGNATURE_SLOT)
            .build();

    assertThat(ForkDigestPayloadContext.LIGHT_CLIENT_UPDATE.getSlotFromPayload(update))
        .isEqualTo(ATTESTED_SLOT);
    assertThat(
            ForkDigestPayloadContext.LIGHT_CLIENT_UPDATE.getSchemaFromSchemaDefinitions(
                schemaDefinitions))
        .isEqualTo(update.getSchema());
  }

  @Test
  void finalityUpdate_usesAttestedSlot() {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(
            ATTESTED_SLOT, FINALIZED_SLOT, SIGNATURE_SLOT);

    assertThat(ForkDigestPayloadContext.LIGHT_CLIENT_FINALITY_UPDATE.getSlotFromPayload(update))
        .isEqualTo(ATTESTED_SLOT);
    assertThat(
            ForkDigestPayloadContext.LIGHT_CLIENT_FINALITY_UPDATE.getSchemaFromSchemaDefinitions(
                schemaDefinitions))
        .isEqualTo(update.getSchema());
  }

  @Test
  void optimisticUpdate_usesAttestedSlot() {
    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(ATTESTED_SLOT, SIGNATURE_SLOT);

    assertThat(ForkDigestPayloadContext.LIGHT_CLIENT_OPTIMISTIC_UPDATE.getSlotFromPayload(update))
        .isEqualTo(ATTESTED_SLOT);
    assertThat(
            ForkDigestPayloadContext.LIGHT_CLIENT_OPTIMISTIC_UPDATE.getSchemaFromSchemaDefinitions(
                schemaDefinitions))
        .isEqualTo(update.getSchema());
  }
}
