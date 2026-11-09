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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = GLOAS)
class ProposersDataManagerGloasTest {

  private RecentChainData recentChainData;
  private Spec spec;
  private DataStructureUtil data;
  private InlineEventThread eventThread;
  private ProposersDataManager manager;
  private ProposerPreferencesManager proposerPreferencesManager;
  private Eth1Address defaultFeeRecipient;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    data = specContext.getDataStructureUtil();
    recentChainData = mock(RecentChainData.class);
    eventThread = new InlineEventThread();
    defaultFeeRecipient = data.randomEth1Address();
    proposerPreferencesManager = mock(ProposerPreferencesManager.class);
    when(proposerPreferencesManager.getProposerPreferences(any(), any()))
        .thenReturn(Optional.empty());
    manager =
        new ProposersDataManager(
            eventThread,
            spec,
            new NoOpMetricsSystem(),
            ExecutionLayerChannel.NOOP,
            recentChainData,
            Optional.of(defaultFeeRecipient),
            false,
            proposerPreferencesManager);
    when(recentChainData.isJustifiedCheckpointFullyValidated()).thenReturn(true);
  }

  @TestTemplate
  void calculatePayloadBuildingAttributes_shouldUseEffectiveWithdrawalsForFullParent()
      throws Exception {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 parentRoot = data.randomBytes32();
    final ForkChoiceNode parent = ForkChoiceNode.createFull(parentRoot);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(blockSlot).getSchemaDefinitions());
    final ExecutionRequests parentExecutionRequests =
        schemaDefinitions.getExecutionRequestsSchema().getDefault();
    final BeaconState state =
        createStateWithFullParentBid(blockSlot, schemaDefinitions, parentExecutionRequests);
    final SignedBlindedExecutionPayloadEnvelope parentEnvelope =
        createParentEnvelope(blockSlot, parentRoot, schemaDefinitions, parentExecutionRequests);
    final Optional<List<Withdrawal>> expectedWithdrawals =
        spec.getPayloadAttributeWithdrawals(
            state, parent.payloadStatus(), Optional.of(parentEnvelope.getExecutionRequests()));
    assertThat(expectedWithdrawals).isNotEqualTo(spec.getExpectedWithdrawals(state));
    final ForkChoiceUpdateData forkChoiceUpdateData = forkChoiceUpdateData(parent, blockSlot);
    prepareLocalProposer(state, blockSlot);
    when(recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, parentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(recentChainData.retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentEnvelope)));

    final PayloadBuildingAttributes attributes =
        safeJoin(calculate(blockSlot, forkChoiceUpdateData)).orElseThrow();

    assertThat(attributes.withdrawals()).isEqualTo(expectedWithdrawals);
    verify(recentChainData).retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot);
  }

  private BeaconState createStateWithFullParentBid(
      final UInt64 blockSlot,
      final SchemaDefinitionsGloas schemaDefinitions,
      final ExecutionRequests parentExecutionRequests) {
    final UInt64 builderIndex = UInt64.ZERO;
    final UInt64 parentPayloadValue = UInt64.valueOf(1234);
    final Bytes32 latestExecutionPayloadBlockHash = Bytes32.fromHexString("0x01");
    final Bytes32 latestBlockHash = Bytes32.fromHexString("0x02");
    final ExecutionPayloadBid latestExecutionPayloadBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .create(
                data.randomBytes32(),
                data.randomBytes32(),
                latestExecutionPayloadBlockHash,
                data.randomBytes32(),
                data.randomEth1Address(),
                data.randomUInt64(),
                builderIndex,
                UInt64.ZERO,
                parentPayloadValue,
                UInt64.ZERO,
                schemaDefinitions
                    .getExecutionPayloadBidSchema()
                    .getBlobKzgCommitmentsSchema()
                    .createFromElements(data.randomBlobKzgCommitments().asList()),
                parentExecutionRequests.hashTreeRoot());

    return data.randomBeaconState(blockSlot)
        .updated(
            state -> {
              final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
              stateGloas.setLatestBlockHash(latestBlockHash);
              stateGloas.setLatestExecutionPayloadBid(latestExecutionPayloadBid);
              stateGloas.setNextWithdrawalBuilderIndex(builderIndex);
              stateGloas.setNextWithdrawalValidatorIndex(UInt64.ZERO);
              stateGloas.setBuilders(
                  BeaconStateSchemaGloas.required(schemaDefinitions.getBeaconStateSchema())
                      .getBuildersSchema()
                      .createFromElements(
                          List.of(data.builderBuilder().balance(parentPayloadValue).build())));
              stateGloas.setPayloadExpectedWithdrawals(
                  schemaDefinitions
                      .getExecutionPayloadSchema()
                      .getWithdrawalsSchemaRequired()
                      .createFromElements(
                          List.of(data.randomWithdrawal(UInt64.valueOf(7), UInt64.valueOf(11)))));
            });
  }

  private SignedBlindedExecutionPayloadEnvelope createParentEnvelope(
      final UInt64 blockSlot,
      final Bytes32 parentRoot,
      final SchemaDefinitionsGloas schemaDefinitions,
      final ExecutionRequests parentExecutionRequests) {
    return schemaDefinitions
        .getSignedBlindedExecutionPayloadEnvelopeSchema()
        .create(
            schemaDefinitions
                .getBlindedExecutionPayloadEnvelopeSchema()
                .create(
                    data.randomExecutionPayloadHeader(spec.atSlot(blockSlot)),
                    parentExecutionRequests,
                    UInt64.ZERO,
                    data.randomBytes32(),
                    parentRoot),
            data.randomSignature());
  }

  @TestTemplate
  void calculatePayloadBuildingAttributes_shouldUsePayloadExpectedWithdrawalsForEmptyParent() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 parentRoot = data.randomBytes32();
    final ForkChoiceNode parent = ForkChoiceNode.createEmpty(parentRoot);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(blockSlot).getSchemaDefinitions());
    final ExecutionRequests parentExecutionRequests =
        schemaDefinitions.getExecutionRequestsSchema().getDefault();
    final BeaconState state =
        createStateWithFullParentBid(blockSlot, schemaDefinitions, parentExecutionRequests);
    final Optional<List<Withdrawal>> payloadExpectedWithdrawals =
        Optional.of(BeaconStateGloas.required(state).getPayloadExpectedWithdrawals().asList());
    assertThat(payloadExpectedWithdrawals).isNotEqualTo(spec.getExpectedWithdrawals(state));
    final ForkChoiceUpdateData forkChoiceUpdateData = forkChoiceUpdateData(parent, blockSlot);
    prepareLocalProposer(state, blockSlot);
    when(recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, parentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final PayloadBuildingAttributes attributes =
        safeJoin(calculate(blockSlot, forkChoiceUpdateData)).orElseThrow();

    assertThat(attributes.withdrawals()).isEqualTo(payloadExpectedWithdrawals);
    verify(recentChainData, never()).retrieveSignedBlindedExecutionPayloadByBlockRoot(any());
  }

  @TestTemplate
  void calculatePayloadBuildingAttributes_shouldNotFetchParentExecutionRequestsForPreGloasParent() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 parentRoot = data.randomBytes32();
    final ForkChoiceNode parent = ForkChoiceNode.createBase(parentRoot);
    final BeaconState state = data.randomBeaconState(blockSlot);
    final ForkChoiceUpdateData forkChoiceUpdateData = forkChoiceUpdateData(parent, blockSlot);
    prepareLocalProposer(state, blockSlot);
    when(recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, parentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final PayloadBuildingAttributes attributes =
        safeJoin(calculate(blockSlot, forkChoiceUpdateData)).orElseThrow();

    assertThat(attributes.withdrawals()).isEqualTo(spec.getExpectedWithdrawals(state));
    verify(recentChainData, never()).retrieveSignedBlindedExecutionPayloadByBlockRoot(any());
  }

  @TestTemplate
  void calculatePayloadBuildingAttributes_shouldFailWhenFullParentExecutionRequestsAreMissing() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 parentRoot = data.randomBytes32();
    final ForkChoiceNode parent = ForkChoiceNode.createFull(parentRoot);
    final BeaconState state = data.randomBeaconState(blockSlot);
    final ForkChoiceUpdateData forkChoiceUpdateData = forkChoiceUpdateData(parent, blockSlot);
    prepareLocalProposer(state, blockSlot);
    when(recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, parentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(recentChainData.retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThatSafeFuture(calculate(blockSlot, forkChoiceUpdateData))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("Execution Requests for parent root");
  }

  @TestTemplate
  void updatePreparedProposers_shouldUseFeeRecipientFromAcceptedProposerPreferences() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 dependentRoot = data.randomBytes32();
    final BeaconState state = data.randomBeaconState(blockSlot);
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final Eth1Address feeRecipient = data.randomEth1Address();
    assertThat(feeRecipient).isNotEqualTo(defaultFeeRecipient);
    final SignedProposerPreferences preferences =
        signedProposerPreferences(blockSlot, dependentRoot, proposerIndex, feeRecipient);
    when(proposerPreferencesManager.getProposerPreferences(blockSlot, dependentRoot))
        .thenReturn(Optional.of(preferences.getMessage()));
    withShufflingDependentRoot(dependentRoot);

    manager.updatePreparedProposersFromProposerPreferences(List.of(preferences), blockSlot);

    assertThat(manager.isValidatorConnected(proposerIndex.intValue(), blockSlot)).isTrue();
    // not mandatory: the accepted preferences are what makes this our proposal
    assertThat(feeRecipientFor(state, blockSlot, false)).isEqualTo(feeRecipient);
  }

  @TestTemplate
  void calculatePayloadBuildingAttributes_shouldSkipOurProposerWithoutAcceptedPreferences() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 dependentRoot = data.randomBytes32();
    final BeaconState state = data.randomBeaconState(blockSlot);
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final SignedProposerPreferences preferences =
        signedProposerPreferences(
            blockSlot, dependentRoot, proposerIndex, data.randomEth1Address());
    when(proposerPreferencesManager.getProposerPreferences(blockSlot, dependentRoot))
        .thenReturn(Optional.of(preferences.getMessage()));
    manager.updatePreparedProposersFromProposerPreferences(List.of(preferences), blockSlot);
    assertThat(manager.isValidatorConnected(proposerIndex.intValue(), blockSlot)).isTrue();
    // a reorg changed the shuffling dependent root after the preferences were accepted
    withShufflingDependentRoot(data.randomBytes32());
    final Bytes32 parentRoot = data.randomBytes32();
    when(recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, parentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    assertThat(
            safeJoin(
                calculate(
                    blockSlot,
                    forkChoiceUpdateData(ForkChoiceNode.createBase(parentRoot), blockSlot),
                    false)))
        .isEmpty();
  }

  @TestTemplate
  void updatePreparedProposers_shouldNotUseFeeRecipientAcceptedForAnotherDependentRoot() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 dependentRoot = data.randomBytes32();
    final BeaconState state = data.randomBeaconState(blockSlot);
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final Eth1Address feeRecipient = data.randomEth1Address();
    assertThat(feeRecipient).isNotEqualTo(defaultFeeRecipient);
    final SignedProposerPreferences preferences =
        signedProposerPreferences(blockSlot, dependentRoot, proposerIndex, feeRecipient);
    when(proposerPreferencesManager.getProposerPreferences(blockSlot, dependentRoot))
        .thenReturn(Optional.of(preferences.getMessage()));
    // a reorg changed the shuffling dependent root after the preferences were accepted
    withShufflingDependentRoot(data.randomBytes32());

    manager.updatePreparedProposersFromProposerPreferences(List.of(preferences), blockSlot);

    assertThat(feeRecipientFor(state, blockSlot)).isEqualTo(defaultFeeRecipient);
  }

  @TestTemplate
  void updatePreparedProposers_shouldIgnoreProposerPreferencesWhichWereNotAccepted() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 dependentRoot = data.randomBytes32();
    final BeaconState state = data.randomBeaconState(blockSlot);
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final SignedProposerPreferences preferences =
        signedProposerPreferences(
            blockSlot, dependentRoot, proposerIndex, data.randomEth1Address());

    manager.updatePreparedProposersFromProposerPreferences(List.of(preferences), blockSlot);

    assertThat(manager.isValidatorConnected(proposerIndex.intValue(), blockSlot)).isFalse();
  }

  @TestTemplate
  void updatePreparedProposers_shouldIgnoreProposerPreferencesWhichDifferFromTheAcceptedOnes() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final Bytes32 dependentRoot = data.randomBytes32();
    final BeaconState state = data.randomBeaconState(blockSlot);
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final SignedProposerPreferences accepted =
        signedProposerPreferences(
            blockSlot, dependentRoot, proposerIndex, data.randomEth1Address());
    // same proposal slot and dependent root, so the submission is only ignored as already seen
    final SignedProposerPreferences submitted =
        signedProposerPreferences(
            blockSlot, dependentRoot, proposerIndex, data.randomEth1Address());
    when(proposerPreferencesManager.getProposerPreferences(blockSlot, dependentRoot))
        .thenReturn(Optional.of(accepted.getMessage()));

    manager.updatePreparedProposersFromProposerPreferences(List.of(submitted), blockSlot);

    assertThat(manager.isValidatorConnected(proposerIndex.intValue(), blockSlot)).isFalse();
  }

  @TestTemplate
  void updatePreparedProposers_shouldIgnorePrepareBeaconProposerAfterGloas() {
    final UInt64 blockSlot = UInt64.valueOf(16);
    final BeaconState state = data.randomBeaconState(blockSlot);
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final Eth1Address feeRecipient = data.randomEth1Address();
    assertThat(feeRecipient).isNotEqualTo(defaultFeeRecipient);

    manager.updatePreparedProposersFromPrepareBeaconProposer(
        List.of(new BeaconPreparableProposer(proposerIndex, feeRecipient)), blockSlot);

    assertThat(feeRecipientFor(state, blockSlot)).isEqualTo(defaultFeeRecipient);
  }

  private Eth1Address feeRecipientFor(final BeaconState state, final UInt64 blockSlot) {
    return feeRecipientFor(state, blockSlot, true);
  }

  private Eth1Address feeRecipientFor(
      final BeaconState state, final UInt64 blockSlot, final boolean mandatory) {
    final Bytes32 parentRoot = data.randomBytes32();
    final ForkChoiceUpdateData forkChoiceUpdateData =
        forkChoiceUpdateData(ForkChoiceNode.createBase(parentRoot), blockSlot);
    when(recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, parentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    return safeJoin(calculate(blockSlot, forkChoiceUpdateData, mandatory))
        .orElseThrow()
        .feeRecipient();
  }

  private void withShufflingDependentRoot(final Bytes32 dependentRoot) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(forkChoiceStrategy.getAncestor(any(), any())).thenReturn(Optional.of(dependentRoot));
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(forkChoiceStrategy));
  }

  private SignedProposerPreferences signedProposerPreferences(
      final UInt64 proposalSlot,
      final Bytes32 dependentRoot,
      final UInt64 validatorIndex,
      final Eth1Address feeRecipient) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(proposalSlot).getSchemaDefinitions());
    return schemaDefinitions
        .getSignedProposerPreferencesSchema()
        .create(
            schemaDefinitions
                .getProposerPreferencesSchema()
                .create(
                    dependentRoot, proposalSlot, validatorIndex, feeRecipient, data.randomUInt64()),
            data.randomSignature());
  }

  private SafeFuture<Optional<PayloadBuildingAttributes>> calculate(
      final UInt64 blockSlot, final ForkChoiceUpdateData forkChoiceUpdateData) {
    return calculate(blockSlot, forkChoiceUpdateData, true);
  }

  private SafeFuture<Optional<PayloadBuildingAttributes>> calculate(
      final UInt64 blockSlot,
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final boolean mandatory) {
    return eventThread.executeFuture(
        () ->
            manager.calculatePayloadBuildingAttributes(
                blockSlot, true, forkChoiceUpdateData, mandatory));
  }

  private void prepareLocalProposer(final BeaconState state, final UInt64 blockSlot) {
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    manager.updatePreparedProposersFromPrepareBeaconProposer(
        List.of(new BeaconPreparableProposer(proposerIndex, defaultFeeRecipient)), blockSlot);
  }

  private ForkChoiceUpdateData forkChoiceUpdateData(
      final ForkChoiceNode parent, final UInt64 blockSlot) {
    final ForkChoiceState forkChoiceState =
        new ForkChoiceState(
            parent,
            blockSlot.decrement(),
            UInt64.ONE,
            data.randomBytes32(),
            data.randomBytes32(),
            data.randomBytes32(),
            false);
    return new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), Optional.empty());
  }
}
