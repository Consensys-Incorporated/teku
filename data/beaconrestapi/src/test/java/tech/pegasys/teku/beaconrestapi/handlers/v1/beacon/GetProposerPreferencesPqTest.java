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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

/** From PQ, proposer preferences carry XMSS signatures. */
class GetProposerPreferencesPqTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalPq());
    setHandler(new GetProposerPreferences(nodeDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldSerializePqPreferencesAsJson() throws Exception {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();

    final String json =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(List.of(preferences), SpecMilestone.PQ, false, false, false));

    assertThat(JsonTestUtil.parseAsJsonNode(json).at("/data/0/signature").asText())
        .isEqualTo(preferences.getXmssSignature().toHexString());
  }

  @Test
  void shouldSerializePqPreferencesAsSsz() throws Exception {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();

    final byte[] ssz =
        getResponseSszFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(List.of(preferences), SpecMilestone.PQ, false, false, false));

    assertThat(Bytes.wrap(ssz))
        .isEqualTo(
            SszProgressiveListSchema.create(preferences.getSchema())
                .createFromElements(List.of(preferences))
                .sszSerialize());
  }
}
