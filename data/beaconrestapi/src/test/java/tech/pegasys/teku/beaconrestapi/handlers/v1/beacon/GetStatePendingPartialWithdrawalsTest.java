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
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class GetStatePendingPartialWithdrawalsTest
    extends AbstractGetStateFieldTest<SszList<PendingPartialWithdrawal>> {

  @Override
  protected SpecMilestone getMinimumMilestone() {
    return SpecMilestone.ELECTRA;
  }

  @Override
  protected String getFieldLabel() {
    return "pending partial withdrawals";
  }

  @Override
  protected AbstractGetStateField<SszList<PendingPartialWithdrawal>> createHandler(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    return new GetStatePendingPartialWithdrawals(provider, schemaDefinitionCache);
  }

  @Override
  protected SszList<PendingPartialWithdrawal> getField(final BeaconState state) {
    return BeaconStateElectra.required(state).getPendingPartialWithdrawals();
  }

  @Override
  protected BeaconState withField(
      final BeaconState state, final SszList<PendingPartialWithdrawal> field) {
    return BeaconStateElectra.required(state)
        .updatedElectra(mutable -> mutable.setPendingPartialWithdrawals(field));
  }

  @Override
  protected SszList<PendingPartialWithdrawal> createField() {
    return SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
        .getPendingPartialWithdrawalsSchema()
        .of(
            dataStructureUtil.randomPendingPartialWithdrawal(),
            dataStructureUtil.randomPendingPartialWithdrawal());
  }

  @Test
  void metadata_shouldHandle200WithSpecFieldNames() throws JsonProcessingException {
    final PendingPartialWithdrawal withdrawal = dataStructureUtil.randomPendingPartialWithdrawal();
    final SszList<PendingPartialWithdrawal> withdrawals =
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
            .getPendingPartialWithdrawalsSchema()
            .of(withdrawal);
    final String data =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(withdrawals, SpecMilestone.ELECTRA, false, true, false));
    final String expected =
        String.format(
            "{\"version\":\"electra\",\"execution_optimistic\":false,\"finalized\":false,"
                + "\"data\":[{\"validator_index\":\"%s\",\"amount\":\"%s\",\"withdrawable_epoch\":\"%s\"}]}",
            withdrawal.getValidatorIndex(),
            withdrawal.getAmount(),
            withdrawal.getWithdrawableEpoch());
    assertThat(data).isEqualTo(expected);
  }
}
