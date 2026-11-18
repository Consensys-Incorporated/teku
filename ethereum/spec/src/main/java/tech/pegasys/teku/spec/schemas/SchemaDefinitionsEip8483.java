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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PQ_REGISTRATION_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PQ_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.VALIDATOR_PQ_REGISTRATION_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.VALIDATOR_PQ_REGISTRATION_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistrationSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistrationSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsEip8483 extends SchemaDefinitionsGloas {

  private final ValidatorPqRegistrationRequestSchema validatorPqRegistrationRequestSchema;
  private final BuilderPqRegistrationRequestSchema builderPqRegistrationRequestSchema;
  private final ValidatorPqRegistrationSchema validatorPqRegistrationSchema;
  private final BuilderPqRegistrationSchema builderPqRegistrationSchema;
  private final ExecutionRequestsSchemaGloas preForkExecutionRequestsSchema;

  public SchemaDefinitionsEip8483(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.validatorPqRegistrationRequestSchema =
        schemaRegistry.get(VALIDATOR_PQ_REGISTRATION_REQUEST_SCHEMA);
    this.builderPqRegistrationRequestSchema =
        schemaRegistry.get(BUILDER_PQ_REGISTRATION_REQUEST_SCHEMA);
    this.validatorPqRegistrationSchema = schemaRegistry.get(VALIDATOR_PQ_REGISTRATION_SCHEMA);
    this.builderPqRegistrationSchema = schemaRegistry.get(BUILDER_PQ_REGISTRATION_SCHEMA);
    this.preForkExecutionRequestsSchema =
        new ExecutionRequestsSchemaGloas(schemaRegistry, "ExecutionRequestsGloas");
  }

  public static SchemaDefinitionsEip8483 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip8483,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsEip8483.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip8483) schemaDefinitions;
  }

  public ValidatorPqRegistrationRequestSchema getValidatorPqRegistrationRequestSchema() {
    return validatorPqRegistrationRequestSchema;
  }

  public BuilderPqRegistrationRequestSchema getBuilderPqRegistrationRequestSchema() {
    return builderPqRegistrationRequestSchema;
  }

  public ValidatorPqRegistrationSchema getValidatorPqRegistrationSchema() {
    return validatorPqRegistrationSchema;
  }

  public BuilderPqRegistrationSchema getBuilderPqRegistrationSchema() {
    return builderPqRegistrationSchema;
  }

  /**
   * The Gloas ExecutionRequests type. A FULL pre-EIP-8483 parent committed to requests of this type
   * in its bid, so the first EIP-8483 block's parent requests are checked against it (see
   * BlockProcessorGloas).
   */
  public ExecutionRequestsSchema<?> getPreForkExecutionRequestsSchema() {
    return preForkExecutionRequestsSchema;
  }

  @Override
  public Optional<SchemaDefinitionsEip8483> toVersionEip8483() {
    return Optional.of(this);
  }
}
