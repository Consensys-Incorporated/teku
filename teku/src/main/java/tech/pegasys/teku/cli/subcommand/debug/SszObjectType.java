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

package tech.pegasys.teku.cli.subcommand.debug;

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader.BeaconBlockHeaderSchema;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data.Eth1DataSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeaderSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData.AttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.operations.Deposit.DepositSchema;
import tech.pegasys.teku.spec.datastructures.operations.DepositData.DepositDataSchema;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage.DepositMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing.ProposerSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit.SignedVoluntaryExitSchema;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit.VoluntaryExitSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionDataSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint.CheckpointSchema;
import tech.pegasys.teku.spec.datastructures.state.Fork.ForkSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkData.ForkDataSchema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation.PendingAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.SigningData.SigningDataSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.Validator.ValidatorSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.api.StateValidatorBalanceData;

@SuppressWarnings("JavaCase")
public enum SszObjectType {
  AggregateAndProof(schemas(SchemaDefinitions::getAggregateAndProofSchema)),
  SignedBeaconBlock(schemas(SchemaDefinitions::getSignedBeaconBlockSchema)),
  SignedBlindedBeaconBlock(schemas(SchemaDefinitions::getSignedBlindedBeaconBlockSchema)),
  ProposerSlashing(new ProposerSlashingSchema()),
  ForkData(new ForkDataSchema()),
  Checkpoint(new CheckpointSchema()),
  SigningData(new SigningDataSchema()),
  BeaconBlock(schemas(SchemaDefinitions::getBeaconBlockSchema)),
  BlindedBeaconBlock(schemas(SchemaDefinitions::getBlindedBeaconBlockSchema)),
  AttesterSlashing(schemas(SchemaDefinitions::getAttesterSlashingSchema)),
  DepositMessage(new DepositMessageSchema()),
  SignedBeaconBlockHeader(new SignedBeaconBlockHeaderSchema()),
  BeaconBlockBody(schemas(SchemaDefinitions::getBeaconBlockBodySchema)),
  BlindedBeaconBlockBody(schemas(SchemaDefinitions::getBlindedBeaconBlockBodySchema)),
  DepositData(new DepositDataSchema()),
  VoluntaryExit(new VoluntaryExitSchema()),
  Eth1Data(new Eth1DataSchema()),
  Fork(new ForkSchema()),
  IndexedAttestation(schemas(SchemaDefinitions::getIndexedAttestationSchema)),
  PendingAttestation(config(PendingAttestationSchema::new)),
  BeaconBlockHeader(new BeaconBlockHeaderSchema()),
  Deposit(new DepositSchema()),
  AttestationData(new AttestationDataSchema()),
  BeaconState(schemas(SchemaDefinitions::getBeaconStateSchema)),
  SingleAttestation(electraSchemas(SchemaDefinitionsElectra::getSingleAttestationSchema)),
  Attestation(schemas(SchemaDefinitions::getAttestationSchema)),
  SignedVoluntaryExit(new SignedVoluntaryExitSchema()),
  SyncCommitteeMessage(altairSchemas(SchemaDefinitionsAltair::getSyncCommitteeMessageSchema)),
  SyncCommitteeContribution(
      altairSchemas(SchemaDefinitionsAltair::getSyncCommitteeContributionSchema)),
  ContributionAndProof(altairSchemas(SchemaDefinitionsAltair::getContributionAndProofSchema)),
  SignedContributionAndProof(
      altairSchemas(SchemaDefinitionsAltair::getSignedContributionAndProofSchema)),
  SyncAggregatorSelectionData(SyncAggregatorSelectionDataSchema.INSTANCE),
  SyncCommittee(altairConfig(SyncCommitteeSchema::new)),
  SyncAggregate(altairConfig(c -> SyncAggregateSchema.create(c.getSyncCommitteeSize()))),
  SignedAggregateAndProof(schemas(SchemaDefinitions::getSignedAggregateAndProofSchema)),
  Validator(new ValidatorSchema()),
  HistoricalBatch(config(c -> new HistoricalBatchSchema(c.getSlotsPerHistoricalRoot()))),
  ExecutionPayload(bellatrixSchemas(SchemaDefinitionsBellatrix::getExecutionPayloadSchema)),
  ExecutionPayloadHeader(
      bellatrixSchemas(SchemaDefinitionsBellatrix::getExecutionPayloadHeaderSchema)),
  BuilderBid(bellatrixSchemas(SchemaDefinitionsBellatrix::getBuilderBidSchema)),
  Withdrawal(capellaSchemas(SchemaDefinitionsCapella::getWithdrawalSchema)),
  BlsToExecutionChange(capellaSchemas(SchemaDefinitionsCapella::getBlsToExecutionChangeSchema)),
  SignedBlsToExecutionChange(
      capellaSchemas(SchemaDefinitionsCapella::getSignedBlsToExecutionChangeSchema)),
  BlobSidecar(denebSchemas(SchemaDefinitionsDeneb::getBlobSidecarSchema)),
  BlobsBundle(denebSchemas(SchemaDefinitionsDeneb::getBlobsBundleSchema)),
  ExecutionPayloadAndBlobsBundle(
      denebSchemas(SchemaDefinitionsDeneb::getExecutionPayloadAndBlobsBundleSchema)),
  DataColumn(fuluSchemas(SchemaDefinitionsFulu::getDataColumnSchema)),
  DataColumnSidecar(fuluSchemas(SchemaDefinitionsFulu::getDataColumnSidecarSchema)),
  StateValidatorBalances(StateValidatorBalanceData.SSZ_LIST_SCHEMA);

  private final Function<SpecVersion, SszSchema<?>> getSchema;

  SszObjectType(final SszSchema<?> schema) {
    this(__ -> schema);
  }

  SszObjectType(final Function<SpecVersion, SszSchema<?>> getSchema) {
    this.getSchema = getSchema;
  }

  public SszSchema<?> getSchema(final SpecVersion spec) {
    return getSchema.apply(spec);
  }

  private static Function<SpecVersion, SszSchema<?>> schemas(
      final Function<SchemaDefinitions, SszSchema<?>> getter) {
    return spec -> getter.apply(spec.getSchemaDefinitions());
  }

  private static Function<SpecVersion, SszSchema<?>> altairSchemas(
      final Function<SchemaDefinitionsAltair, SszSchema<?>> getter) {
    return spec -> getter.apply(SchemaDefinitionsAltair.required(spec.getSchemaDefinitions()));
  }

  private static Function<SpecVersion, SszSchema<?>> bellatrixSchemas(
      final Function<SchemaDefinitionsBellatrix, SszSchema<?>> getter) {
    return spec -> getter.apply(SchemaDefinitionsBellatrix.required(spec.getSchemaDefinitions()));
  }

  private static Function<SpecVersion, SszSchema<?>> capellaSchemas(
      final Function<SchemaDefinitionsCapella, SszSchema<?>> getter) {
    return spec -> getter.apply(SchemaDefinitionsCapella.required(spec.getSchemaDefinitions()));
  }

  private static Function<SpecVersion, SszSchema<?>> denebSchemas(
      final Function<SchemaDefinitionsDeneb, SszSchema<?>> getter) {
    return spec -> getter.apply(SchemaDefinitionsDeneb.required(spec.getSchemaDefinitions()));
  }

  private static Function<SpecVersion, SszSchema<?>> electraSchemas(
      final Function<SchemaDefinitionsElectra, SszSchema<?>> getter) {
    return spec -> getter.apply(SchemaDefinitionsElectra.required(spec.getSchemaDefinitions()));
  }

  private static Function<SpecVersion, SszSchema<?>> fuluSchemas(
      final Function<SchemaDefinitionsFulu, SszSchema<?>> getter) {
    return spec -> getter.apply(SchemaDefinitionsFulu.required(spec.getSchemaDefinitions()));
  }

  private static Function<SpecVersion, SszSchema<?>> config(
      final Function<SpecConfig, SszSchema<?>> getter) {
    return spec -> getter.apply(spec.getConfig());
  }

  private static Function<SpecVersion, SszSchema<?>> altairConfig(
      final Function<SpecConfigAltair, SszSchema<?>> getter) {
    return spec -> getter.apply(SpecConfigAltair.required(spec.getConfig()));
  }
}
