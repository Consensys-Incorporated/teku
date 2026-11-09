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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBuilderBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapellaImpl;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderCapellaImpl;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderCapella extends BeaconBlockBodyBuilderBellatrix {

  protected SszList<SignedBlsToExecutionChange> blsToExecutionChanges;

  public BeaconBlockBodyBuilderCapella(
      final BeaconBlockBodySchema<? extends BeaconBlockBodyCapella> schema,
      final BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyCapella> blindedSchema) {
    super(schema, blindedSchema);
  }

  protected SszList<SignedBlsToExecutionChange> getBlsToExecutionChanges() {
    return blsToExecutionChanges;
  }

  @Override
  public Boolean supportsBlsToExecutionChanges() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder blsToExecutionChanges(
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
    this.blsToExecutionChanges = blsToExecutionChanges;
    return this;
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blsToExecutionChanges, "blsToExecutionChanges must be specified");
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    if (isBlinded()) {
      final BlindedBeaconBlockBodySchemaCapellaImpl schema =
          getAndValidateSchema(true, BlindedBeaconBlockBodySchemaCapellaImpl.class);
      return new BlindedBeaconBlockBodyCapellaImpl(
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
          (ExecutionPayloadHeaderCapellaImpl)
              executionPayloadHeader.toVersionCapella().orElseThrow(),
          blsToExecutionChanges);
    }

    final BeaconBlockBodySchemaCapellaImpl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaCapellaImpl.class);
    return new BeaconBlockBodyCapellaImpl(
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
        (ExecutionPayloadCapellaImpl) executionPayload.toVersionCapella().orElseThrow(),
        blsToExecutionChanges);
  }
}
