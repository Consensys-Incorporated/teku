/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.addon;

import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.RestApiBuilderAddon;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetPayloadAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetSignedExecutionPayloadEnvelope;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostExecutionPayloadEnvelope;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostPayloadAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostPublishExecutionPayloadBid;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostStateBuilders;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetExecutionPayloadBid;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetExecutionPayloadEnvelope;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetPayloadAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostProposerPreferences;
import tech.pegasys.teku.infrastructure.restapi.RestApiBuilder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GloasRestApiBuilderAddon implements RestApiBuilderAddon {

  final Spec spec;
  final DataProvider dataProvider;
  final SchemaDefinitionCache schemaCache;

  public GloasRestApiBuilderAddon(
      final Spec spec, final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this.spec = spec;
    this.dataProvider = dataProvider;
    this.schemaCache = schemaCache;
  }

  @Override
  public boolean isEnabled() {
    return spec.isMilestoneSupported(SpecMilestone.GLOAS);
  }

  @Override
  public RestApiBuilder apply(final RestApiBuilder builder) {
    return builder
        .endpoint(new GetSignedExecutionPayloadEnvelope(dataProvider, schemaCache))
        .endpoint(new GetPayloadAttestations(dataProvider, schemaCache))
        .endpoint(
            new PostExecutionPayloadEnvelope(dataProvider.getValidatorDataProvider(), schemaCache))
        .endpoint(new PostPayloadAttestations(dataProvider, spec, schemaCache))
        .endpoint(new GetExecutionPayloadBid(dataProvider, schemaCache))
        .endpoint(new GetExecutionPayloadEnvelope(dataProvider, schemaCache))
        .endpoint(new GetPayloadAttestationData(dataProvider, schemaCache))
        .endpoint(new PostPublishExecutionPayloadBid(dataProvider, schemaCache))
        .endpoint(new PostProposerPreferences(dataProvider, spec, schemaCache))
        .endpoint(new PostStateBuilders(dataProvider));
  }
}
