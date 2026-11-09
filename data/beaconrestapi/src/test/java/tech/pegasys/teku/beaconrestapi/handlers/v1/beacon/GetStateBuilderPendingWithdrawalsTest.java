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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetStateBuilderPendingWithdrawalsTest
    extends AbstractGetStateFieldTest<SszList<BuilderPendingWithdrawal>> {

  @Override
  protected SpecMilestone getMinimumMilestone() {
    return SpecMilestone.GLOAS;
  }

  @Override
  protected String getFieldLabel() {
    return "builder pending withdrawals";
  }

  @Override
  protected AbstractGetStateField<SszList<BuilderPendingWithdrawal>> createHandler(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    return new GetStateBuilderPendingWithdrawals(provider, schemaDefinitionCache);
  }

  @Override
  protected SszList<BuilderPendingWithdrawal> getField(final BeaconState state) {
    return BeaconStateGloas.required(state).getBuilderPendingWithdrawals();
  }

  @Override
  protected BeaconState withField(
      final BeaconState state, final SszList<BuilderPendingWithdrawal> field) {
    return BeaconStateGloas.required(state)
        .updatedGloas(mutable -> mutable.setBuilderPendingWithdrawals(field));
  }

  @Override
  protected SszList<BuilderPendingWithdrawal> createField() {
    return SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
        .getBuilderPendingWithdrawalsSchema()
        .of(
            dataStructureUtil.randomBuilderPendingWithdrawal(),
            dataStructureUtil.randomBuilderPendingWithdrawal());
  }

  @Test
  void metadata_shouldHandle200WithSpecFieldNames() throws Exception {
    final SszList<BuilderPendingWithdrawal> withdrawals = createField();
    final BuilderPendingWithdrawal withdrawal = withdrawals.get(0);

    final String data =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(withdrawals, SpecMilestone.GLOAS, false, true, false));

    final JsonNode item = JsonTestUtil.parseAsJsonNode(data).get("data").get(0);
    assertThat(item.get("fee_recipient").asText())
        .isEqualToIgnoringCase(withdrawal.getFeeRecipient().toHexString());
    assertThat(item.get("amount").asText()).isEqualTo(withdrawal.getAmount().toString());
    assertThat(item.get("builder_index").asText())
        .isEqualTo(withdrawal.getBuilderIndex().toString());
  }
}
