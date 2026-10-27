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
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetStateBuilderPendingPaymentsTest
    extends AbstractGetStateFieldTest<SszVector<BuilderPendingPayment>> {

  @Override
  protected SpecMilestone getMinimumMilestone() {
    return SpecMilestone.GLOAS;
  }

  @Override
  protected String getFieldLabel() {
    return "builder pending payments";
  }

  @Override
  protected AbstractGetStateField<SszVector<BuilderPendingPayment>> createHandler(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    return new GetStateBuilderPendingPayments(provider, schemaDefinitionCache);
  }

  @Override
  protected SszVector<BuilderPendingPayment> getField(final BeaconState state) {
    return BeaconStateGloas.required(state).getBuilderPendingPayments();
  }

  @Override
  protected BeaconState withField(
      final BeaconState state, final SszVector<BuilderPendingPayment> field) {
    return BeaconStateGloas.required(state)
        .updatedGloas(mutable -> mutable.setBuilderPendingPayments(field));
  }

  @Override
  protected SszVector<BuilderPendingPayment> createField() {
    final SszVectorSchema<BuilderPendingPayment, ?> schema =
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
            .getBuilderPendingPaymentsSchema();
    return schema.createFromElements(
        Stream.generate(dataStructureUtil::randomBuilderPendingPayment)
            .limit(schema.getLength())
            .toList());
  }

  @Test
  void metadata_shouldHandle200WithSpecFieldNames() throws Exception {
    final SszVector<BuilderPendingPayment> payments = createField();
    final BuilderPendingPayment payment = payments.get(0);

    final String data =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(payments, SpecMilestone.GLOAS, false, true, false));

    final JsonNode dataNode = JsonTestUtil.parseAsJsonNode(data).get("data");
    assertThat(dataNode.size()).isEqualTo(2 * spec.getGenesisSpecConfig().getSlotsPerEpoch());
    final JsonNode item = dataNode.get(0);
    assertThat(item.get("weight").asText()).isEqualTo(payment.getWeight().toString());
    assertThat(item.get("withdrawal").get("amount").asText())
        .isEqualTo(payment.getWithdrawal().getAmount().toString());
    assertThat(item.get("proposer_index").asText())
        .isEqualTo(payment.getProposerIndex().toString());
  }
}
