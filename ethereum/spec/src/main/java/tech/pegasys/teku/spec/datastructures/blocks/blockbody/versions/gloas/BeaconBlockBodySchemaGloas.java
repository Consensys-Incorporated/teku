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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;

public interface BeaconBlockBodySchemaGloas<T extends BeaconBlockBodyGloas>
    extends BeaconBlockBodySchemaElectra<T> {

  static BeaconBlockBodySchemaGloas<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BeaconBlockBodySchemaGloas<?>,
        "Expected a BeaconBlockBodySchemaGloas but was %s",
        schema.getClass());
    return (BeaconBlockBodySchemaGloas<?>) schema;
  }

  SszListSchema<PayloadAttestation, ?> getPayloadAttestationsSchema();

  ExecutionRequestsSchema<?> getParentExecutionRequestsSchema();

  @Override
  default Optional<BeaconBlockBodySchemaGloas<?>> toVersionGloas() {
    return Optional.of(this);
  }
}
