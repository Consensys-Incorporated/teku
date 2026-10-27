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

import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetStateBuilderPendingPayments
    extends AbstractGetStateField<SszVector<BuilderPendingPayment>> {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/builder_pending_payments";

  public GetStateBuilderPendingPayments(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  GetStateBuilderPendingPayments(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        provider,
        EndpointMetadata.get(ROUTE)
            .operationId("getBuilderPendingPayments")
            .summary("Get State Builder Pending Payments")
            .description(
                "Returns pending builder payments for state with given 'stateId'. Should return 400 if the state retrieved is prior to Gloas."),
        "GetBuilderPendingPaymentsResponse",
        SpecMilestone.GLOAS,
        "builder pending payments",
        SchemaDefinitionsGloas.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
            .getBuilderPendingPaymentsSchema(),
        state -> BeaconStateGloas.required(state).getBuilderPendingPayments());
  }
}
