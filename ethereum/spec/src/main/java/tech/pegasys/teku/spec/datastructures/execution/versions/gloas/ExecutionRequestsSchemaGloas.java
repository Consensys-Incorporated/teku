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

package tech.pegasys.teku.spec.datastructures.execution.versions.gloas;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas.BUILDER_DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas.BUILDER_EXITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas.CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas.DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas.WITHDRAWALS;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_DEPOSIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_EXIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CONSOLIDATION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DEPOSIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_REQUESTS_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class ExecutionRequestsSchemaGloas
    extends ContainerSchema5<
        ExecutionRequestsGloas,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>,
        SszList<BuilderDepositRequest>,
        SszList<BuilderExitRequest>>
    implements ExecutionRequestsSchema<ExecutionRequestsGloas> {

  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(5);

  public static ExecutionRequestsSchemaGloas required(final ExecutionRequestsSchema<?> schema) {
    checkArgument(
        schema instanceof ExecutionRequestsSchemaGloas,
        "Expected a ExecutionRequestsSchemaGloas but was %s",
        schema.getClass());
    return (ExecutionRequestsSchemaGloas) schema;
  }

  @SuppressWarnings("unchecked")
  public ExecutionRequestsSchemaGloas(
      final SchemaRegistry schemaRegistry, final String containerName) {
    super(
        containerName,
        ACTIVE_FIELDS,
        namedSchema(
            DEPOSITS,
            (SszListSchema<DepositRequest, SszList<DepositRequest>>)
                schemaRegistry.get(DEPOSIT_REQUESTS_SCHEMA)),
        namedSchema(
            WITHDRAWALS,
            (SszListSchema<WithdrawalRequest, SszList<WithdrawalRequest>>)
                schemaRegistry.get(WITHDRAWAL_REQUESTS_SCHEMA)),
        namedSchema(
            CONSOLIDATIONS,
            (SszListSchema<ConsolidationRequest, SszList<ConsolidationRequest>>)
                schemaRegistry.get(CONSOLIDATION_REQUESTS_SCHEMA)),
        namedSchema(
            BUILDER_DEPOSITS,
            (SszListSchema<BuilderDepositRequest, SszList<BuilderDepositRequest>>)
                schemaRegistry.get(BUILDER_DEPOSIT_REQUESTS_SCHEMA)),
        namedSchema(
            BUILDER_EXITS,
            (SszListSchema<BuilderExitRequest, SszList<BuilderExitRequest>>)
                schemaRegistry.get(BUILDER_EXIT_REQUESTS_SCHEMA)));
  }

  public ExecutionRequestsGloas create(
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations,
      final List<BuilderDepositRequest> builderDeposits,
      final List<BuilderExitRequest> builderExits) {
    return new ExecutionRequestsGloas(
        this, deposits, withdrawals, consolidations, builderDeposits, builderExits);
  }

  @Override
  public ExecutionRequestsGloas createFromBackingNode(final TreeNode node) {
    return new ExecutionRequestsGloas(this, node);
  }

  @Override
  public ExecutionRequestsBuilder createBuilder() {
    return new ExecutionRequestsBuilderGloas(this);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<DepositRequest, ?> getDepositRequestsSchema() {
    return (SszListSchema<DepositRequest, ?>) getChildSchema(getFieldIndex(DEPOSITS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<WithdrawalRequest, ?> getWithdrawalRequestsSchema() {
    return (SszListSchema<WithdrawalRequest, ?>) getChildSchema(getFieldIndex(WITHDRAWALS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<ConsolidationRequest, ?> getConsolidationRequestsSchema() {
    return (SszListSchema<ConsolidationRequest, ?>) getChildSchema(getFieldIndex(CONSOLIDATIONS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderDepositRequest, ?> getBuilderDepositRequestsSchema() {
    return (SszListSchema<BuilderDepositRequest, ?>)
        getChildSchema(getFieldIndex(BUILDER_DEPOSITS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderExitRequest, ?> getBuilderExitRequestsSchema() {
    return (SszListSchema<BuilderExitRequest, ?>) getChildSchema(getFieldIndex(BUILDER_EXITS));
  }
}
