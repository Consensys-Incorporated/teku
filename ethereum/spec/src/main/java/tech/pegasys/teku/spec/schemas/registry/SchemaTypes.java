/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.schemas.registry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.MoreObjects;
import java.util.Locale;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntrySchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BlindedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContentsSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionListSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdateSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdateSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.PayloadTimelinessCommitteeWindowSchema;

public class SchemaTypes {
  // PHASE0
  public static final SchemaId<SszBitvectorSchema<SszBitvector>> ATTNETS_ENR_FIELD_SCHEMA =
      create("ATTNETS_ENR_FIELD_SCHEMA");
  public static final SchemaId<SszBitvectorSchema<SszBitvector>> SYNCNETS_ENR_FIELD_SCHEMA =
      create("SYNCNETS_ENR_FIELD_SCHEMA");
  public static final SchemaId<HistoricalBatchSchema> HISTORICAL_BATCH_SCHEMA =
      create("HISTORICAL_BATCH_SCHEMA");
  public static final SchemaId<BeaconBlocksByRootRequestMessageSchema>
      BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA =
          create("BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA");
  public static final SchemaId<AttesterSlashingSchema> ATTESTER_SLASHING_SCHEMA =
      create("ATTESTER_SLASHING_SCHEMA");
  public static final SchemaId<SszUInt64ListSchema<SszUInt64List>> ATTESTING_INDICES_SCHEMA =
      create("ATTESTING_INDICES_SCHEMA");
  public static final SchemaId<IndexedAttestationSchema> INDEXED_ATTESTATION_SCHEMA =
      create("INDEXED_ATTESTATION_SCHEMA");

  public static final SchemaId<AttestationSchema<Attestation>> ATTESTATION_SCHEMA =
      create("ATTESTATION_SCHEMA");

  public static final SchemaId<AggregateAndProofSchema> AGGREGATE_AND_PROOF_SCHEMA =
      create("AGGREGATE_AND_PROOF_SCHEMA");
  public static final SchemaId<SignedAggregateAndProofSchema> SIGNED_AGGREGATE_AND_PROOF_SCHEMA =
      create("SIGNED_AGGREGATE_AND_PROOF_SCHEMA");

  public static final SchemaId<BeaconBlockBodySchema<? extends BeaconBlockBody>>
      BEACON_BLOCK_BODY_SCHEMA = create("BEACON_BLOCK_BODY_SCHEMA");
  public static final SchemaId<BeaconBlockSchema> BEACON_BLOCK_SCHEMA =
      create("BEACON_BLOCK_SCHEMA");
  public static final SchemaId<SignedBeaconBlockSchema> SIGNED_BEACON_BLOCK_SCHEMA =
      create("SIGNED_BEACON_BLOCK_SCHEMA");

  public static final SchemaId<
          BeaconStateSchema<? extends BeaconState, ? extends MutableBeaconState>>
      BEACON_STATE_SCHEMA = create("BEACON_STATE_SCHEMA");
  public static final SchemaId<MetadataMessageSchema<?>> METADATA_MESSAGE_SCHEMA =
      create("METADATA_MESSAGE_SCHEMA");
  public static final SchemaId<StatusMessageSchema<?>> STATUS_MESSAGE_SCHEMA =
      create("STATUS_MESSAGE_SCHEMA");

  // Altair
  public static final SchemaId<LightClientHeaderSchema<? extends LightClientHeader>>
      LIGHT_CLIENT_HEADER_SCHEMA = create("LIGHT_CLIENT_HEADER_SCHEMA");
  public static final SchemaId<LightClientBootstrapSchema> LIGHT_CLIENT_BOOTSTRAP_SCHEMA =
      create("LIGHT_CLIENT_BOOTSTRAP_SCHEMA");
  public static final SchemaId<LightClientUpdateSchema> LIGHT_CLIENT_UPDATE_SCHEMA =
      create("LIGHT_CLIENT_UPDATE_SCHEMA");
  public static final SchemaId<LightClientFinalityUpdateSchema>
      LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA = create("LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA");
  public static final SchemaId<LightClientOptimisticUpdateSchema>
      LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA = create("LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA");

  // Bellatrix
  public static final SchemaId<ExecutionPayloadSchema<? extends ExecutionPayload>>
      EXECUTION_PAYLOAD_SCHEMA = create("EXECUTION_PAYLOAD_SCHEMA");
  public static final SchemaId<ExecutionPayloadHeaderSchema<? extends ExecutionPayloadHeader>>
      EXECUTION_PAYLOAD_HEADER_SCHEMA = create("EXECUTION_PAYLOAD_HEADER_SCHEMA");

  public static final SchemaId<BeaconBlockSchema> BLINDED_BEACON_BLOCK_SCHEMA =
      create("BLINDED_BEACON_BLOCK_SCHEMA");
  public static final SchemaId<
          BlindedBeaconBlockBodySchemaBellatrix<? extends BlindedBeaconBlockBodyBellatrix>>
      BLINDED_BEACON_BLOCK_BODY_SCHEMA = create("BLINDED_BEACON_BLOCK_BODY_SCHEMA");
  public static final SchemaId<SignedBeaconBlockSchema> SIGNED_BLINDED_BEACON_BLOCK_SCHEMA =
      create("SIGNED_BLINDED_BEACON_BLOCK_SCHEMA");

  public static final SchemaId<BuilderBidSchema<? extends BuilderBid>> BUILDER_BID_SCHEMA =
      create("BUILDER_BID_SCHEMA");
  public static final SchemaId<SignedBuilderBidSchema> SIGNED_BUILDER_BID_SCHEMA =
      create("SIGNED_BUILDER_BID_SCHEMA");
  public static final SchemaId<SszByteListSchema<Transaction>> TRANSACTION_SCHEMA =
      create("TRANSACTION_SCHEMA");
  public static final SchemaId<SszListSchema<Transaction, SszList<Transaction>>>
      TRANSACTIONS_SCHEMA = create("TRANSACTIONS_SCHEMA");

  // Capella
  public static final SchemaId<WithdrawalSchema> WITHDRAWAL_SCHEMA = create("WITHDRAWAL_SCHEMA");
  public static final SchemaId<BlsToExecutionChangeSchema> BLS_TO_EXECUTION_CHANGE_SCHEMA =
      create("BLS_TO_EXECUTION_CHANGE_SCHEMA");
  public static final SchemaId<SignedBlsToExecutionChangeSchema>
      SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA = create("SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA");
  public static final SchemaId<SszListSchema<HistoricalSummary, ?>> HISTORICAL_SUMMARIES_SCHEMA =
      create("HISTORICAL_SUMMARIES_SCHEMA");

  // Deneb
  public static final SchemaId<BlobKzgCommitmentsSchema> BLOB_KZG_COMMITMENTS_SCHEMA =
      create("BLOB_KZG_COMMITMENTS_SCHEMA");
  public static final SchemaId<BlobSchema> BLOB_SCHEMA = create("BLOB_SCHEMA");
  public static final SchemaId<SszListSchema<Blob, ? extends SszList<Blob>>> BLOBS_IN_BLOCK_SCHEMA =
      create("BLOBS_IN_BLOCK_SCHEMA");
  public static final SchemaId<BlobSidecarSchema> BLOB_SIDECAR_SCHEMA =
      create("BLOB_SIDECAR_SCHEMA");
  public static final SchemaId<BlobSidecarsByRootRequestMessageSchema>
      BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA =
          create("BLOB_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA");
  public static final SchemaId<BlockContentsSchema<?>> BLOCK_CONTENTS_SCHEMA =
      create("BLOCK_CONTENTS_SCHEMA");
  public static final SchemaId<SignedBlockContentsSchema<?>> SIGNED_BLOCK_CONTENTS_SCHEMA =
      create("SIGNED_BLOCK_CONTENTS_SCHEMA");
  public static final SchemaId<BlobsBundleSchema<?>> BLOBS_BUNDLE_SCHEMA =
      create("BLOBS_BUNDLE_SCHEMA");

  // Electra
  public static final SchemaId<SszBitlistSchema<SszBitlist>> AGGREGATION_BITS_SCHEMA =
      create("AGGREGATION_BITS_SCHEMA");
  public static final SchemaId<ExecutionRequestsSchema<? extends ExecutionRequests>>
      EXECUTION_REQUESTS_SCHEMA = create("EXECUTION_REQUESTS_SCHEMA");
  public static final SchemaId<SszListSchema<PendingPartialWithdrawal, ?>>
      PENDING_PARTIAL_WITHDRAWALS_SCHEMA = create("PENDING_PARTIAL_WITHDRAWALS_SCHEMA");
  public static final SchemaId<SszListSchema<PendingConsolidation, ?>>
      PENDING_CONSOLIDATIONS_SCHEMA = create("PENDING_CONSOLIDATIONS_SCHEMA");
  public static final SchemaId<SszListSchema<PendingDeposit, ?>> PENDING_DEPOSITS_SCHEMA =
      create("PENDING_DEPOSITS_SCHEMA");
  public static final SchemaId<ExecutionPayloadAndBlobsBundleSchema>
      EXECUTION_PAYLOAD_AND_BLOBS_BUNDLE_SCHEMA =
          create("EXECUTION_PAYLOAD_AND_BLOBS_BUNDLE_SCHEMA");
  public static final SchemaId<DepositRequestSchema> DEPOSIT_REQUEST_SCHEMA =
      create("DEPOSIT_REQUEST_SCHEMA");
  public static final SchemaId<SszListSchema<DepositRequest, ?>> DEPOSIT_REQUESTS_SCHEMA =
      create("DEPOSIT_REQUESTS_SCHEMA");
  public static final SchemaId<WithdrawalRequestSchema> WITHDRAWAL_REQUEST_SCHEMA =
      create("WITHDRAWAL_REQUEST_SCHEMA");
  public static final SchemaId<SszListSchema<WithdrawalRequest, ?>> WITHDRAWAL_REQUESTS_SCHEMA =
      create("WITHDRAWAL_REQUESTS_SCHEMA");
  public static final SchemaId<ConsolidationRequestSchema> CONSOLIDATION_REQUEST_SCHEMA =
      create("CONSOLIDATION_REQUEST_SCHEMA");
  public static final SchemaId<SszListSchema<ConsolidationRequest, ?>>
      CONSOLIDATION_REQUESTS_SCHEMA = create("CONSOLIDATION_REQUESTS_SCHEMA");
  public static final SchemaId<SingleAttestationSchema> SINGLE_ATTESTATION_SCHEMA =
      create("SINGLE_ATTESTATION_SCHEMA");
  // Move this when we decide which fork this schema should be under
  public static final SchemaId<ExecutionProofSchema> EXECUTION_PROOF_SCHEMA =
      create("EXECUTION_PROOF_SCHEMA");

  // Fulu
  public static final SchemaId<CellSchema> CELL_SCHEMA = create("CELL_SCHEMA");
  public static final SchemaId<DataColumnSchema> DATA_COLUMN_SCHEMA = create("DATA_COLUMN_SCHEMA");
  public static final SchemaId<DataColumnSidecarSchema<? extends DataColumnSidecar>>
      DATA_COLUMN_SIDECAR_SCHEMA = create("DATA_COLUMN_SIDECAR_SCHEMA");
  public static final SchemaId<DataColumnsByRootIdentifierSchema>
      DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA = create("DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA");
  public static final SchemaId<MatrixEntrySchema> MATRIX_ENTRY_SCHEMA =
      create("MATRIX_ENTRY_SCHEMA");
  public static final SchemaId<SszUInt64VectorSchema<?>> PROPOSER_LOOKAHEAD_SCHEMA =
      create("PROPOSER_LOOKAHEAD_SCHEMA");
  public static final SchemaId<DataColumnSidecarsByRootRequestMessageSchema>
      DATA_COLUMN_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA =
          create("DATA_COLUMN_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA");
  public static final SchemaId<
          DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema>
      DATA_COLUMN_SIDECARS_BY_RANGE_REQUEST_MESSAGE_SCHEMA =
          create("DATA_COLUMN_SIDECARS_BY_RANGE_REQUEST_MESSAGE_SCHEMA");

  // Gloas
  public static final SchemaId<BuilderPendingPaymentSchema> BUILDER_PENDING_PAYMENT_SCHEMA =
      create("BUILDER_PENDING_PAYMENT_SCHEMA");
  public static final SchemaId<BuilderPendingWithdrawalSchema> BUILDER_PENDING_WITHDRAWAL_SCHEMA =
      create("BUILDER_PENDING_WITHDRAWAL_SCHEMA");
  public static final SchemaId<PayloadAttestationDataSchema> PAYLOAD_ATTESTATION_DATA_SCHEMA =
      create("PAYLOAD_ATTESTATION_DATA_SCHEMA");
  public static final SchemaId<PayloadAttestationSchema> PAYLOAD_ATTESTATION_SCHEMA =
      create("PAYLOAD_ATTESTATION_SCHEMA");
  public static final SchemaId<PayloadAttestationMessageSchema> PAYLOAD_ATTESTATION_MESSAGE_SCHEMA =
      create("PAYLOAD_ATTESTATION_MESSAGE_SCHEMA");
  public static final SchemaId<IndexedPayloadAttestationSchema> INDEXED_PAYLOAD_ATTESTATION_SCHEMA =
      create("INDEXED_PAYLOAD_ATTESTATION_SCHEMA");
  public static final SchemaId<ExecutionPayloadBidSchema> EXECUTION_PAYLOAD_BID_SCHEMA =
      create("EXECUTION_PAYLOAD_BID_SCHEMA");
  public static final SchemaId<SignedExecutionPayloadBidSchema>
      SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA = create("SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA");
  public static final SchemaId<ProposerPreferencesSchema> PROPOSER_PREFERENCES_SCHEMA =
      create("PROPOSER_PREFERENCES_SCHEMA");
  public static final SchemaId<SignedProposerPreferencesSchema> SIGNED_PROPOSER_PREFERENCES_SCHEMA =
      create("SIGNED_PROPOSER_PREFERENCES_SCHEMA");
  public static final SchemaId<ExecutionPayloadEnvelopeSchema> EXECUTION_PAYLOAD_ENVELOPE_SCHEMA =
      create("EXECUTION_PAYLOAD_ENVELOPE_SCHEMA");
  public static final SchemaId<BlindedExecutionPayloadEnvelopeSchema>
      BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA =
          create("BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA");
  public static final SchemaId<SignedExecutionPayloadEnvelopeSchema>
      SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA = create("SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA");
  public static final SchemaId<SignedExecutionPayloadEnvelopeContentsSchema>
      SIGNED_EXECUTION_PAYLOAD_ENVELOPE_CONTENTS_SCHEMA =
          create("SIGNED_EXECUTION_PAYLOAD_ENVELOPE_CONTENTS_SCHEMA");
  public static final SchemaId<SignedBlindedExecutionPayloadEnvelopeSchema>
      SIGNED_BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA =
          create("SIGNED_BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA");
  public static final SchemaId<SszBitvectorSchema<?>> EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA =
      create("EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA");
  public static final SchemaId<SszVectorSchema<BuilderPendingPayment, ?>>
      BUILDER_PENDING_PAYMENTS_SCHEMA = create("BUILDER_PENDING_PAYMENTS_SCHEMA");
  public static final SchemaId<SszListSchema<BuilderPendingWithdrawal, ?>>
      BUILDER_PENDING_WITHDRAWALS_SCHEMA = create("BUILDER_PENDING_WITHDRAWALS_SCHEMA");
  public static final SchemaId<PayloadTimelinessCommitteeWindowSchema> PTC_WINDOW_SCHEMA =
      create("PTC_WINDOW_SCHEMA");
  public static final SchemaId<ExecutionPayloadEnvelopesByRootRequestMessageSchema>
      EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA =
          create("EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA");
  public static final SchemaId<BuilderDepositRequestSchema> BUILDER_DEPOSIT_REQUEST_SCHEMA =
      create("BUILDER_DEPOSIT_REQUEST_SCHEMA");
  public static final SchemaId<SszListSchema<BuilderDepositRequest, ?>>
      BUILDER_DEPOSIT_REQUESTS_SCHEMA = create("BUILDER_DEPOSIT_REQUESTS_SCHEMA");
  public static final SchemaId<BuilderExitRequestSchema> BUILDER_EXIT_REQUEST_SCHEMA =
      create("BUILDER_EXIT_REQUEST_SCHEMA");
  public static final SchemaId<SszListSchema<BuilderExitRequest, ?>> BUILDER_EXIT_REQUESTS_SCHEMA =
      create("BUILDER_EXIT_REQUESTS_SCHEMA");
  public static final SchemaId<SszByteListSchema<SszByteList>> BLOCK_ACCESS_LIST_SCHEMA =
      create("BLOCK_ACCESS_LIST_SCHEMA");

  // Heze
  public static final SchemaId<InclusionListSchema> INCLUSION_LIST_SCHEMA =
      create("INCLUSION_LIST_SCHEMA");
  public static final SchemaId<SignedInclusionListSchema> SIGNED_INCLUSION_LIST_SCHEMA =
      create("SIGNED_INCLUSION_LIST_SCHEMA");

  private SchemaTypes() {
    // Prevent instantiation
  }

  @VisibleForTesting
  static <T> SchemaId<T> create(final String name) {
    return new SchemaId<>(name);
  }

  public static class SchemaId<T> {
    private static final Converter<String, String> UPPER_UNDERSCORE_TO_UPPER_CAMEL =
        CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL);

    public static String upperSnakeCaseToUpperCamel(final String camelCase) {
      return UPPER_UNDERSCORE_TO_UPPER_CAMEL.convert(camelCase);
    }

    private static String capitalizeMilestone(final SpecMilestone milestone) {
      return milestone.name().charAt(0) + milestone.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private final String name;

    private SchemaId(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public String getSchemaName(final SpecMilestone milestone) {
      return getSchemaName() + capitalizeMilestone(milestone);
    }

    public String getSchemaName() {
      return upperSnakeCaseToUpperCamel(name.replace("_SCHEMA", ""));
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof SchemaId<?> other) {
        return name.equals(other.name);
      }
      return false;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).toString();
    }
  }
}
