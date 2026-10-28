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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class GetStatePendingConsolidationsTest
    extends AbstractGetStateFieldTest<SszList<PendingConsolidation>> {

  @Override
  protected SpecMilestone getMinimumMilestone() {
    return SpecMilestone.ELECTRA;
  }

  @Override
  protected String getFieldLabel() {
    return "pending consolidations";
  }

  @Override
  protected AbstractGetStateField<SszList<PendingConsolidation>> createHandler(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    return new GetStatePendingConsolidations(provider, schemaDefinitionCache);
  }

  @Override
  protected SszList<PendingConsolidation> getField(final BeaconState state) {
    return BeaconStateElectra.required(state).getPendingConsolidations();
  }

  @Override
  protected BeaconState withField(
      final BeaconState state, final SszList<PendingConsolidation> field) {
    return BeaconStateElectra.required(state)
        .updatedElectra(mutable -> mutable.setPendingConsolidations(field));
  }

  @Override
  protected SszList<PendingConsolidation> createField() {
    return SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
        .getPendingConsolidationsSchema()
        .of(
            dataStructureUtil.randomPendingConsolidation(),
            dataStructureUtil.randomPendingConsolidation());
  }

  @Test
  void metadata_shouldHandle200WithSpecFieldNames() throws JsonProcessingException {
    final PendingConsolidation consolidation = dataStructureUtil.randomPendingConsolidation();
    final SszList<PendingConsolidation> consolidations =
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
            .getPendingConsolidationsSchema()
            .of(consolidation);
    final String data =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(consolidations, SpecMilestone.ELECTRA, false, true, false));
    final String expected =
        String.format(
            "{\"version\":\"electra\",\"execution_optimistic\":false,\"finalized\":false,"
                + "\"data\":[{\"source_index\":\"%s\",\"target_index\":\"%s\"}]}",
            consolidation.getSourceIndex(), consolidation.getTargetIndex());
    assertThat(data).isEqualTo(expected);
  }
}
