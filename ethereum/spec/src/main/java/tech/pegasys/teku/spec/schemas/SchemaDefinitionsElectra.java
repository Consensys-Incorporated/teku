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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CONSOLIDATION_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DEPOSIT_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PROOF_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.LIGHT_CLIENT_BOOTSTRAP_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_CONSOLIDATIONS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_DEPOSITS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_PARTIAL_WITHDRAWALS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SINGLE_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_REQUEST_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyBuilderElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequestSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation.PendingConsolidationSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit.PendingDepositSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal.PendingPartialWithdrawalSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsElectra extends SchemaDefinitionsDeneb {
  private final ExecutionRequestsSchema<? extends ExecutionRequests> executionRequestsSchema;
  private final DepositRequestSchema depositRequestSchema;
  private final WithdrawalRequestSchema withdrawalRequestSchema;
  private final ConsolidationRequestSchema consolidationRequestSchema;

  private final SszListSchema<PendingDeposit, ?> pendingDepositsSchema;
  private final SszListSchema<PendingPartialWithdrawal, ?> pendingPartialWithdrawalsSchema;
  private final SszListSchema<PendingConsolidation, ?> pendingConsolidationsSchema;

  private final PendingDepositSchema pendingDepositSchema;
  private final PendingPartialWithdrawalSchema pendingPartialWithdrawalSchema;
  private final PendingConsolidationSchema pendingConsolidationSchema;
  private final LightClientBootstrapSchema lightClientBootstrapSchema;

  private final SingleAttestationSchema singleAttestationSchema;

  private final ExecutionProofSchema executionProofSchema;

  public SchemaDefinitionsElectra(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.executionRequestsSchema = schemaRegistry.get(EXECUTION_REQUESTS_SCHEMA);
    this.pendingDepositsSchema = schemaRegistry.get(PENDING_DEPOSITS_SCHEMA);
    this.pendingPartialWithdrawalsSchema = schemaRegistry.get(PENDING_PARTIAL_WITHDRAWALS_SCHEMA);
    this.pendingConsolidationsSchema = schemaRegistry.get(PENDING_CONSOLIDATIONS_SCHEMA);

    this.singleAttestationSchema = schemaRegistry.get(SINGLE_ATTESTATION_SCHEMA);

    this.depositRequestSchema = schemaRegistry.get(DEPOSIT_REQUEST_SCHEMA);
    this.withdrawalRequestSchema = schemaRegistry.get(WITHDRAWAL_REQUEST_SCHEMA);
    this.consolidationRequestSchema = schemaRegistry.get(CONSOLIDATION_REQUEST_SCHEMA);
    this.pendingDepositSchema =
        (PendingDepositSchema) schemaRegistry.get(PENDING_DEPOSITS_SCHEMA).getElementSchema();
    this.pendingPartialWithdrawalSchema =
        (PendingPartialWithdrawalSchema)
            schemaRegistry.get(PENDING_PARTIAL_WITHDRAWALS_SCHEMA).getElementSchema();
    this.pendingConsolidationSchema =
        (PendingConsolidationSchema)
            schemaRegistry.get(PENDING_CONSOLIDATIONS_SCHEMA).getElementSchema();
    this.lightClientBootstrapSchema = schemaRegistry.get(LIGHT_CLIENT_BOOTSTRAP_SCHEMA);

    this.executionProofSchema = schemaRegistry.get(EXECUTION_PROOF_SCHEMA);
  }

  public static SchemaDefinitionsElectra required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsElectra,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsElectra.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsElectra) schemaDefinitions;
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderElectra(
        getBeaconBlockBodySchema().toVersionElectra().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionElectra().orElseThrow());
  }

  public ExecutionRequestsSchema<? extends ExecutionRequests> getExecutionRequestsSchema() {
    return executionRequestsSchema;
  }

  public DepositRequestSchema getDepositRequestSchema() {
    return depositRequestSchema;
  }

  public WithdrawalRequestSchema getWithdrawalRequestSchema() {
    return withdrawalRequestSchema;
  }

  public ConsolidationRequestSchema getConsolidationRequestSchema() {
    return consolidationRequestSchema;
  }

  public PendingDeposit.PendingDepositSchema getPendingDepositSchema() {
    return pendingDepositSchema;
  }

  public PendingPartialWithdrawal.PendingPartialWithdrawalSchema
      getPendingPartialWithdrawalSchema() {
    return pendingPartialWithdrawalSchema;
  }

  public PendingConsolidation.PendingConsolidationSchema getPendingConsolidationSchema() {
    return pendingConsolidationSchema;
  }

  public SszListSchema<PendingDeposit, ?> getPendingDepositsSchema() {
    return pendingDepositsSchema;
  }

  public SszListSchema<PendingPartialWithdrawal, ?> getPendingPartialWithdrawalsSchema() {
    return pendingPartialWithdrawalsSchema;
  }

  public SingleAttestationSchema getSingleAttestationSchema() {
    return singleAttestationSchema;
  }

  public SszListSchema<PendingConsolidation, ?> getPendingConsolidationsSchema() {
    return pendingConsolidationsSchema;
  }

  @Override
  public Optional<SchemaDefinitionsElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public LightClientBootstrapSchema getLightClientBootstrapSchema() {
    return lightClientBootstrapSchema;
  }

  public ExecutionProofSchema getExecutionProofSchema() {
    return executionProofSchema;
  }
}
