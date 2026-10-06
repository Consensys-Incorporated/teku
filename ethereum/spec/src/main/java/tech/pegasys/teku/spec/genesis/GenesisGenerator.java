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

package tech.pegasys.teku.spec.genesis;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GenesisGenerator {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecVersion genesisSpec;
  private final SpecConfig specConfig;

  private final MutableBeaconState state;
  private final Object2IntMap<BLSPublicKey> keyCache = new Object2IntOpenHashMap<>();
  private final SszMutableList<DepositData> depositDataList;

  private int activeValidatorCount = 0;

  public GenesisGenerator(final SpecVersion genesisSpec, final Fork genesisFork) {
    this.genesisSpec = genesisSpec;
    this.specConfig = genesisSpec.getConfig();
    final SchemaDefinitions schemaDefinitions = genesisSpec.getSchemaDefinitions();

    state = schemaDefinitions.getBeaconStateSchema().createBuilder();
    // Not createGenesisBody(state): the state is still empty here, so a Gloas body would embed a
    // default execution payload bid. The header is recomputed in updateCandidateState once the
    // bid is known.
    state.setLatestBlockHeader(
        createGenesisBlockHeader(schemaDefinitions.getBeaconBlockBodySchema().createEmpty()));
    state.setFork(genesisFork);

    depositDataList =
        SszListSchema.create(DepositData.SSZ_SCHEMA, 1L << specConfig.getDepositContractTreeDepth())
            .getDefault()
            .createWritableCopy();
  }

  public void updateExecutionPayloadHeader(final ExecutionPayloadHeader payloadHeader) {
    state
        .toMutableVersionBellatrix()
        .ifPresent(stateBellatrix -> stateBellatrix.setLatestExecutionPayloadHeader(payloadHeader));
  }

  public void updateCandidateState(
      final Bytes32 eth1BlockHash, final UInt64 eth1Timestamp, final List<Deposit> deposits) {
    updateGenesisTime(eth1Timestamp);

    state.setEth1Data(
        new Eth1Data(
            Bytes32.ZERO, UInt64.valueOf(depositDataList.size() + deposits.size()), eth1BlockHash));
    if (genesisSpec.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      MutableBeaconStateElectra.required(state)
          .setDepositRequestsStartIndex(UNSET_DEPOSIT_REQUESTS_START_INDEX);
    }

    // Process deposits
    deposits.forEach(
        deposit -> {
          LOG.trace("About to process deposit: {}", depositDataList.size());
          depositDataList.append(deposit.getData());

          // Skip verifying the merkle proof as these deposits come directly from an Eth1 event.
          // We do still verify the signature
          genesisSpec
              .getBlockProcessor()
              .processDepositWithoutCheckingMerkleProof(
                  state, deposit, Optional.of(keyCache), false);

          if (!genesisSpec.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
            processActivation(deposit);
          }
        });

    // Process deposit balance updates
    if (genesisSpec.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      final SchemaDefinitionsElectra schemaDefinitionsElectra =
          SchemaDefinitionsElectra.required(genesisSpec.getSchemaDefinitions());
      final BeaconStateMutatorsElectra mutatorsElectra =
          new BeaconStateMutatorsElectra(
              specConfig,
              genesisSpec.miscHelpers(),
              genesisSpec.beaconStateAccessors(),
              schemaDefinitionsElectra);
      BeaconStateElectra.required(state)
          .getPendingDeposits()
          .forEach(
              pendingDeposit ->
                  mutatorsElectra.increaseBalance(
                      state,
                      keyCache.getInt(pendingDeposit.getPublicKey()),
                      pendingDeposit.getAmount()));
      MutableBeaconStateElectra.required(state)
          .setPendingDeposits(
              schemaDefinitionsElectra.getPendingDepositsSchema().createFromElements(List.of()));

      // Process activations
      keyCache.values().intStream().forEach(this::processActivation);
    }

    // Process proposer lookahead
    if (genesisSpec.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {

      final SchemaDefinitionsFulu schemaDefinitionsFulu =
          SchemaDefinitionsFulu.required(genesisSpec.getSchemaDefinitions());

      final BeaconStateAccessorsFulu accessorsFulu =
          BeaconStateAccessorsFulu.required(genesisSpec.beaconStateAccessors());

      final MiscHelpersFulu helpersFulu = MiscHelpersFulu.required(genesisSpec.miscHelpers());

      final List<UInt64> proposerLookahead =
          helpersFulu.initializeProposerLookahead(
              BeaconStateElectra.required(state), accessorsFulu);

      MutableBeaconStateFulu.required(state)
          .setProposerLookahead(
              schemaDefinitionsFulu.getProposerLookaheadSchema().of(proposerLookahead));
    }

    // Process Gloas
    if (genesisSpec.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {

      final SchemaDefinitionsGloas schemaDefinitionsGloas =
          SchemaDefinitionsGloas.required(genesisSpec.getSchemaDefinitions());

      final BeaconStateAccessorsGloas accessorsGloas =
          BeaconStateAccessorsGloas.required(genesisSpec.beaconStateAccessors());

      final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);

      stateGloas.setLatestBlockHash(eth1BlockHash);
      final Bytes32 parentBlockHash = eth1BlockHash;

      stateGloas.setLatestExecutionPayloadBid(
          schemaDefinitionsGloas
              .getExecutionPayloadBidSchema()
              .create(
                  parentBlockHash,
                  Bytes32.ZERO,
                  parentBlockHash,
                  Bytes32.ZERO,
                  Bytes20.ZERO,
                  UInt64.ZERO,
                  UInt64.ZERO,
                  UInt64.ZERO,
                  UInt64.ZERO,
                  UInt64.ZERO,
                  schemaDefinitionsGloas.getBlobKzgCommitmentsSchema().of(),
                  schemaDefinitionsGloas.getExecutionRequestsSchema().getDefault().hashTreeRoot()));
      // The Gloas genesis block body embeds the latest execution payload bid, so the latest block
      // header must be recomputed now that the bid is set
      state.setLatestBlockHeader(
          createGenesisBlockHeader(
              schemaDefinitionsGloas.getBeaconBlockBodySchema().createGenesisBody(state)));
      stateGloas.setBuilders(
          BeaconStateSchemaGloas.required(state.getBeaconStateSchema()).getBuildersSchema().of());
      stateGloas.setNextWithdrawalBuilderIndex(UInt64.ZERO);
      final SszBitvector executionPayloadAvailability =
          schemaDefinitionsGloas
              .getExecutionPayloadAvailabilitySchema()
              .ofBits(IntStream.range(0, specConfig.getSlotsPerHistoricalRoot()).toArray());
      stateGloas.setExecutionPayloadAvailability(executionPayloadAvailability);
      final List<BuilderPendingPayment> builderPendingPayments =
          Collections.nCopies(
              2 * specConfig.getSlotsPerEpoch(),
              schemaDefinitionsGloas.getBuilderPendingPaymentSchema().getDefault());
      stateGloas.setBuilderPendingPayments(
          schemaDefinitionsGloas
              .getBuilderPendingPaymentsSchema()
              .createFromElements(builderPendingPayments));
      stateGloas.setBuilderPendingWithdrawals(
          schemaDefinitionsGloas.getBuilderPendingWithdrawalsSchema().of());
      stateGloas.setPayloadExpectedWithdrawals(
          schemaDefinitionsGloas.getExecutionPayloadSchema().getWithdrawalsSchemaRequired().of());
      stateGloas.setPtcWindow(accessorsGloas.initializePtcWindow(state));
    }
  }

  private void processActivation(final Deposit deposit) {
    processActivation(keyCache.getOrDefault(deposit.getData().getPubkey(), -1));
  }

  private void processActivation(final int index) {
    if (index == -1) {
      // Could be absent if the deposit was invalid
      return;
    }
    final Validator validator = state.getValidators().get(index);
    if (validator.getActivationEpoch().equals(GENESIS_EPOCH)) {
      // Validator is already activated (and thus already has the max effective balance)
      return;
    }

    final UInt64 balance = state.getBalances().getElement(index);
    final UInt64 effectiveBalance =
        balance
            .minus(balance.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(specConfig.getMaxEffectiveBalance());

    final UInt64 activationEligibilityEpoch;
    final UInt64 activationEpoch;

    if (effectiveBalance.equals(specConfig.getMaxEffectiveBalance())) {
      activationEligibilityEpoch = GENESIS_EPOCH;
      activationEpoch = GENESIS_EPOCH;
      activeValidatorCount++;
    } else {
      activationEligibilityEpoch = validator.getActivationEligibilityEpoch();
      activationEpoch = validator.getActivationEpoch();
    }

    final Validator modifiedValidator =
        new Validator(
            validator.getPubkeyBytes(),
            validator.getWithdrawalCredentials(),
            effectiveBalance,
            validator.isSlashed(),
            activationEligibilityEpoch,
            activationEpoch,
            validator.getExitEpoch(),
            validator.getWithdrawableEpoch());

    state.getValidators().set(index, modifiedValidator);
  }

  private static BeaconBlockHeader createGenesisBlockHeader(
      final BeaconBlockBody genesisBlockBody) {
    return new BeaconBlockHeader(
        SpecConfig.GENESIS_SLOT, ZERO, Bytes32.ZERO, Bytes32.ZERO, genesisBlockBody.hashTreeRoot());
  }

  public int getActiveValidatorCount() {
    return activeValidatorCount;
  }

  public UInt64 getGenesisTime() {
    return state.getGenesisTime();
  }

  public BeaconState getGenesisState() {
    finalizeState();
    return state.commitChanges();
  }

  public long getDepositCount() {
    return depositDataList.size();
  }

  private void finalizeState() {
    calculateRandaoMixes();
    calculateDepositRoot();
    final BeaconState readOnlyState = state.commitChanges();
    state.setGenesisValidatorsRoot(readOnlyState.getValidators().hashTreeRoot());

    genesisSpec
        .getSyncCommitteeUtil()
        .ifPresent(syncCommitteeUtil -> syncCommitteeUtil.setGenesisStateSyncCommittees(state));
  }

  private void calculateRandaoMixes() {
    for (int i = 0; i < state.getRandaoMixes().size(); i++) {
      state.getRandaoMixes().setElement(i, state.getEth1Data().getBlockHash());
    }
  }

  private void calculateDepositRoot() {
    final Eth1Data eth1Data = state.getEth1Data();
    state.setEth1Data(
        new Eth1Data(
            depositDataList.hashTreeRoot(), eth1Data.getDepositCount(), eth1Data.getBlockHash()));
  }

  private void updateGenesisTime(final UInt64 eth1Timestamp) {
    final UInt64 genesisTime = eth1Timestamp.plus(specConfig.getGenesisDelay());
    state.setGenesisTime(genesisTime);
  }
}
