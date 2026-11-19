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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq;

import java.util.OptionalLong;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsSchemaPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/** The PQ block body, block and XMSS-signed wrappers. */
public class PqBlockSchemas {

  private final SignedExecutionPayloadBidSchema signedExecutionPayloadBidSchema;
  private final SignedExecutionPayloadEnvelopeSchema signedExecutionPayloadEnvelopeSchema;
  private final SignedProposerPreferencesSchema signedProposerPreferencesSchema;
  private final BeaconBlockBodySchemaPq beaconBlockBodySchema;
  private final BeaconBlockSchema beaconBlockSchema;
  private final SignedBeaconBlockSchema signedBeaconBlockSchema;

  public PqBlockSchemas(
      final SchemaRegistry schemaRegistry,
      final PqAttestationSchemas attestationSchemas,
      final ExecutionRequestsSchemaPq executionRequestsSchema) {
    final SpecConfigPq specConfig = SpecConfigPq.required(schemaRegistry.getSpecConfig());
    this.signedExecutionPayloadBidSchema =
        new SignedExecutionPayloadBidSchema(schemaRegistry, SszXmssSignatureSchema.INSTANCE);
    this.signedExecutionPayloadEnvelopeSchema =
        new SignedExecutionPayloadEnvelopeSchema(
            schemaRegistry, "SignedExecutionPayloadEnvelopePq", SszXmssSignatureSchema.INSTANCE);
    this.signedProposerPreferencesSchema =
        new SignedProposerPreferencesSchema(schemaRegistry, SszXmssSignatureSchema.INSTANCE);
    this.beaconBlockBodySchema =
        new BeaconBlockBodySchemaPq(
            "BeaconBlockBodyPq",
            specConfig,
            attestationSchemas,
            signedExecutionPayloadBidSchema,
            executionRequestsSchema);
    this.beaconBlockSchema = new BeaconBlockSchema(beaconBlockBodySchema, "BeaconBlockPq");
    this.signedBeaconBlockSchema =
        new SignedBeaconBlockSchema(
            beaconBlockSchema,
            "SignedBeaconBlockPq",
            SszXmssSignatureSchema.INSTANCE,
            OptionalLong.empty());
  }

  public BeaconBlockBodySchemaPq getBeaconBlockBodySchema() {
    return beaconBlockBodySchema;
  }

  public BeaconBlockSchema getBeaconBlockSchema() {
    return beaconBlockSchema;
  }

  public SignedBeaconBlockSchema getSignedBeaconBlockSchema() {
    return signedBeaconBlockSchema;
  }

  public SignedExecutionPayloadBidSchema getSignedExecutionPayloadBidSchema() {
    return signedExecutionPayloadBidSchema;
  }

  public SignedExecutionPayloadEnvelopeSchema getSignedExecutionPayloadEnvelopeSchema() {
    return signedExecutionPayloadEnvelopeSchema;
  }

  public SignedProposerPreferencesSchema getSignedProposerPreferencesSchema() {
    return signedProposerPreferencesSchema;
  }
}
