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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientUpdateSerializerTest {
  private static final UInt64 ATTESTED_SLOT = UInt64.ONE;

  @TestTemplate
  void roundTrip_update(final SpecContext specContext) {
    final KvStoreSerializer<LightClientUpdate> updateSerializer =
        new LightClientUpdateSerializer(specContext.getSpec());
    final LightClientUpdate value = randomUpdate(specContext.getDataStructureUtil());

    final byte[] bytes = updateSerializer.serialize(value);
    final LightClientUpdate deserialized = updateSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @TestTemplate
  void serialize_prefixesTheAttestedSlot(final SpecContext specContext) {
    final KvStoreSerializer<LightClientUpdate> updateSerializer =
        new LightClientUpdateSerializer(specContext.getSpec());
    final LightClientUpdate value = randomUpdate(specContext.getDataStructureUtil());

    final byte[] bytes = updateSerializer.serialize(value);

    assertThat(bytes).hasSize(Long.BYTES + value.sszSerialize().size());
    assertThat(Bytes.wrap(bytes).slice(0, Long.BYTES))
        .isEqualTo(Bytes.ofUnsignedLong(ATTESTED_SLOT.longValue()));
  }

  @Test
  void roundTrip_updateStoredBeforeALaterScheduledFork() {
    final Spec spec = TestSpecFactory.createMinimalWithElectraForkEpoch(UInt64.valueOf(2));
    final KvStoreSerializer<LightClientUpdate> updateSerializer =
        new LightClientUpdateSerializer(spec);
    final LightClientUpdate value = randomUpdate(new DataStructureUtil(spec));

    final byte[] bytes = updateSerializer.serialize(value);
    final LightClientUpdate deserialized = updateSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  private static LightClientUpdate randomUpdate(final DataStructureUtil dataStructureUtil) {
    return dataStructureUtil.createRandomLightClientUpdateBuilder(ATTESTED_SLOT).build();
  }
}
