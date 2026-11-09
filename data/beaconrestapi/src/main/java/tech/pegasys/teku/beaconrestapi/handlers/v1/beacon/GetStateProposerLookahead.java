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
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class GetStateProposerLookahead extends AbstractGetStateField<SszUInt64Vector> {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/proposer_lookahead";

  public GetStateProposerLookahead(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  GetStateProposerLookahead(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        provider,
        EndpointMetadata.get(ROUTE)
            .operationId("getStateProposerLookahead")
            .summary("Get proposer look ahead from state")
            .description(
                "Returns proposer lookahead for state with given 'stateId'. Should return 400 if the state retrieved is prior to Fulu."),
        "GetProposerLookaheadResponse",
        SpecMilestone.FULU,
        "proposer lookahead",
        SchemaDefinitionsFulu.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.FULU))
            .getProposerLookaheadSchema(),
        state -> BeaconStateFulu.required(state).getProposerLookahead());
  }
}
