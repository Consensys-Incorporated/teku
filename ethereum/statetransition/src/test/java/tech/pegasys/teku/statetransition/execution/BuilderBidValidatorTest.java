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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.BUILDER_ENTRY_SCHEMA;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BuilderBidValidatorTest {

  private static final UInt64 FINALIZED_EPOCH = UInt64.valueOf(5);
  private static final UInt64 BUILDER_INDEX = UInt64.ZERO;
  private static final Bytes32 DEPENDENT_ROOT = Bytes32.fromHexString("0x1234");

  // NOOP verifier so random signatures pass — lets tests focus on the other validation rules
  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          config -> config.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final BuilderBidValidator validator =
      new BuilderBidValidator(
          spec, proposerPreferencesManager, recentChainData, gossipValidationHelper);

  private BeaconStateGloas state;
  private Bytes32 validParentBlockHash;
  private Bytes32 validParentBlockRoot;
  private Bytes32 validPrevRandao;
  private UInt64 validGasLimit;
  private Eth1Address validFeeRecipient;

  @BeforeEach
  void setUp() {
    state =
        createStateWithActiveBuilder(spec.getGenesisSpec().getConfig().getMaxEffectiveBalance());

    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);
    final BeaconStateAccessorsGloas beaconStateAccessors =
        BeaconStateAccessorsGloas.required(spec.atSlot(state.getSlot()).beaconStateAccessors());

    validParentBlockHash = stateGloas.getLatestExecutionPayloadBid().getBlockHash();
    validParentBlockRoot = state.getLatestBlockHeader().hashTreeRoot();
    validPrevRandao =
        beaconStateAccessors.getRandaoMix(state, beaconStateAccessors.getCurrentEpoch(state));
    validGasLimit = stateGloas.getLatestExecutionPayloadBid().getGasLimit();
    validFeeRecipient = dataStructureUtil.randomEth1Address();

    when(recentChainData.getExecutionGasLimitForBlockRootAndHash(any(), any()))
        .thenReturn(Optional.of(validGasLimit));
    when(gossipValidationHelper.getShufflingDependentRoot(any(), any()))
        .thenReturn(Optional.of(DEPENDENT_ROOT));
    when(proposerPreferencesManager.getProposerPreferences(any(), any()))
        .thenReturn(Optional.of(createProposerPreferences(validFeeRecipient, validGasLimit)));
  }

  @Test
  void returnsTrueForValidBid() {
    assertThat(validate(validSignedBid(), state)).isTrue();
  }

  @Test
  void rejectsIfBuilderIsNotActive() {
    // Builder at index 1 does not exist — state only has a single builder at index 0
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX.plus(1),
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsIfSlotMismatch() {
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot().plus(1),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsBidForASlotWhoseMilestoneIsBeforeGloas() {
    final Spec preGloasSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(100));
    final BeaconState preGloasState =
        new DataStructureUtil(preGloasSpec).randomBeaconState(UInt64.ONE);
    final BuilderBidValidator preGloasValidator =
        new BuilderBidValidator(
            preGloasSpec, proposerPreferencesManager, recentChainData, gossipValidationHelper);
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            preGloasState.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);

    assertThat(
            preGloasValidator.validateBid(
                bid,
                preGloasState,
                bid.getMessage().getParentBlockHash(),
                bid.getMessage().getParentBlockRoot(),
                builderEntryWithPubkeys(List.of())))
        .isFalse();
  }

  @Test
  void rejectsIfParentBlockHashDoesNotMatchEither() {
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            dataStructureUtil.randomBytes32(),
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void acceptsBidWhoseParentBlockHashMatchesLatestBlockHash() {
    final Bytes32 latestBlockHash = BeaconStateGloas.required(state).getLatestBlockHash();
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            latestBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isTrue();
  }

  @Test
  void rejectsBidExtendingAParentOtherThanTheOneBeingBuiltOn() {
    // The bid extends the EMPTY variant, which the spec permits in isolation, but the block is
    // being built on the FULL variant
    final Bytes32 latestBlockHash = BeaconStateGloas.required(state).getLatestBlockHash();
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            latestBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(
            validator.validateBid(
                bid,
                state,
                validParentBlockHash,
                validParentBlockRoot,
                builderEntryWithPubkeys(List.of())))
        .isFalse();
  }

  @Test
  void rejectsBidWhoseParentBlockRootIsNotTheOneBeingBuiltOn() {
    assertThat(
            validator.validateBid(
                validSignedBid(),
                state,
                validParentBlockHash,
                dataStructureUtil.randomBytes32(),
                builderEntryWithPubkeys(List.of())))
        .isFalse();
  }

  @Test
  void rejectsIfParentBlockRootMismatch() {
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            dataStructureUtil.randomBytes32(),
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsIfPrevRandaoMismatch() {
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            dataStructureUtil.randomBytes32(),
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsWhenFeeRecipientDoesNotMatchProposerPreferences() {
    final Eth1Address feeRecipient = dataStructureUtil.randomEth1Address();
    when(proposerPreferencesManager.getProposerPreferences(state.getSlot(), DEPENDENT_ROOT))
        .thenReturn(
            Optional.of(
                createProposerPreferences(dataStructureUtil.randomEth1Address(), validGasLimit)));

    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            feeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsIfGasLimitNotCompatibleWithProposerPreferences() {
    // Same fee recipient as the preferences, so the bid reaches the gas limit check
    // Target gas limit far out of the compatible range forces a specific adjusted value
    final UInt64 incompatibleTargetGasLimit = validGasLimit.plus(1_000_000);
    when(proposerPreferencesManager.getProposerPreferences(state.getSlot(), DEPENDENT_ROOT))
        .thenReturn(
            Optional.of(createProposerPreferences(validFeeRecipient, incompatibleTargetGasLimit)));

    // Bid gas limit equals the parent gas limit but the required value (capped at max) differs
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsWhenParentGasLimitIsUnavailable() {
    when(recentChainData.getExecutionGasLimitForBlockRootAndHash(any(), any()))
        .thenReturn(Optional.empty());
    assertThat(validate(validSignedBid(), state)).isFalse();
  }

  @Test
  void rejectsIfBuilderCannotCoverBidValue() {
    // Builder has zero balance — below MIN_DEPOSIT_AMOUNT, so it cannot cover any positive bid
    final BeaconState lowBalanceState = createStateWithActiveBuilder(UInt64.ZERO);
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(lowBalanceState);
    final BeaconStateAccessorsGloas beaconStateAccessors =
        BeaconStateAccessorsGloas.required(
            spec.atSlot(lowBalanceState.getSlot()).beaconStateAccessors());

    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            lowBalanceState.getSlot(),
            UInt64.ONE,
            stateGloas.getLatestExecutionPayloadBid().getBlockHash(),
            lowBalanceState.getLatestBlockHeader().hashTreeRoot(),
            beaconStateAccessors.getRandaoMix(
                lowBalanceState, beaconStateAccessors.getCurrentEpoch(lowBalanceState)),
            // matches the stubbed parent gas limit and the preferences target, so the bid reaches
            // the collateral check
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, lowBalanceState)).isFalse();
  }

  @Test
  void rejectsZeroValueBidFromAnUnderfundedBuilder() {
    // validate_bid skips the collateral check when value is 0, but process_execution_payload_bid
    // does not, and an underfunded builder cannot cover even a zero amount
    final BeaconState lowBalanceState = createStateWithActiveBuilder(UInt64.ZERO);
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(lowBalanceState);
    final BeaconStateAccessorsGloas beaconStateAccessors =
        BeaconStateAccessorsGloas.required(
            spec.atSlot(lowBalanceState.getSlot()).beaconStateAccessors());

    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            lowBalanceState.getSlot(),
            UInt64.ZERO,
            stateGloas.getLatestExecutionPayloadBid().getBlockHash(),
            lowBalanceState.getLatestBlockHeader().hashTreeRoot(),
            beaconStateAccessors.getRandaoMix(
                lowBalanceState, beaconStateAccessors.getCurrentEpoch(lowBalanceState)),
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, lowBalanceState)).isFalse();
  }

  @Test
  void rejectsBidFromBuilderNotInTheEntryPubkeyAllowlist() {
    final BuilderEntry builderEntry =
        builderEntryWithPubkeys(List.of(dataStructureUtil.randomPublicKey()));
    assertThat(validate(validSignedBid(), state, builderEntry)).isFalse();
  }

  @Test
  void acceptsBidFromBuilderInTheEntryPubkeyAllowlist() {
    final BuilderEntry builderEntry =
        builderEntryWithPubkeys(List.of(dataStructureUtil.randomPublicKey(), builderPubkey()));
    assertThat(validate(validSignedBid(), state, builderEntry)).isTrue();
  }

  @Test
  void acceptsBidFromAnyBuilderWhenTheEntryPubkeyAllowlistIsEmpty() {
    assertThat(validate(validSignedBid(), state, builderEntryWithPubkeys(List.of()))).isTrue();
  }

  @Test
  void rejectsWhenProposerPreferencesAbsent() {
    when(proposerPreferencesManager.getProposerPreferences(any(), any()))
        .thenReturn(Optional.empty());
    assertThat(validate(validSignedBid(), state)).isFalse();
  }

  @Test
  void rejectsWhenShufflingDependentRootIsUnavailable() {
    when(gossipValidationHelper.getShufflingDependentRoot(any(), any()))
        .thenReturn(Optional.empty());
    assertThat(validate(validSignedBid(), state)).isFalse();
  }

  @Test
  void rejectsIfBuilderIsNotAPayloadBuilder() {
    final BeaconStateGloas stateWithOtherBuilderVersion =
        createStateWithActiveBuilder(
            spec.getGenesisSpec().getConfig().getMaxEffectiveBalance(),
            PAYLOAD_BUILDER_VERSION + 1);
    final BeaconStateAccessorsGloas beaconStateAccessors =
        BeaconStateAccessorsGloas.required(
            spec.atSlot(stateWithOtherBuilderVersion.getSlot()).beaconStateAccessors());

    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            stateWithOtherBuilderVersion.getSlot(),
            UInt64.ZERO,
            stateWithOtherBuilderVersion.getLatestExecutionPayloadBid().getBlockHash(),
            stateWithOtherBuilderVersion.getLatestBlockHeader().hashTreeRoot(),
            beaconStateAccessors.getRandaoMix(
                stateWithOtherBuilderVersion,
                beaconStateAccessors.getCurrentEpoch(stateWithOtherBuilderVersion)),
            validGasLimit,
            validFeeRecipient);
    assertThat(validate(bid, stateWithOtherBuilderVersion)).isFalse();
  }

  @Test
  void rejectsIfBlockHashIsTheSameAsParentBlockHash() {
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient,
            validParentBlockHash,
            0);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void rejectsIfBlobKzgCommitmentCountExceedsTheMaximum() {
    final int maxBlobsPerBlock = spec.getMaxBlobsPerBlockAtSlot(state.getSlot()).orElseThrow();
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient,
            dataStructureUtil.randomBytes32(),
            maxBlobsPerBlock + 1);
    assertThat(validate(bid, state)).isFalse();
  }

  @Test
  void acceptsBidWithTheMaximumNumberOfBlobKzgCommitments() {
    final int maxBlobsPerBlock = spec.getMaxBlobsPerBlockAtSlot(state.getSlot()).orElseThrow();
    final SignedExecutionPayloadBid bid =
        signedBidWith(
            BUILDER_INDEX,
            state.getSlot(),
            UInt64.ZERO,
            validParentBlockHash,
            validParentBlockRoot,
            validPrevRandao,
            validGasLimit,
            validFeeRecipient,
            dataStructureUtil.randomBytes32(),
            maxBlobsPerBlock);
    assertThat(validate(bid, state)).isTrue();
  }

  /**
   * Validates the bid against the parent it was built on, so that each test exercises the check it
   * targets. The bids that do not extend the parent being built on are covered separately.
   */
  private boolean validate(final SignedExecutionPayloadBid signedBid, final BeaconState state) {
    return validate(signedBid, state, builderEntryWithPubkeys(List.of()));
  }

  private boolean validate(
      final SignedExecutionPayloadBid signedBid,
      final BeaconState state,
      final BuilderEntry builderEntry) {
    final ExecutionPayloadBid bid = signedBid.getMessage();
    return validator.validateBid(
        signedBid, state, bid.getParentBlockHash(), bid.getParentBlockRoot(), builderEntry);
  }

  private BLSPublicKey builderPubkey() {
    return state.getBuilders().get(BUILDER_INDEX.intValue()).getPublicKey();
  }

  private BuilderEntry builderEntryWithPubkeys(final List<BLSPublicKey> builderPubkeys) {
    return BUILDER_ENTRY_SCHEMA.create(
        Bytes.of("https://builder.example.com".getBytes(StandardCharsets.UTF_8)),
        dataStructureUtil.randomSignedBuilderRequestAuth(),
        builderPubkeys,
        UInt64.MAX_VALUE,
        UInt64.ZERO,
        UInt64.valueOf(100));
  }

  private SignedExecutionPayloadBid validSignedBid() {
    return signedBidWith(
        BUILDER_INDEX,
        state.getSlot(),
        UInt64.ZERO,
        validParentBlockHash,
        validParentBlockRoot,
        validPrevRandao,
        validGasLimit,
        validFeeRecipient);
  }

  private SignedExecutionPayloadBid signedBidWith(
      final UInt64 builderIndex,
      final UInt64 slot,
      final UInt64 value,
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final Bytes32 prevRandao,
      final UInt64 gasLimit,
      final Eth1Address feeRecipient) {
    return signedBidWith(
        builderIndex,
        slot,
        value,
        parentBlockHash,
        parentBlockRoot,
        prevRandao,
        gasLimit,
        feeRecipient,
        dataStructureUtil.randomBytes32(),
        0);
  }

  private SignedExecutionPayloadBid signedBidWith(
      final UInt64 builderIndex,
      final UInt64 slot,
      final UInt64 value,
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final Bytes32 prevRandao,
      final UInt64 gasLimit,
      final Eth1Address feeRecipient,
      final Bytes32 blockHash,
      final int blobKzgCommitmentCount) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadBidSchema<? extends ExecutionPayloadBid> schema =
        schemaDefinitions.getExecutionPayloadBidSchema();
    final ExecutionPayloadBid bid =
        schema.create(
            parentBlockHash,
            parentBlockRoot,
            blockHash,
            prevRandao,
            feeRecipient,
            gasLimit,
            builderIndex,
            slot,
            value,
            UInt64.ZERO,
            schema
                .getBlobKzgCommitmentsSchema()
                .createFromElements(
                    IntStream.range(0, blobKzgCommitmentCount)
                        .mapToObj(__ -> dataStructureUtil.randomSszKZGCommitment())
                        .toList()),
            dataStructureUtil.randomBytes32());
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, dataStructureUtil.randomSignature());
  }

  private ProposerPreferences createProposerPreferences(
      final Eth1Address feeRecipient, final UInt64 targetGasLimit) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions());
    return schemaDefinitions
        .getProposerPreferencesSchema()
        .create(
            DEPENDENT_ROOT,
            state.getSlot(),
            dataStructureUtil.randomUInt64(),
            feeRecipient,
            targetGasLimit);
  }

  private BeaconStateGloas createStateWithActiveBuilder(final UInt64 builderBalance) {
    return createStateWithActiveBuilder(builderBalance, PAYLOAD_BUILDER_VERSION);
  }

  private BeaconStateGloas createStateWithActiveBuilder(
      final UInt64 builderBalance, final int builderVersion) {
    final UInt64 slot =
        FINALIZED_EPOCH.times(spec.getGenesisSpec().getConfig().getSlotsPerEpoch()).plus(1);

    final BeaconStateSchemaGloas stateSchema =
        BeaconStateSchemaGloas.required(
            spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions().getBeaconStateSchema());

    final Builder activeBuilder =
        dataStructureUtil
            .builderBuilder()
            .depositEpoch(UInt64.ZERO)
            .withdrawableEpoch(FAR_FUTURE_EPOCH)
            .balance(builderBalance)
            .version(builderVersion)
            .build();

    return dataStructureUtil
        .stateBuilderGloas(10, 0, 10)
        .builders(stateSchema.getBuildersSchema().createFromElements(List.of(activeBuilder)))
        .slot(slot)
        // stubbing the finalized checkpoint, because builder needs to be active
        .finalizedCheckpoint(new Checkpoint(FINALIZED_EPOCH, dataStructureUtil.randomBytes32()))
        .build();
  }
}
