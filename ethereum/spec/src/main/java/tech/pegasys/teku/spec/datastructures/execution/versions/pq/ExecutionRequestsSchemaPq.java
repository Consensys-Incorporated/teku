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

package tech.pegasys.teku.spec.datastructures.execution.versions.pq;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.BUILDER_DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.BUILDER_EXITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.BUILDER_PREREGISTRATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.VALIDATOR_PREREGISTRATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.VOLUNTARY_EXITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsFieldsPq.WITHDRAWALS;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_DEPOSIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_EXIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CONSOLIDATION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DEPOSIT_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_REQUESTS_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * {@code ExecutionRequests} in PQ: a progressive container 10 fields wide with gaps at 5 and 6,
 * where EIP-8483's PQ registrations were.
 */
public class ExecutionRequestsSchemaPq
    extends ContainerSchema8<
        ExecutionRequestsPq,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>,
        SszList<BuilderDepositRequest>,
        SszList<BuilderExitRequest>,
        SszList<ValidatorPreregistrationRequest>,
        SszList<BuilderPreregistrationRequest>,
        SszList<VoluntaryExitRequest>>
    implements ExecutionRequestsSchema<ExecutionRequestsPq> {

  private static final boolean[] ACTIVE_FIELDS = {
    true, true, true, true, true, false, false, true, true, true
  };

  public static ExecutionRequestsSchemaPq required(final ExecutionRequestsSchema<?> schema) {
    checkArgument(
        schema instanceof ExecutionRequestsSchemaPq,
        "Expected a ExecutionRequestsSchemaPq but was %s",
        schema.getClass());
    return (ExecutionRequestsSchemaPq) schema;
  }

  @SuppressWarnings("unchecked")
  public ExecutionRequestsSchemaPq(
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
            VALIDATOR_PREREGISTRATIONS,
            SszProgressiveListSchema.create(
                ValidatorPreregistrationRequest.SSZ_SCHEMA,
                SpecConfigPq.required(schemaRegistry.getSpecConfig())
                    .getMaxValidatorPreregistrationRequestsPerPayload())),
        namedSchema(
            BUILDER_PREREGISTRATIONS,
            SszProgressiveListSchema.create(
                BuilderPreregistrationRequest.SSZ_SCHEMA,
                SpecConfigPq.required(schemaRegistry.getSpecConfig())
                    .getMaxBuilderPreregistrationRequestsPerPayload())),
        namedSchema(
            VOLUNTARY_EXITS,
            SszProgressiveListSchema.create(
                VoluntaryExitRequest.SSZ_SCHEMA,
                SpecConfigPq.required(schemaRegistry.getSpecConfig())
                    .getMaxVoluntaryExitRequestsPerPayload())));
  }

  public ExecutionRequestsPq create(
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations,
      final List<BuilderDepositRequest> builderDeposits,
      final List<BuilderExitRequest> builderExits,
      final List<ValidatorPreregistrationRequest> validatorPreregistrations,
      final List<BuilderPreregistrationRequest> builderPreregistrations,
      final List<VoluntaryExitRequest> voluntaryExits) {
    return new ExecutionRequestsPq(
        this,
        deposits,
        withdrawals,
        consolidations,
        builderDeposits,
        builderExits,
        validatorPreregistrations,
        builderPreregistrations,
        voluntaryExits);
  }

  @Override
  public ExecutionRequestsPq createFromBackingNode(final TreeNode node) {
    return new ExecutionRequestsPq(this, node);
  }

  @Override
  public ExecutionRequestsBuilder createBuilder() {
    return new ExecutionRequestsBuilderPq(this);
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
  public SszListSchema<ValidatorPreregistrationRequest, ?>
      getValidatorPreregistrationRequestsSchema() {
    return (SszListSchema<ValidatorPreregistrationRequest, ?>)
        getChildSchema(getFieldIndex(VALIDATOR_PREREGISTRATIONS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderPreregistrationRequest, ?> getBuilderPreregistrationRequestsSchema() {
    return (SszListSchema<BuilderPreregistrationRequest, ?>)
        getChildSchema(getFieldIndex(BUILDER_PREREGISTRATIONS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<VoluntaryExitRequest, ?> getVoluntaryExitRequestsSchema() {
    return (SszListSchema<VoluntaryExitRequest, ?>) getChildSchema(getFieldIndex(VOLUNTARY_EXITS));
  }
}
