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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyBuilderAltair;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderBellatrix extends BeaconBlockBodyBuilderAltair {
  protected ExecutionPayload executionPayload;
  protected ExecutionPayloadHeader executionPayloadHeader;
  protected final BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyBellatrix> blindedSchema;

  public BeaconBlockBodyBuilderBellatrix(
      final BeaconBlockBodySchema<? extends BeaconBlockBodyBellatrix> schema,
      final BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyBellatrix> blindedSchema) {
    super(schema);
    checkState(schema != null || blindedSchema != null, "At least one schema must be specified");
    this.blindedSchema = blindedSchema;
  }

  @Override
  public Boolean supportsExecutionPayload() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayload(final ExecutionPayload executionPayload) {
    this.executionPayload = executionPayload;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayloadHeader(
      final ExecutionPayloadHeader executionPayloadHeader) {
    this.executionPayloadHeader = executionPayloadHeader;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <T> T getAndValidateSchema(final boolean blinded, final Class<T> expectedSchemaType) {
    final BeaconBlockBodySchema<?> resolvedSchema;
    if (blinded) {
      resolvedSchema = blindedSchema;
      checkNotNull(resolvedSchema, "Blinded schema must be specified");
    } else {
      resolvedSchema = schema;
      checkNotNull(resolvedSchema, "Schema must be specified");
    }
    checkArgument(
        expectedSchemaType == resolvedSchema.getClass(),
        "Schema should be %s but was %s",
        expectedSchemaType.getSimpleName(),
        resolvedSchema.getClass().getSimpleName());
    return (T) resolvedSchema;
  }

  @Override
  protected void validate() {
    super.validate();
    checkState(
        executionPayload != null ^ executionPayloadHeader != null,
        "Exactly one of 'executionPayload' or 'executionPayloadHeader' must be set");
  }

  protected Boolean isBlinded() {
    return executionPayloadHeader != null;
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    if (isBlinded()) {
      final BlindedBeaconBlockBodySchemaBellatrixImpl schema =
          getAndValidateSchema(true, BlindedBeaconBlockBodySchemaBellatrixImpl.class);
      return new BlindedBeaconBlockBodyBellatrixImpl(
          schema,
          new SszSignature(randaoReveal),
          eth1Data,
          SszBytes32.of(graffiti),
          proposerSlashings,
          attesterSlashings,
          attestations,
          deposits,
          voluntaryExits,
          syncAggregate,
          executionPayloadHeader.toVersionBellatrix().orElseThrow());
    }

    final BeaconBlockBodySchemaBellatrixImpl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaBellatrixImpl.class);
    return new BeaconBlockBodyBellatrixImpl(
        schema,
        new SszSignature(randaoReveal),
        eth1Data,
        SszBytes32.of(graffiti),
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate,
        executionPayload.toVersionBellatrix().orElseThrow());
  }
}
