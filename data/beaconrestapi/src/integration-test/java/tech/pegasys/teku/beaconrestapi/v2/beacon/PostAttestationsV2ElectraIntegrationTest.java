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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostAttestationsV2ElectraIntegrationTest extends PostAttestationsV2IntegrationTest {
  protected SerializableTypeDefinition<List<SingleAttestation>> attestationsListTypeDef;

  @Override
  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalElectra();
    specMilestone = SpecMilestone.ELECTRA;
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = new DataStructureUtil(spec);
    attestationsListTypeDef =
        SerializableTypeDefinition.listOf(
            SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
                .getSingleAttestationSchema()
                .getJsonTypeDefinition());
  }

  @Override
  protected List<Attestation> getAttestationList(final int listSize) {
    final List<Attestation> attestations = new ArrayList<>(listSize);
    for (int i = 0; i < listSize; i++) {
      attestations.add(dataStructureUtil.randomSingleAttestation());
    }
    return attestations;
  }

  @Override
  protected AttestationSchema<Attestation> getAttestationSchema() {
    return SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
        .getSingleAttestationSchema()
        .castTypeToAttestationSchema();
  }
}
