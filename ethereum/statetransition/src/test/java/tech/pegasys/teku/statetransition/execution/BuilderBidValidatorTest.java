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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
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
import tech.pegasys.teku.storage.client.RecentChainData;

public class BuilderBidValidatorTest {

  private static final UInt64 FINALIZED_EPOCH = UInt64.valueOf(5);
  private static final UInt64 BUILDER_INDEX = UInt64.ZERO;

  // NOOP verifier so random signatures pass — lets tests focus on the other validation rules
  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          config -> config.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final BuilderBidValidator validator =
      new BuilderBidValidator(spec, proposerPreferencesManager, recentChainData);

  private BeaconStateGloas state;
  private Bytes32 validParentBlockHash;
  private Bytes32 validParentBlockRoot;
  private Bytes32 validPrevRandao;
  private UInt64 validGasLimit;

  @BeforeEach
  void setUp() {
    when(proposerPreferencesManager.getProposerPreferences(any())).thenReturn(Optional.empty());
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

    when(recentChainData.getExecutionGasLimitForBlockRootAndHash(any(), any()))
        .thenReturn(Optional.of(validGasLimit));
  }

  @Test
  void returnsTrueForValidBid() {
    assertThat(validator.validateBid(validSignedBid(), state)).isTrue();
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
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, state)).isFalse();
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
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, state)).isFalse();
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
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, state)).isFalse();
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
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, state)).isTrue();
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
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, state)).isFalse();
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
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, state)).isFalse();
  }

  @Test
  void rejectsWhenFeeRecipientDoesNotMatchProposerPreferences() {
    final Eth1Address feeRecipient = dataStructureUtil.randomEth1Address();
    when(proposerPreferencesManager.getProposerPreferences(state.getSlot()))
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
    assertThat(validator.validateBid(bid, state)).isFalse();
  }

  @Test
  void rejectsIfGasLimitNotCompatibleWithProposerPreferences() {
    final Eth1Address bidFeeRecipient = dataStructureUtil.randomEth1Address();
    final Eth1Address preferencesFeeRecipient = dataStructureUtil.randomEth1Address();
    // Target gas limit far out of the compatible range forces a specific adjusted value
    final UInt64 incompatibleTargetGasLimit = validGasLimit.plus(1_000_000);
    when(proposerPreferencesManager.getProposerPreferences(state.getSlot()))
        .thenReturn(
            Optional.of(
                createProposerPreferences(preferencesFeeRecipient, incompatibleTargetGasLimit)));

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
            bidFeeRecipient);
    assertThat(validator.validateBid(bid, state)).isFalse();
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
            stateGloas.getLatestExecutionPayloadBid().getGasLimit(),
            dataStructureUtil.randomEth1Address());
    assertThat(validator.validateBid(bid, lowBalanceState)).isFalse();
  }

  @Test
  void skipsFeeAndGasLimitChecksWhenProposerPreferencesAbsent() {
    when(proposerPreferencesManager.getProposerPreferences(any())).thenReturn(Optional.empty());
    assertThat(validator.validateBid(validSignedBid(), state)).isTrue();
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
        dataStructureUtil.randomEth1Address());
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
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadBidSchema schema = schemaDefinitions.getExecutionPayloadBidSchema();
    final ExecutionPayloadBid bid =
        schema.create(
            parentBlockHash,
            parentBlockRoot,
            dataStructureUtil.randomBytes32(),
            prevRandao,
            feeRecipient,
            gasLimit,
            builderIndex,
            slot,
            value,
            UInt64.ZERO,
            schema.getBlobKzgCommitmentsSchema().createFromElements(List.of()),
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
            dataStructureUtil.randomBytes32(),
            state.getSlot(),
            dataStructureUtil.randomUInt64(),
            feeRecipient,
            targetGasLimit);
  }

  private BeaconStateGloas createStateWithActiveBuilder(final UInt64 builderBalance) {
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
