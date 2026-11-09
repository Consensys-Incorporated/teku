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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_SUMMARIES_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyBuilderCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary.HistoricalSummarySchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsCapella extends SchemaDefinitionsBellatrix {
  private final WithdrawalSchema withdrawalSchema;

  private final BlsToExecutionChangeSchema blsToExecutionChangeSchema;
  private final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema;

  private final SszListSchema<HistoricalSummary, ?> historicalSummariesSchema;

  public SchemaDefinitionsCapella(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.historicalSummariesSchema = schemaRegistry.get(HISTORICAL_SUMMARIES_SCHEMA);
    this.blsToExecutionChangeSchema = schemaRegistry.get(BLS_TO_EXECUTION_CHANGE_SCHEMA);
    this.signedBlsToExecutionChangeSchema =
        schemaRegistry.get(SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA);
    this.withdrawalSchema = schemaRegistry.get(WITHDRAWAL_SCHEMA);
  }

  public static SchemaDefinitionsCapella required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsCapella,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsCapella.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsCapella) schemaDefinitions;
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderCapella(
        getBeaconBlockBodySchema().toVersionCapella().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionCapella().orElseThrow());
  }

  public WithdrawalSchema getWithdrawalSchema() {
    return withdrawalSchema;
  }

  public BlsToExecutionChangeSchema getBlsToExecutionChangeSchema() {
    return blsToExecutionChangeSchema;
  }

  public SignedBlsToExecutionChangeSchema getSignedBlsToExecutionChangeSchema() {
    return signedBlsToExecutionChangeSchema;
  }

  public HistoricalSummarySchema getHistoricalSummarySchema() {
    return (HistoricalSummarySchema) historicalSummariesSchema.getElementSchema();
  }

  @Override
  public Optional<SchemaDefinitionsCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
