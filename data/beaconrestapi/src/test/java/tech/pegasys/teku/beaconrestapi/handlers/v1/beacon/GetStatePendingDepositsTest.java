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
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class GetStatePendingDepositsTest
    extends AbstractGetStateFieldTest<SszList<PendingDeposit>> {

  @Override
  protected SpecMilestone getMinimumMilestone() {
    return SpecMilestone.ELECTRA;
  }

  @Override
  protected String getFieldLabel() {
    return "pending deposits";
  }

  @Override
  protected AbstractGetStateField<SszList<PendingDeposit>> createHandler(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    return new GetStatePendingDeposits(provider, schemaDefinitionCache);
  }

  @Override
  protected SszList<PendingDeposit> getField(final BeaconState state) {
    return BeaconStateElectra.required(state).getPendingDeposits();
  }

  @Override
  protected BeaconState withField(final BeaconState state, final SszList<PendingDeposit> field) {
    return BeaconStateElectra.required(state)
        .updatedElectra(mutable -> mutable.setPendingDeposits(field));
  }

  @Override
  protected SszList<PendingDeposit> createField() {
    return SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
        .getPendingDepositsSchema()
        .of(dataStructureUtil.randomPendingDeposit(), dataStructureUtil.randomPendingDeposit());
  }

  @Test
  void metadata_shouldHandle200WithSpecFieldNames() throws JsonProcessingException {
    final PendingDeposit deposit = dataStructureUtil.randomPendingDeposit();
    final SszList<PendingDeposit> deposits =
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
            .getPendingDepositsSchema()
            .of(deposit);
    final String data =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(deposits, SpecMilestone.ELECTRA, false, true, false));
    final String expected =
        String.format(
            "{\"version\":\"electra\",\"execution_optimistic\":false,\"finalized\":false,"
                + "\"data\":[{\"pubkey\":\"%s\",\"withdrawal_credentials\":\"%s\",\"amount\":\"%s\",\"signature\":\"%s\",\"slot\":\"%s\"}]}",
            deposit.getPublicKey(),
            deposit.getWithdrawalCredentials(),
            deposit.getAmount(),
            deposit.getSignature(),
            deposit.getSlot());
    assertThat(data).isEqualTo(expected);
  }
}
