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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;

import com.google.common.base.Suppliers;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsSchemaPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStateSchemaPq;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * Schemas for the PQ fork. Until PQ's own containers are all in place and its state transition uses
 * them, the milestone's schemas are EIP-8483's; PQ's own are built alongside them.
 */
public class SchemaDefinitionsPq extends SchemaDefinitionsEip8483 {

  private final Supplier<BeaconStateSchemaPq> pqBeaconStateSchema;
  private final Supplier<ExecutionRequestsSchemaPq> pqExecutionRequestsSchema;

  public SchemaDefinitionsPq(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.pqBeaconStateSchema =
        Suppliers.memoize(
            () -> BeaconStateSchemaPq.create(schemaRegistry.getSpecConfig(), schemaRegistry));
    this.pqExecutionRequestsSchema =
        Suppliers.memoize(
            () ->
                new ExecutionRequestsSchemaPq(
                    schemaRegistry, EXECUTION_REQUESTS_SCHEMA.getSchemaName(SpecMilestone.PQ)));
  }

  public static SchemaDefinitionsPq required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsPq,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsPq.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsPq) schemaDefinitions;
  }

  /**
   * The PQ state schema. Not yet the milestone's {@link #getBeaconStateSchema()}: that switches
   * over with PQ's state transition.
   */
  @NonSchema
  public BeaconStateSchemaPq getPqBeaconStateSchema() {
    return pqBeaconStateSchema.get();
  }

  /**
   * The PQ execution requests schema. Not yet the milestone's {@link
   * #getExecutionRequestsSchema()}: that switches over with PQ's state transition.
   */
  @NonSchema
  public ExecutionRequestsSchemaPq getPqExecutionRequestsSchema() {
    return pqExecutionRequestsSchema.get();
  }

  @Override
  public Optional<SchemaDefinitionsPq> toVersionPq() {
    return Optional.of(this);
  }
}
