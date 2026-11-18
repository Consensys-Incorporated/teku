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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip8483;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.BUILDER_DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.BUILDER_EXITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.BUILDER_PQ_REGISTRATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.VALIDATOR_PQ_REGISTRATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ExecutionRequestsFieldsEip8483.WITHDRAWALS;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_DEPOSIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_EXIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PQ_REGISTRATION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CONSOLIDATION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DEPOSIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.VALIDATOR_PQ_REGISTRATION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_REQUESTS_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema7;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class ExecutionRequestsSchemaEip8483
    extends ContainerSchema7<
        ExecutionRequestsEip8483,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>,
        SszList<BuilderDepositRequest>,
        SszList<BuilderExitRequest>,
        SszList<ValidatorPqRegistrationRequest>,
        SszList<BuilderPqRegistrationRequest>>
    implements ExecutionRequestsSchema<ExecutionRequestsEip8483> {

  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(7);

  public static ExecutionRequestsSchemaEip8483 required(final ExecutionRequestsSchema<?> schema) {
    checkArgument(
        schema instanceof ExecutionRequestsSchemaEip8483,
        "Expected a ExecutionRequestsSchemaEip8483 but was %s",
        schema.getClass());
    return (ExecutionRequestsSchemaEip8483) schema;
  }

  @SuppressWarnings("unchecked")
  public ExecutionRequestsSchemaEip8483(
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
                schemaRegistry.get(BUILDER_EXIT_REQUESTS_SCHEMA)),
        namedSchema(
            VALIDATOR_PQ_REGISTRATIONS,
            (SszListSchema<ValidatorPqRegistrationRequest, SszList<ValidatorPqRegistrationRequest>>)
                schemaRegistry.get(VALIDATOR_PQ_REGISTRATION_REQUESTS_SCHEMA)),
        namedSchema(
            BUILDER_PQ_REGISTRATIONS,
            (SszListSchema<BuilderPqRegistrationRequest, SszList<BuilderPqRegistrationRequest>>)
                schemaRegistry.get(BUILDER_PQ_REGISTRATION_REQUESTS_SCHEMA)));
  }

  public ExecutionRequestsEip8483 create(
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations,
      final List<BuilderDepositRequest> builderDeposits,
      final List<BuilderExitRequest> builderExits,
      final List<ValidatorPqRegistrationRequest> validatorPqRegistrations,
      final List<BuilderPqRegistrationRequest> builderPqRegistrations) {
    return new ExecutionRequestsEip8483(
        this,
        deposits,
        withdrawals,
        consolidations,
        builderDeposits,
        builderExits,
        validatorPqRegistrations,
        builderPqRegistrations);
  }

  @Override
  public ExecutionRequestsEip8483 createFromBackingNode(final TreeNode node) {
    return new ExecutionRequestsEip8483(this, node);
  }

  @Override
  public ExecutionRequestsBuilder createBuilder() {
    return new ExecutionRequestsBuilderEip8483(this);
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

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderDepositRequest, ?> getBuilderDepositRequestsSchema() {
    return (SszListSchema<BuilderDepositRequest, ?>)
        getChildSchema(getFieldIndex(BUILDER_DEPOSITS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderExitRequest, ?> getBuilderExitRequestsSchema() {
    return (SszListSchema<BuilderExitRequest, ?>) getChildSchema(getFieldIndex(BUILDER_EXITS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<ValidatorPqRegistrationRequest, ?>
      getValidatorPqRegistrationRequestsSchema() {
    return (SszListSchema<ValidatorPqRegistrationRequest, ?>)
        getChildSchema(getFieldIndex(VALIDATOR_PQ_REGISTRATIONS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderPqRegistrationRequest, ?> getBuilderPqRegistrationRequestsSchema() {
    return (SszListSchema<BuilderPqRegistrationRequest, ?>)
        getChildSchema(getFieldIndex(BUILDER_PQ_REGISTRATIONS));
  }
}
