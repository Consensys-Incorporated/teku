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

import java.util.Optional;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/** Schemas for the PQ fork. Until PQ's own containers land, these are EIP-8483's. */
public class SchemaDefinitionsPq extends SchemaDefinitionsEip8483 {

  public SchemaDefinitionsPq(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
  }

  public static SchemaDefinitionsPq required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsPq,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsPq.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsPq) schemaDefinitions;
  }

  @Override
  public Optional<SchemaDefinitionsPq> toVersionPq() {
    return Optional.of(this);
  }
}
