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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;

public interface BeaconBlockBodySchemaCapella<T extends BeaconBlockBodyCapella>
    extends BeaconBlockBodySchemaBellatrix<T> {

  static BeaconBlockBodySchemaCapella<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BeaconBlockBodySchemaCapella,
        "Expected a BeaconBlockBodySchemaCapella but was %s",
        schema.getClass());
    return (BeaconBlockBodySchemaCapella<?>) schema;
  }

  SszListSchema<SignedBlsToExecutionChange, ?> getBlsToExecutionChangesSchema();

  @Override
  default Optional<BeaconBlockBodySchemaCapella<?>> toVersionCapella() {
    return Optional.of(this);
  }
}
