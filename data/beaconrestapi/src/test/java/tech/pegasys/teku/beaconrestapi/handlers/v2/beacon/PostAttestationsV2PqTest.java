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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;

/** PQ validators submit single attestations signed with XMSS. */
class PostAttestationsV2PqTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalPq());
    setHandler(new PostAttestationsV2(validatorDataProvider, spec, schemaDefinitionCache));
  }

  @Test
  void shouldReadPqSingleAttestations() throws Exception {
    final SingleAttestationPq attestation =
        new SingleAttestationPq(
            UInt64.ONE,
            UInt64.valueOf(7),
            dataStructureUtil.randomAttestationData(),
            dataStructureUtil.randomXmssSignature().getBytes());
    final String json =
        JsonUtil.serialize(
            List.of(attestation),
            DeserializableTypeDefinition.listOf(
                SingleAttestationPq.SSZ_SCHEMA.getJsonTypeDefinition()));

    final Object requestBody =
        getRequestBodyFromMetadata(handler, Map.of(HEADER_CONSENSUS_VERSION, "pq"), json);

    assertThat(requestBody).isEqualTo(List.of(attestation));
  }
}
