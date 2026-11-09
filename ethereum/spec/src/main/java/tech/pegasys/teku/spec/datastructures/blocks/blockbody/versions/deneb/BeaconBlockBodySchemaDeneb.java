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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BeaconBlockBodySchemaDeneb<T extends BeaconBlockBodyDeneb>
    extends BeaconBlockBodySchemaCapella<T> {

  static BeaconBlockBodySchemaDeneb<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BeaconBlockBodySchemaDeneb,
        "Expected a BeaconBlockBodySchemaDeneb but was %s",
        schema.getClass());
    return (BeaconBlockBodySchemaDeneb<?>) schema;
  }

  SszListSchema<SszKZGCommitment, ?> getBlobKzgCommitmentsSchema();

  long getBlobKzgCommitmentsGeneralizedIndex();

  @Override
  default Optional<BeaconBlockBodySchemaDeneb<?>> toVersionDeneb() {
    return Optional.of(this);
  }
}
