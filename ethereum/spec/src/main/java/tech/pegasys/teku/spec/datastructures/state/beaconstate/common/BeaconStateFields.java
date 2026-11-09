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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.SLOT_FIELD;

import java.util.List;
import java.util.Locale;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public enum BeaconStateFields implements SszFieldName {
  GENESIS_TIME,
  GENESIS_VALIDATORS_ROOT,
  SLOT,
  FORK,
  LATEST_BLOCK_HEADER,
  BLOCK_ROOTS,
  STATE_ROOTS,
  HISTORICAL_ROOTS,
  ETH1_DATA,
  ETH1_DATA_VOTES,
  ETH1_DEPOSIT_INDEX,
  VALIDATORS,
  BALANCES,
  RANDAO_MIXES,
  SLASHINGS,
  PREVIOUS_EPOCH_ATTESTATIONS,
  CURRENT_EPOCH_ATTESTATIONS,
  JUSTIFICATION_BITS,
  PREVIOUS_JUSTIFIED_CHECKPOINT,
  CURRENT_JUSTIFIED_CHECKPOINT,
  FINALIZED_CHECKPOINT,
  // Altair fields
  PREVIOUS_EPOCH_PARTICIPATION,
  CURRENT_EPOCH_PARTICIPATION,
  INACTIVITY_SCORES,
  CURRENT_SYNC_COMMITTEE,
  NEXT_SYNC_COMMITTEE,
  // Bellatrix fields
  LATEST_EXECUTION_PAYLOAD_HEADER, // removed in Gloas
  // Capella fields
  NEXT_WITHDRAWAL_INDEX,
  NEXT_WITHDRAWAL_VALIDATOR_INDEX,
  HISTORICAL_SUMMARIES,
  // Electra fields
  DEPOSIT_REQUESTS_START_INDEX,
  DEPOSIT_BALANCE_TO_CONSUME,
  EXIT_BALANCE_TO_CONSUME,
  EARLIEST_EXIT_EPOCH,
  CONSOLIDATION_BALANCE_TO_CONSUME,
  EARLIEST_CONSOLIDATION_EPOCH,
  PENDING_DEPOSITS,
  PENDING_PARTIAL_WITHDRAWALS,
  PENDING_CONSOLIDATIONS,
  // Fulu fields
  PROPOSER_LOOKAHEAD,
  // Gloas fields
  LATEST_EXECUTION_PAYLOAD_BID,
  BUILDERS,
  NEXT_WITHDRAWAL_BUILDER_INDEX,
  EXECUTION_PAYLOAD_AVAILABILITY,
  BUILDER_PENDING_PAYMENTS,
  BUILDER_PENDING_WITHDRAWALS,
  LATEST_BLOCK_HASH,
  PAYLOAD_EXPECTED_WITHDRAWALS,
  PTC_WINDOW;

  private final String sszFieldName;

  BeaconStateFields() {
    this.sszFieldName = name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String getSszFieldName() {
    return sszFieldName;
  }

  public static void copyCommonFieldsFromSource(
      final MutableBeaconState state, final BeaconState source) {
    // Version
    state.setGenesisTime(source.getGenesisTime());
    state.setGenesisValidatorsRoot(source.getGenesisValidatorsRoot());
    state.setSlot(source.getSlot());
    state.setFork(source.getFork());
    // History
    state.setLatestBlockHeader(source.getLatestBlockHeader());
    state.setBlockRoots(source.getBlockRoots());
    state.setStateRoots(source.getStateRoots());
    state.setHistoricalRoots(source.getHistoricalRoots());
    // Eth1
    state.setEth1Data(source.getEth1Data());
    state.setEth1DataVotes(source.getEth1DataVotes());
    state.setEth1DepositIndex(source.getEth1DepositIndex());
    // Registry
    state.setValidators(source.getValidators());
    state.setBalances(source.getBalances());
    // Randomness
    state.setRandaoMixes(source.getRandaoMixes());
    // Slashings
    state.setSlashings(source.getSlashings());
    // Finality
    state.setJustificationBits(source.getJustificationBits());
    state.setPreviousJustifiedCheckpoint(source.getPreviousJustifiedCheckpoint());
    state.setCurrentJustifiedCheckpoint(source.getCurrentJustifiedCheckpoint());
    state.setFinalizedCheckpoint(source.getFinalizedCheckpoint());
  }

  /**
   * Like {@link #copyCommonFieldsFromSource} but rebuilds the common list fields through the target
   * state schema. Required when upgrading into a milestone (Gloas+) whose common list fields are
   * progressive, since the source's bounded list backing nodes are structurally incompatible with
   * the progressive target fields.
   */
  public static void copyCommonFieldsFromSourceUsingTargetSchemas(
      final MutableBeaconState state, final BeaconState source) {
    final BeaconStateSchema<?, ?> targetSchema = state.getBeaconStateSchema();
    // Version
    state.setGenesisTime(source.getGenesisTime());
    state.setGenesisValidatorsRoot(source.getGenesisValidatorsRoot());
    state.setSlot(source.getSlot());
    state.setFork(source.getFork());
    // History
    state.setLatestBlockHeader(source.getLatestBlockHeader());
    state.setBlockRoots(source.getBlockRoots());
    state.setStateRoots(source.getStateRoots());
    // historical_roots stays a bounded primitive (Bytes32) list across the upgrade
    state.setHistoricalRoots(source.getHistoricalRoots());
    // Eth1
    state.setEth1Data(source.getEth1Data());
    // eth1_data_votes schema is unchanged across the upgrade (bounded list), so copy directly.
    state.setEth1DataVotes(source.getEth1DataVotes());
    state.setEth1DepositIndex(source.getEth1DepositIndex());
    // Registry
    state.setValidators(
        BeaconStateListFieldMigration.rematerialize(
            targetSchema, VALIDATORS, source.getValidators()));
    state.setBalances(
        BeaconStateListFieldMigration.rematerializeUInt64(
            targetSchema, BALANCES, source.getBalances()));
    // Randomness
    state.setRandaoMixes(source.getRandaoMixes());
    // Slashings
    state.setSlashings(source.getSlashings());
    // Finality
    state.setJustificationBits(source.getJustificationBits());
    state.setPreviousJustifiedCheckpoint(source.getPreviousJustifiedCheckpoint());
    state.setCurrentJustifiedCheckpoint(source.getCurrentJustifiedCheckpoint());
    state.setFinalizedCheckpoint(source.getFinalizedCheckpoint());
  }

  /**
   * Returns the schema to use for a BeaconState list field: a progressive (EIP-7916) list for Gloas
   * and later, or the supplied bounded list schema for earlier milestones.
   */
  public static SszSchema<?> listSchemaForState(
      final SpecConfig specConfig, final SszListSchema<?, ?> boundedListSchema) {
    if (!specConfig.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      return boundedListSchema;
    }
    return toProgressiveListSchema(boundedListSchema);
  }

  /** Returns the progressive equivalent of a bounded list schema, preserving the element type. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static SszSchema<?> toProgressiveListSchema(final SszListSchema<?, ?> boundedListSchema) {
    if (boundedListSchema.getElementSchema() instanceof SszPrimitiveSchema primitiveSchema) {
      return SszPrimitiveListSchema.createProgressive(primitiveSchema, getHints(boundedListSchema));
    }
    return SszProgressiveListSchema.create(
        (SszSchema) boundedListSchema.getElementSchema(), getHints(boundedListSchema));
  }

  private static SszSchemaHints getHints(final SszListSchema<?, ?> schema) {
    return schema instanceof AbstractSszCollectionSchema<?, ?> collectionSchema
        ? collectionSchema.getHints()
        : SszSchemaHints.none();
  }

  static List<SszField> getCommonFields(final SpecConfig specConfig) {
    SszField forkField = new SszField(3, BeaconStateFields.FORK, Fork.SSZ_SCHEMA);
    final BeaconBlockHeader.BeaconBlockHeaderSchema blockHeaderSchema =
        BeaconBlockHeader.SSZ_SCHEMA;
    SszField latestBlockHeaderField =
        new SszField(4, BeaconStateFields.LATEST_BLOCK_HEADER, blockHeaderSchema);
    SszField blockRootsField =
        new SszField(
            5,
            BeaconStateFields.BLOCK_ROOTS,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getSlotsPerHistoricalRoot()));
    SszField stateRootsField =
        new SszField(
            6,
            BeaconStateFields.STATE_ROOTS,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getSlotsPerHistoricalRoot()));
    SszField historicalRootsField =
        new SszField(
            7,
            BeaconStateFields.HISTORICAL_ROOTS,
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getHistoricalRootsLimit()));
    SszField eth1DataField = new SszField(8, BeaconStateFields.ETH1_DATA, Eth1Data.SSZ_SCHEMA);
    // eth1_data_votes stays a bounded list in all milestones (not converted to a progressive list
    // by EIP-7688): List[Eth1Data, EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH].
    SszField eth1DataVotesField =
        new SszField(
            9,
            BeaconStateFields.ETH1_DATA_VOTES,
            () ->
                SszListSchema.create(
                    Eth1Data.SSZ_SCHEMA,
                    (long) specConfig.getEpochsPerEth1VotingPeriod()
                        * specConfig.getSlotsPerEpoch()));
    SszField eth1DepositIndexField =
        new SszField(10, BeaconStateFields.ETH1_DEPOSIT_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA);
    SszField validatorsField =
        new SszField(
            11,
            BeaconStateFields.VALIDATORS,
            () ->
                listSchemaForState(
                    specConfig,
                    SszListSchema.create(
                        Validator.SSZ_SCHEMA,
                        specConfig.getValidatorRegistryLimit(),
                        SszSchemaHints.sszSuperNode(8))));
    SszField balancesField =
        new SszField(
            12,
            BeaconStateFields.BALANCES,
            () ->
                listSchemaForState(
                    specConfig,
                    SszListSchema.create(
                        SszPrimitiveSchemas.UINT64_SCHEMA,
                        specConfig.getValidatorRegistryLimit())));
    SszField randaoMixesField =
        new SszField(
            13,
            BeaconStateFields.RANDAO_MIXES,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getEpochsPerHistoricalVector()));
    SszField slashingsField =
        new SszField(
            14,
            BeaconStateFields.SLASHINGS,
            () ->
                SszVectorSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, specConfig.getEpochsPerSlashingsVector()));
    SszField justificationBitsField =
        new SszField(
            17,
            BeaconStateFields.JUSTIFICATION_BITS,
            () -> SszBitvectorSchema.create(specConfig.getJustificationBitsLength()));
    SszField previousJustifiedCheckpointField =
        new SszField(18, BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT, Checkpoint.SSZ_SCHEMA);
    SszField currentJustifiedCheckpointField =
        new SszField(19, BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT, Checkpoint.SSZ_SCHEMA);
    SszField finalizedCheckpointField =
        new SszField(20, BeaconStateFields.FINALIZED_CHECKPOINT, Checkpoint.SSZ_SCHEMA);

    return List.of(
        GENESIS_TIME_FIELD,
        GENESIS_VALIDATORS_ROOT_FIELD,
        SLOT_FIELD,
        forkField,
        latestBlockHeaderField,
        blockRootsField,
        stateRootsField,
        historicalRootsField,
        eth1DataField,
        eth1DataVotesField,
        eth1DepositIndexField,
        validatorsField,
        balancesField,
        randaoMixesField,
        slashingsField,
        justificationBitsField,
        previousJustifiedCheckpointField,
        currentJustifiedCheckpointField,
        finalizedCheckpointField);
  }

  @Override
  public String toString() {
    return getSszFieldName();
  }
}
