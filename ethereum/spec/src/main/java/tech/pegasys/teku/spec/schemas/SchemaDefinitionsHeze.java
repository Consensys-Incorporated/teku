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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INCLUSION_LIST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_INCLUSION_LIST_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionListSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsHeze extends SchemaDefinitionsGloas {

  private final InclusionListSchema inclusionListSchema;
  private final SignedInclusionListSchema signedInclusionListSchema;

  public SchemaDefinitionsHeze(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.inclusionListSchema = schemaRegistry.get(INCLUSION_LIST_SCHEMA);
    this.signedInclusionListSchema = schemaRegistry.get(SIGNED_INCLUSION_LIST_SCHEMA);
  }

  public static SchemaDefinitionsHeze required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsHeze,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsHeze.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsHeze) schemaDefinitions;
  }

  public InclusionListSchema getInclusionListSchema() {
    return inclusionListSchema;
  }

  public SignedInclusionListSchema getSignedInclusionListSchema() {
    return signedInclusionListSchema;
  }

  @Override
  public Optional<SchemaDefinitionsHeze> toVersionHeze() {
    return Optional.of(this);
  }
}
