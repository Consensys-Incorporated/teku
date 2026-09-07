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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdateData.RESEND_AFTER_MILLIS;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoiceNotifierTest {

  private final int numberOfValidators = 3;
  private final InlineEventThread eventThread = new InlineEventThread();
  private final Spec spec =
      TestSpecFactory.createMinimalBellatrix(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  private StubMetricsSystem metricsSystem;
  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private ReadOnlyForkChoiceStrategy forkChoiceStrategy;
  private ProposersDataManager proposersDataManager;

  private final Optional<Eth1Address> defaultFeeRecipient =
      Optional.of(Eth1Address.fromHexString("0x2Df386eFF130f991321bfC4F8372Ba838b9AB14B"));

  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private ForkChoiceNotifierImpl notifier;
  private ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification;

  @BeforeEach
  void setUp() {
    setUp(false);
  }

  void setUp(final boolean doNotInitializeWithDefaultFeeRecipient) {
    setUp(doNotInitializeWithDefaultFeeRecipient, false);
  }

  void setUp(
      final boolean doNotInitializeWithDefaultFeeRecipient,
      final boolean forkChoiceUpdatedAlwaysSendPayloadAttributes) {
    // initialize post-merge by default
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(numberOfValidators, spec);
    recentChainData = storageSystem.recentChainData();
    metricsSystem = new StubMetricsSystem();
    proposersDataManager =
        spy(
            new ProposersDataManager(
                eventThread,
                spec,
                metricsSystem,
                executionLayerChannel,
                recentChainData,
                doNotInitializeWithDefaultFeeRecipient ? Optional.empty() : defaultFeeRecipient,
                forkChoiceUpdatedAlwaysSendPayloadAttributes));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            false);
    notifier.onSyncingStatusChanged(true); // Start in sync to make testing easier
    // store fcu notification
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(false, dataStructureUtil.randomExecutionPayloadHeader());
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();

    when(executionLayerChannel.builderRegisterValidators(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);
    when(executionLayerChannel.engineNewPayload(any(), any()))
        .thenReturn(SafeFuture.completedFuture(PayloadStatus.VALID));
    when(executionLayerChannel.engineForkChoiceUpdated(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.empty())));
  }

  void reInitializePreMerge() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(numberOfValidators, spec);
    recentChainData = storageSystem.recentChainData();
    metricsSystem = new StubMetricsSystem();
    proposersDataManager =
        spy(
            new ProposersDataManager(
                eventThread,
                spec,
                metricsSystem,
                executionLayerChannel,
                recentChainData,
                defaultFeeRecipient,
                false));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            false);
    notifier.onSyncingStatusChanged(true);
    // store fcu notification
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);
    storageSystem.chainUpdater().initializeGenesis(false);
    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    forkChoiceStrategy = recentChainData.getForkChoiceStrategy().orElseThrow();
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationToExecutionEngine() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithPayloadBuildingAttributesForNextProposer() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendNotificationWithPayloadBuildingAttributesForRequestedBlockProductionSlot() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendWhenRequestedBlockProductionSlotAlreadyResolvedLateBlockReorgOverride() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();

    recentChainData = mock(RecentChainData.class);
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            false);
    // store fcu notification
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);
    notifier.onSyncingStatusChanged(true);

    final UInt64 blockSlot = headState.getSlot().plus(1);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendIfNotShouldShouldOverrideForkChoiceUpdateAndLateBlockReorgIsEnabled() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();

    recentChainData = mock(RecentChainData.class);
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            true);
    // store fcu notification
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);
    notifier.onSyncingStatusChanged(true);

    final UInt64 blockSlot = headState.getSlot().plus(1);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    when(recentChainData.shouldOverrideForkChoiceUpdate(any(), any())).thenReturn(false);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
  }

  @Test
  void onForkChoiceUpdated_shouldSkipOrdinaryForkChoiceUpdateWhenLateBlockReorgOverrideIsActive() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();

    recentChainData = mock(RecentChainData.class);
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            true);
    notifier.onSyncingStatusChanged(true);
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);

    when(recentChainData.shouldOverrideForkChoiceUpdate(
            forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot()))
        .thenReturn(true);

    notifyForkChoiceUpdated(
        forkChoiceState, Optional.empty(), notification -> assertThat(notification).isNull());

    verify(recentChainData)
        .shouldOverrideForkChoiceUpdate(
            forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot());
    verifyNoInteractions(executionLayerChannel);
  }

  @Test
  void onForkChoiceUpdated_shouldNotCheckLateBlockReorgOverrideWhenDisabled() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();

    recentChainData = mock(RecentChainData.class);
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            false);
    notifier.onSyncingStatusChanged(true);
    notifier.subscribeToForkChoiceUpdatedResult(
        notification -> forkChoiceUpdatedResultNotification = notification);

    when(recentChainData.getCurrentSlot()).thenReturn(Optional.empty());

    notifyForkChoiceUpdated(forkChoiceState);

    verify(recentChainData, never()).shouldOverrideForkChoiceUpdate(any(), any());
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendNotificationWithoutPayloadBuildingAttributesWhenNotProposingNext() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final int notTheNextProposer = spec.getBeaconProposerIndex(headState, blockSlot) + 1;
    proposersDataManager.updatePreparedProposersFromPrepareBeaconProposer(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(notTheNextProposer), dataStructureUtil.randomEth1Address())),
        recentChainData.getHeadSlot());

    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationWithStableSlot() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();

    // setup two block productions in a row
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final UInt64 nextBlockSlot = headState.getSlot().plus(2);

    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(forkChoiceState, headState, blockSlot, nextBlockSlot);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(0)));

    assertThat(recentChainData.getCurrentSlot()).contains(blockSlot);

    // fcu call with same state but no requested block production slot should not cause a new call
    // to the EL
    notifyForkChoiceUpdatedVerifyNoNotification(forkChoiceState);
    verifyNoMoreInteractions(executionLayerChannel);

    // when attestation due arrives, the next proposer attributes should be sent
    forkChoiceUpdatedResultNotification = null;
    notifier.onAttestationsDue(blockSlot);
    assertThat(forkChoiceUpdatedResultNotification).isNotNull();
    assertThat(forkChoiceUpdatedResultNotification.payloadAttributes())
        .contains(payloadBuildingAttributes.get(1));
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    // we are still on blockSlot, with EL already notified to build nextBlockSlot
    notifyForkChoiceUpdatedVerifyNoNotification(forkChoiceState);
    verifyNoMoreInteractions(executionLayerChannel);

    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot);

    // we are asked to build nextBlockSlot, EL is already notified for that
    notifyForkChoiceUpdatedVerifyNoNotification(forkChoiceState, Optional.of(nextBlockSlot));
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void
      onForkChoiceUpdated_shouldSendNotificationWithPayloadBuildingAttributesIfConfiguredToAlwaysSendThem() {
    setUp(false, true);

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    // not proposer but should still calculate payload attributes with the default fee recipient
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlotButDoNotPrepare(
            forkChoiceState, headState, blockSlot, defaultFeeRecipient);

    final int notTheNextProposer = spec.getBeaconProposerIndex(headState, blockSlot) + 1;
    proposersDataManager.updatePreparedProposersFromPrepareBeaconProposer(
        List.of(
            new BeaconPreparableProposer(
                UInt64.valueOf(notTheNextProposer), dataStructureUtil.randomEth1Address())),
        recentChainData.getHeadSlot());

    notifyForkChoiceUpdated(
        forkChoiceState,
        Optional.empty(),
        forkChoiceUpdatedResultNotification ->
            assertThat(forkChoiceUpdatedResultNotification.payloadAttributes())
                .hasValue(payloadBuildingAttributes));
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
  }

  @Test
  void onForkChoiceUpdated_shouldNotSendNotificationWhenHeadBlockHashIsZero() {

    notifyForkChoiceUpdated(
        new ForkChoiceState(
            ForkChoiceNode.createBase(Bytes32.ZERO),
            UInt64.ZERO,
            UInt64.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            false),
        Optional.empty(),
        notification -> assertThat(notification).isNull());

    verifyNoInteractions(executionLayerChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void onForkChoiceUpdated_shouldNotSendNotificationOfOutOfOrderPayloadBuildingAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    // proposer index 1 and 0 will propose slot 2 and 3
    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(forkChoiceState, headState, blockSlot, blockSlot.plus(1));

    // current slot is 1

    // store real payload attributes and return an incomplete future
    AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualResponseA =
        new AtomicReference<>();
    SafeFuture<Optional<PayloadBuildingAttributes>> deferredResponseA = new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualResponseA.set(
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
              return deferredResponseA;
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 2

    // it is called once with no attributes. the one with attributes is pending
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    // forward to real method call
    doAnswer(InvocationOnMock::callRealMethod)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    storageSystem
        .chainUpdater()
        .setCurrentSlot(headState.getSlot().plus(1)); // set current slot to 2

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 3

    // expect a call with second attributes
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    // let the payload attributes for slot 2 return
    actualResponseA.get().propagateTo(deferredResponseA);

    // it should get ignored
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void
      onForkChoiceUpdated_shouldIgnoreStalePinnedBlockProductionPayloadAttributesAfterReplacement() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    withProposerForSlot(forkChoiceState, headState, blockSlot);

    AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualResponseA =
        new AtomicReference<>();
    SafeFuture<Optional<PayloadBuildingAttributes>> deferredResponseA = new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualResponseA.set(
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
              return deferredResponseA;
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    notifyForkChoiceUpdated(
        forkChoiceState, Optional.of(blockSlot), notification -> assertThat(notification).isNull());

    verifyNoInteractions(executionLayerChannel);

    doAnswer(InvocationOnMock::callRealMethod)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    final SignedBlockAndState newHead = storageSystem.chainUpdater().addNewBestBlock();
    final ForkChoiceState pinnedForkChoiceState = getCurrentForkChoiceState();
    final UInt64 pinnedBlockSlot = newHead.getSlot().plus(1);
    final PayloadBuildingAttributes pinnedPayloadBuildingAttributes =
        withProposerForSlot(pinnedForkChoiceState, newHead.getState(), pinnedBlockSlot);

    notifyForkChoiceUpdated(pinnedForkChoiceState, Optional.of(pinnedBlockSlot));

    verify(executionLayerChannel)
        .engineForkChoiceUpdated(
            pinnedForkChoiceState, Optional.of(pinnedPayloadBuildingAttributes));

    actualResponseA.get().propagateTo(deferredResponseA);

    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onForkChoiceUpdated_shouldSendNotificationOfOrderedPayloadBuildingAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    // proposer index 1 and 0 will propose slot 2 and 3
    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(forkChoiceState, headState, blockSlot, blockSlot.plus(1));

    // current slot is 1

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 2

    // expect attributes for slot 2
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(0)));

    notifier.onAttestationsDue(blockSlot); // calculate attributes for slot 3

    // expect attributes for slot 3
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void onForkChoiceUpdated_shouldNotSendNotificationWithOldPayloadBuildingAttributes() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    BeaconState headState = getHeadState();
    UInt64 blockSlot = headState.getSlot().plus(1); // slot 2

    final List<PayloadBuildingAttributes> payloadBuildingAttributesArr =
        withProposerForTwoSlots(forkChoiceState, headState, blockSlot, blockSlot.plus(1));

    // proposer index 1 and 0 will propose slot 2 and 3
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    // current slot is 1

    notifyForkChoiceUpdated(forkChoiceState); // calculate attributes for slot 2

    // expect attributes for slot 2
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));

    // advance chain (generate block at slot 2), we get a new forkChoiceState
    storageSystem.chainUpdater().addNewBestBlock();

    headState = getHeadState();
    blockSlot = headState.getSlot().plus(1);

    // new attributes for new state block production at slot 3
    final ForkChoiceState forkChoiceStateSlot3 = getCurrentForkChoiceState();
    final PayloadBuildingAttributes payloadBuildingAttributesSlot3 =
        withProposerForSlot(
            forkChoiceStateSlot3,
            headState,
            blockSlot,
            false,
            Optional.of(payloadBuildingAttributesArr.get(1).feeRecipient()),
            Optional.empty());

    // store real payload attributes and return an incomplete future
    AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualResponseA =
        new AtomicReference<>();
    SafeFuture<Optional<PayloadBuildingAttributes>> deferredResponseA = new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualResponseA.set(
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
              return deferredResponseA;
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    notifyForkChoiceUpdated(forkChoiceStateSlot3); // calculate attributes for slot 2

    // it is called once with no attributes. the one with attributes is pending
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceStateSlot3, Optional.empty());

    // let the payload attributes for slot 3 return
    actualResponseA.get().propagateTo(deferredResponseA);

    // expect attributes for slot 3
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceStateSlot3, Optional.of(payloadBuildingAttributesSlot3));

    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onAttestationsDue_shouldNotSendUpdateIfNotChanged() {
    final BeaconState headState = getHeadState();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionLayerChannel);

    notifier.onAttestationsDue(headState.getSlot().plus(1));
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onAttestationsDue_shouldSendUpdateIfNotChangedButResendLimitPassed() {
    final BeaconState headState = getHeadState();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);

    timeProvider.advanceTimeByMillis(RESEND_AFTER_MILLIS.longValue() + 1);

    notifier.onAttestationsDue(headState.getSlot());
    verify(executionLayerChannel, times(2))
        .engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onAttestationsDue_shouldSendUpdateEvenWithAMissedBlockIfWeAreDueToProposeNextTwo() {
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot1 = headState.getSlot().plus(1); // slot 2
    final UInt64 blockSlot2 = headState.getSlot().plus(2); // slot 3
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final List<PayloadBuildingAttributes> payloadBuildingAttributes =
        withProposerForTwoSlots(forkChoiceState, headState, blockSlot1, blockSlot2);
    // context:
    //  current slot is 1
    //  proposer index 1 proposes on slot 2
    //  proposer index 0 proposes on slot 3

    // slot is 1 and is not empty -> sending forkChoiceUpdated
    notifyForkChoiceUpdated(forkChoiceState);
    // We are proposing block on slot 2
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(0)));

    // onAttestationsDue for slot 1 (attributes for slot2)
    notifier.onAttestationsDue(headState.getSlot());
    verifyNoMoreInteractions(executionLayerChannel);

    // simulating we missed trying to produce a block: we are now in slot 2
    storageSystem
        .chainUpdater()
        .setCurrentSlot(recentChainData.getCurrentSlot().orElseThrow().plus(1));

    // Slot 2 is now assumed empty so prepare to propose in slot 3
    notifier.onAttestationsDue(recentChainData.getCurrentSlot().orElseThrow());
    verify(executionLayerChannel)
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));

    // Shouldn't resend with added payload attributes
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void shouldUseStateFromCorrectEpochToCalculateBlockProposer() {
    final int firstSlotOfNextEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);
    final UInt64 blockSlot = UInt64.valueOf(firstSlotOfNextEpoch);
    final UInt64 slotBeforeBlock = blockSlot.minus(1);
    final SignedBlockAndState headBlockAndState =
        storageSystem.chainUpdater().advanceChainUntil(firstSlotOfNextEpoch - 1);
    storageSystem.chainUpdater().updateBestBlock(headBlockAndState);
    final BeaconState headState = headBlockAndState.getState();
    storageSystem.chainUpdater().setTime(spec.computeTimeAtSlot(headState, slotBeforeBlock));
    assertThat(recentChainData.getCurrentSlot()).contains(slotBeforeBlock);

    final PayloadBuildingAttributes payloadBuildingAttributes = withProposerForSlot(blockSlot);

    notifyForkChoiceUpdated(getCurrentForkChoiceState());

    verify(executionLayerChannel)
        .engineForkChoiceUpdated(
            getCurrentForkChoiceState(), Optional.of(payloadBuildingAttributes));
  }

  @Test
  void onForkChoiceUpdated_shouldNotIncludePayloadBuildingAttributesWhileSyncing() {
    withProposerForSlot(recentChainData.getHeadSlot().plus(1));
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifier.onSyncingStatusChanged(false);

    notifyForkChoiceUpdated(forkChoiceState);

    // We're syncing so don't include payload attributes
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  @Test
  void onPreparedProposersUpdated_shouldNotIncludePayloadBuildingAttributesWhileSyncing() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    notifier.onSyncingStatusChanged(false);
    withProposerForSlot(recentChainData.getHeadSlot().plus(1));

    // Shouldn't resend with added payload attributes
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void onPreparedProposersUpdated_shouldNotCallForkChoiceUpdated() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    notifyForkChoiceUpdated(forkChoiceState);
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());

    withProposerForSlot(forkChoiceState, headState, blockSlot);
    verifyNoMoreInteractions(executionLayerChannel);
  }

  @Test
  void getPayloadId_shouldReturnLatestPayloadId() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isNotCompleted();

    // But becomes available once we receive the response
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @Test
  void getPayloadId_shouldReturnLatestPayloadIdWithValidatorRegistration() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(
            forkChoiceState,
            headState,
            blockSlot,
            true,
            Optional.empty(),
            Optional.of(createValidatorRegistration(headState, blockSlot)));

    assertThat(payloadBuildingAttributes.validatorRegistration()).isNotEmpty();

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    // Initially has no payload ID.
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isNotCompleted();

    // But becomes available once we receive the response
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyLatestPayloadIdOnWrongRoot() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(1);

    final Bytes32 wrongBlockRoot = dataStructureUtil.randomBytes32();

    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    assertThatSafeFuture(
            notifier.getPayloadId(ForkChoiceNode.createBase(wrongBlockRoot), blockSlot))
        .isCompletedExceptionally();
  }

  @Test
  @SuppressWarnings("unchecked")
  void getPayloadId_shouldWaitForPinnedBlockProductionPayloadAttributes() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    final AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualAttributesFuture =
        new AtomicReference<>();
    final SafeFuture<Optional<PayloadBuildingAttributes>> deferredAttributesFuture =
        new SafeFuture<>();
    doAnswer(
            invocation -> {
              actualAttributesFuture.set(
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
              return deferredAttributesFuture;
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(
        forkChoiceState, Optional.of(blockSlot), notification -> assertThat(notification).isNull());

    final SafeFuture<Optional<ExecutionPayloadContext>> payloadContextFuture =
        notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot);
    assertThatSafeFuture(payloadContextFuture).isNotCompleted();

    actualAttributesFuture.get().propagateTo(deferredAttributesFuture);
    assertThatSafeFuture(payloadContextFuture).isNotCompleted();

    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    assertThatSafeFuture(payloadContextFuture)
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @Test
  void getPayloadId_shouldKeepPinnedBlockProductionForSameSlotRetry() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(
            SafeFuture.completedFuture(
                createForkChoiceUpdatedResult(
                    ExecutionPayloadStatus.VALID, Optional.of(payloadId))));

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @Test
  void getPayloadId_shouldFailAfterHeadAdvancementClearsPinnedBlockProduction() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    final SafeFuture<Optional<ExecutionPayloadContext>> payloadContextFuture =
        notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot);
    assertThatSafeFuture(payloadContextFuture).isNotCompleted();

    storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
    notifyForkChoiceUpdated(getCurrentForkChoiceState());

    assertThatSafeFuture(payloadContextFuture).isCompletedExceptionally();
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedExceptionally();
  }

  @Test
  void getPayloadId_shouldKeepPinnedBlockProductionUntilAfterProposalSlot() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);

    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    final SafeFuture<Optional<ExecutionPayloadContext>> payloadContextFuture =
        notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot);
    assertThatSafeFuture(payloadContextFuture).isNotCompleted();

    notifier.onAttestationsDue(blockSlot);

    assertThatSafeFuture(payloadContextFuture).isNotCompleted();

    notifier.onAttestationsDue(blockSlot.plus(1));

    assertThatSafeFuture(payloadContextFuture).isCompletedExceptionally();
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedExceptionally();
  }

  @Test
  @SuppressWarnings("unchecked")
  void
      onForkChoiceUpdated_shouldIgnoreStalePinnedBlockProductionPayloadAttributesAfterAbandonment() {
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    withProposerForSlot(forkChoiceState, headState, blockSlot);

    final AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualAttributesFuture =
        new AtomicReference<>();
    final SafeFuture<Optional<PayloadBuildingAttributes>> deferredAttributesFuture =
        new SafeFuture<>();
    final AtomicInteger calculationCount = new AtomicInteger();
    doAnswer(
            invocation -> {
              final SafeFuture<Optional<PayloadBuildingAttributes>> actualFuture =
                  (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod();
              if (calculationCount.getAndIncrement() == 0) {
                actualAttributesFuture.set(actualFuture);
                return deferredAttributesFuture;
              }
              return new SafeFuture<Optional<PayloadBuildingAttributes>>();
            })
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    notifyForkChoiceUpdated(
        forkChoiceState, Optional.of(blockSlot), notification -> assertThat(notification).isNull());
    notifier.onAttestationsDue(blockSlot.plus(1));

    actualAttributesFuture.get().propagateTo(deferredAttributesFuture);

    verifyNoInteractions(executionLayerChannel);
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedExceptionally();
  }

  @Test
  void getPayloadId_shouldReturnEmptyWithNoForkChoiceAndNoTerminalBlock() {
    reInitializePreMerge();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = recentChainData.getHeadSlot().plus(1);

    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void getPayloadId_shouldReturnExceptionallyBeforeTheFirstForkChoiceState() {
    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    // we are post-merge, we must have a payloadId
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedExceptionally();
  }

  @Test
  void getPayloadId_preMergeShouldReturnEmptyBeforeTheFirstForkChoiceState() {
    reInitializePreMerge();

    final BeaconState headState = getHeadState();
    final UInt64 blockSlot = headState.getSlot().plus(2); // proposing slot 3
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();

    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(responseFuture);

    storageSystem.chainUpdater().setCurrentSlot(blockSlot);

    // we are pre-merge, we can continue producing blocks with no execution payload
    assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void getPayloadId_shouldAcceptTerminalBlockPayloadForPreMergeParent() {
    reInitializePreMerge();

    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
    final BeaconState headState = getHeadState();
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 blockSlot = headState.getSlot().plus(1);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        withProposerForSlot(forkChoiceState, headState, blockSlot);
    final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            any(), eq(Optional.of(payloadBuildingAttributes))))
        .thenReturn(responseFuture);

    notifier.onTerminalBlockReached(terminalBlockHash);
    notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    responseFuture.complete(
        createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    final Optional<ExecutionPayloadContext> executionPayloadContext =
        safeJoin(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot));
    assertThat(executionPayloadContext).isPresent();
    assertThat(executionPayloadContext.orElseThrow().getParentHash()).isEqualTo(terminalBlockHash);
  }

  private void notifyForkChoiceUpdated(final ForkChoiceState forkChoiceState) {
    notifyForkChoiceUpdated(forkChoiceState, Optional.empty());
  }

  private void notifyForkChoiceUpdatedVerifyNoNotification(final ForkChoiceState forkChoiceState) {
    notifyForkChoiceUpdated(
        forkChoiceState, Optional.empty(), notification -> assertThat(notification).isNull());
  }

  private void notifyForkChoiceUpdatedVerifyNoNotification(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
    notifyForkChoiceUpdated(
        forkChoiceState,
        requestedBlockProductionSlot,
        notification -> assertThat(notification).isNull());
  }

  private void notifyForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
    notifyForkChoiceUpdated(
        forkChoiceState,
        requestedBlockProductionSlot,
        notification -> assertThat(notification).isNotNull());
  }

  private void notifyForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final Optional<UInt64> requestedBlockProductionSlot,
      final Consumer<ForkChoiceUpdatedResultNotification> requirements) {
    forkChoiceUpdatedResultNotification = null;
    notifier.onForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot);
    requirements.accept(forkChoiceUpdatedResultNotification);
  }

  private PayloadBuildingAttributes withProposerForSlot(final UInt64 blockSlot) {
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState state =
        recentChainData
            .retrieveBlockState(new SlotAndBlockRoot(blockSlot, bestBlockRoot))
            .join()
            .orElseThrow();
    return withProposerForSlot(getCurrentForkChoiceState(), state, blockSlot);
  }

  private PayloadBuildingAttributes withProposerForSlotButDoNotPrepare(
      final ForkChoiceState forkChoiceState,
      final BeaconState headState,
      final UInt64 blockSlot,
      final Optional<Eth1Address> overrideFeeRecipient) {
    return withProposerForSlot(
        forkChoiceState, headState, blockSlot, false, overrideFeeRecipient, Optional.empty());
  }

  private PayloadBuildingAttributes withProposerForSlot(
      final ForkChoiceState forkChoiceState, final BeaconState headState, final UInt64 blockSlot) {
    return withProposerForSlot(
        forkChoiceState, headState, blockSlot, true, Optional.empty(), Optional.empty());
  }

  private PayloadBuildingAttributes withProposerForSlot(
      final ForkChoiceState forkChoiceState,
      final BeaconState headState,
      final UInt64 blockSlot,
      final boolean doPrepare,
      final Optional<Eth1Address> overrideFeeRecipient,
      final Optional<SignedValidatorRegistration> validatorRegistration) {
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(headState, blockSlot));
    final PayloadBuildingAttributes payloadBuildingAttributes =
        getExpectedPayloadBuildingAttributes(
            proposerIndex,
            forkChoiceState,
            headState,
            blockSlot,
            overrideFeeRecipient,
            validatorRegistration);
    if (doPrepare) {
      proposersDataManager.updatePreparedProposersFromPrepareBeaconProposer(
          List.of(
              new BeaconPreparableProposer(
                  proposerIndex, payloadBuildingAttributes.feeRecipient())),
          recentChainData.getHeadSlot());
    }
    validatorRegistration.ifPresent(
        signedValidatorRegistration ->
            safeJoin(
                proposersDataManager.updateValidatorRegistrations(
                    SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.createFromElements(
                        List.of(signedValidatorRegistration)),
                    recentChainData.getHeadSlot())));
    return payloadBuildingAttributes;
  }

  private List<PayloadBuildingAttributes> withProposerForTwoSlots(
      final ForkChoiceState forkChoiceState,
      final BeaconState headState,
      final UInt64 blockSlot1,
      final UInt64 blockSlot2) {
    final UInt64 proposerIndex1 =
        UInt64.valueOf(spec.getBeaconProposerIndex(headState, blockSlot1));
    final UInt64 proposerIndex2 =
        UInt64.valueOf(spec.getBeaconProposerIndex(headState, blockSlot2));
    final PayloadBuildingAttributes payloadBuildingAttributes1 =
        getExpectedPayloadBuildingAttributes(
            proposerIndex1,
            forkChoiceState,
            headState,
            blockSlot1,
            Optional.empty(),
            Optional.empty());
    final PayloadBuildingAttributes payloadBuildingAttributes2 =
        getExpectedPayloadBuildingAttributes(
            proposerIndex2,
            forkChoiceState,
            headState,
            blockSlot2,
            Optional.empty(),
            Optional.empty());

    if (proposerIndex1.equals(proposerIndex2)) {
      throw new UnsupportedOperationException(
          "unsupported test scenario: with same proposer for different slots");
    }
    proposersDataManager.updatePreparedProposersFromPrepareBeaconProposer(
        List.of(
            new BeaconPreparableProposer(proposerIndex1, payloadBuildingAttributes1.feeRecipient()),
            new BeaconPreparableProposer(
                proposerIndex2, payloadBuildingAttributes2.feeRecipient())),
        recentChainData.getHeadSlot());
    return List.of(payloadBuildingAttributes1, payloadBuildingAttributes2);
  }

  private PayloadBuildingAttributes getExpectedPayloadBuildingAttributes(
      final UInt64 proposerIndex,
      final ForkChoiceState forkChoiceState,
      final BeaconState headState,
      final UInt64 blockSlot,
      final Optional<Eth1Address> overrideFeeRecipient,
      final Optional<SignedValidatorRegistration> validatorRegistration) {
    final Eth1Address feeRecipient =
        overrideFeeRecipient.orElse(dataStructureUtil.randomEth1Address());
    final UInt64 timestamp = spec.computeTimeAtSlot(headState, blockSlot);
    final Bytes32 random = spec.getRandaoMix(headState, UInt64.ZERO);
    return new PayloadBuildingAttributes(
        proposerIndex,
        blockSlot,
        timestamp,
        random,
        feeRecipient,
        validatorRegistration
            .map(registration -> registration.getMessage().getGasLimit())
            .orElse(UInt64.ZERO),
        validatorRegistration,
        dataStructureUtil.randomWithdrawalList(),
        forkChoiceState.headBlock());
  }

  private ForkChoiceState getCurrentForkChoiceState() {
    final UInt64 headBlockSlot = recentChainData.getHeadSlot();
    final Bytes32 headBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 headExecutionBlockNumber =
        forkChoiceStrategy.executionBlockNumber(headBlockRoot).orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(headBlockRoot).orElseThrow();
    final Bytes32 finalizedRoot = recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy.executionBlockHash(finalizedRoot).orElseThrow();

    return new ForkChoiceState(
        ForkChoiceNode.createBase(headBlockRoot),
        headBlockSlot,
        headExecutionBlockNumber,
        headExecutionHash,
        headExecutionHash,
        finalizedExecutionHash,
        false);
  }

  private SignedValidatorRegistration createValidatorRegistration(
      final BeaconState headState, final UInt64 blockSlot) {
    final int block2Proposer = spec.getBeaconProposerIndex(headState, blockSlot);
    return dataStructureUtil.randomSignedValidatorRegistration(
        spec.getValidatorPubKey(headState, UInt64.valueOf(block2Proposer)).orElseThrow());
  }

  private ForkChoiceUpdatedResult createForkChoiceUpdatedResult(
      final ExecutionPayloadStatus status, final Optional<Bytes8> payloadId) {
    return new ForkChoiceUpdatedResult(
        PayloadStatus.create(status, Optional.empty(), Optional.empty()), payloadId);
  }

  private BeaconState getHeadState() {
    final SafeFuture<BeaconState> stateFuture = recentChainData.getBestState().orElseThrow();
    assertThat(stateFuture).isCompleted();
    return safeJoin(stateFuture);
  }
}
