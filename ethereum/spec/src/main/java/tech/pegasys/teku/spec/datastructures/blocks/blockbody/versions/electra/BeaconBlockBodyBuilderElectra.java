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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyBuilderDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDenebImpl;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderDenebImpl;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderElectra extends BeaconBlockBodyBuilderDeneb {

  private ExecutionRequests executionRequests;

  public BeaconBlockBodyBuilderElectra(
      final BeaconBlockBodySchema<? extends BeaconBlockBodyElectra> schema,
      final BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyElectra> blindedSchema) {
    super(schema, blindedSchema);
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(executionRequests, "execution_requests must be specified");
  }

  @Override
  public boolean supportsExecutionRequests() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder executionRequests(final ExecutionRequests executionRequests) {
    this.executionRequests = executionRequests;
    return this;
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    if (isBlinded()) {
      final BlindedBeaconBlockBodySchemaElectraImpl schema =
          getAndValidateSchema(true, BlindedBeaconBlockBodySchemaElectraImpl.class);
      return new BlindedBeaconBlockBodyElectraImpl(
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
          (ExecutionPayloadHeaderDenebImpl) executionPayloadHeader.toVersionDeneb().orElseThrow(),
          getBlsToExecutionChanges(),
          getBlobKzgCommitments(),
          executionRequests);
    }

    final BeaconBlockBodySchemaElectraImpl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaElectraImpl.class);
    return new BeaconBlockBodyElectraImpl(
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
        (ExecutionPayloadDenebImpl) executionPayload.toVersionDeneb().orElseThrow(),
        getBlsToExecutionChanges(),
        getBlobKzgCommitments(),
        executionRequests);
  }
}
