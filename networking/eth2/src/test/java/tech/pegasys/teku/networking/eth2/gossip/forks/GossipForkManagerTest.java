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

package tech.pegasys.teku.networking.eth2.gossip.forks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

class GossipForkManagerTest {
  private static final Bytes32 GENESIS_VALIDATORS_ROOT = Bytes32.fromHexString("0x12345678446687");
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final DataStructureUtil gloasDataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  @BeforeEach
  void setUp() {
    reset(recentChainData);
    when(recentChainData.getGenesisData())
        .thenReturn(
            Optional.of(new GenesisData(UInt64.valueOf(134234134L), GENESIS_VALIDATORS_ROOT)));
  }

  @Test
  void shouldThrowExceptionIfNoForksRegistered() {
    assertThatThrownBy(() -> builder().build()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowExceptionIfNoForkActiveAtStartingEpoch() {
    final GossipForkManager manager = builder().fork(forkAtEpoch(6)).build();
    assertThatThrownBy(() -> manager.configureGossipForEpoch(UInt64.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No fork active at epoch 0");
  }

  @Test
  void shouldActivateCurrentForkOnStart() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkManager manager = builder().fork(currentForkSubscriptions).build();
    manager.configureGossipForEpoch(UInt64.ZERO);

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT, false);
  }

  @Test
  void shouldActivateCurrentAndNextForkOnStartIfNextForkWithinTwoEpochs() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions);

    manager.configureGossipForEpoch(UInt64.valueOf(3));

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(nextForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT, false);
  }

  @Test
  void shouldActivateMultipleFutureForksIfTheyAreWithinTwoEpochs() {
    final GossipForkSubscriptions currentFork = forkAtEpoch(0);
    final GossipForkSubscriptions nextFork = forkAtEpoch(2);
    final GossipForkSubscriptions laterFork = forkAtEpoch(3);
    final GossipForkSubscriptions tooLateFork = forkAtEpoch(4);

    managerForForks(currentFork, nextFork, laterFork, tooLateFork)
        .configureGossipForEpoch(UInt64.ONE);

    verify(currentFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(nextFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(laterFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(tooLateFork, never()).startGossip(any(), anyBoolean());
  }

  @Test
  void shouldNotStartNextForkIfNotWithinTwoEpochs() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions);

    manager.configureGossipForEpoch(UInt64.valueOf(2));

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(nextForkSubscriptions, never()).startGossip(any(), anyBoolean());
  }

  @Test
  void shouldStopActiveSubscriptionsOnStop() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkSubscriptions laterForkSubscriptions = forkAtEpoch(10);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions, laterForkSubscriptions);
    manager.configureGossipForEpoch(UInt64.valueOf(3));

    manager.stopGossip();

    verify(currentForkSubscriptions).stopGossip();
    verify(nextForkSubscriptions).stopGossip();
    verify(laterForkSubscriptions, never()).stopGossip();
  }

  @Test
  void shouldResubscribeAfterStopping() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkSubscriptions laterForkSubscriptions = forkAtEpoch(10);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions, laterForkSubscriptions);
    manager.configureGossipForEpoch(UInt64.valueOf(3));
    verify(currentForkSubscriptions, times(1)).startGossip(any(), anyBoolean());
    verify(nextForkSubscriptions, times(1)).startGossip(any(), anyBoolean());

    manager.stopGossip();

    verify(currentForkSubscriptions).stopGossip();
    verify(nextForkSubscriptions).stopGossip();

    manager.configureGossipForEpoch(UInt64.valueOf(3));
    verify(currentForkSubscriptions, times(2)).startGossip(any(), anyBoolean());
    verify(nextForkSubscriptions, times(2)).startGossip(any(), anyBoolean());
  }

  @Test
  void shouldStopForkTwoEpochsAfterTheNextOneActivates() {
    final GossipForkSubscriptions genesisFork = forkAtEpoch(0);
    final GossipForkSubscriptions newFork = forkAtEpoch(5);

    final GossipForkManager manager = managerForForks(genesisFork, newFork);
    manager.configureGossipForEpoch(UInt64.valueOf(4));

    verify(genesisFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(newFork).startGossip(GENESIS_VALIDATORS_ROOT, false);

    // Shouldn't make any changes in epochs 5 or 6
    manager.configureGossipForEpoch(UInt64.valueOf(5));
    manager.configureGossipForEpoch(UInt64.valueOf(6));
    verify(genesisFork, times(1)).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(newFork, times(1)).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(genesisFork, never()).stopGossip();
    verify(newFork, never()).stopGossip();

    // Should stop the genesis fork at epoch 7
    manager.configureGossipForEpoch(UInt64.valueOf(7));
    verify(genesisFork).stopGossip();
    verify(newFork, never()).stopGossip();
  }

  @Test
  void shouldProcessForkChangesWhenEpochsAreMissed() {
    // We may skip epochs if we fall behind and skip slots to catch up
    final GossipForkSubscriptions genesisFork = forkAtEpoch(0);
    final GossipForkSubscriptions newFork = forkAtEpoch(3);
    final GossipForkSubscriptions laterFork = forkAtEpoch(6);

    final GossipForkManager manager = managerForForks(genesisFork, newFork, laterFork);

    // Should start the genesis subscriptions on first call
    manager.configureGossipForEpoch(UInt64.ZERO);
    verify(genesisFork).startGossip(GENESIS_VALIDATORS_ROOT, false);

    // Jump to epoch 10 and should wind up with only laterFork active
    manager.configureGossipForEpoch(UInt64.valueOf(10));
    verify(genesisFork).stopGossip();

    // No point starting newFork as it's already due to be stopped
    verify(newFork, never()).startGossip(GENESIS_VALIDATORS_ROOT, false);
    verify(newFork, never()).stopGossip();

    verify(laterFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
  }

  @Test
  void shouldPublishAttestationToForkForAttestationsSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);

    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidatableAttestation firstForkAttestation =
        ValidatableAttestation.fromValidator(spec, dataStructureUtil.randomAttestation(0));
    final ValidatableAttestation secondForkAttestation =
        ValidatableAttestation.fromValidator(
            spec,
            dataStructureUtil.randomAttestation(
                spec.computeStartSlotAtEpoch(UInt64.ONE).longValue()));
    final ValidatableAttestation thirdForkAttestation =
        ValidatableAttestation.fromValidator(
            spec,
            dataStructureUtil.randomAttestation(
                spec.computeStartSlotAtEpoch(UInt64.valueOf(2)).longValue()));

    manager.publishAttestation(firstForkAttestation);
    verify(firstFork).publishAttestation(firstForkAttestation);
    verify(secondFork, never()).publishAttestation(firstForkAttestation);
    verify(thirdFork, never()).publishAttestation(firstForkAttestation);

    manager.publishAttestation(secondForkAttestation);
    verify(firstFork, never()).publishAttestation(secondForkAttestation);
    verify(secondFork).publishAttestation(secondForkAttestation);
    verify(thirdFork, never()).publishAttestation(secondForkAttestation);

    manager.publishAttestation(thirdForkAttestation);
    verify(firstFork, never()).publishAttestation(thirdForkAttestation);
    verify(secondFork, never()).publishAttestation(thirdForkAttestation);
    verify(thirdFork).publishAttestation(thirdForkAttestation);
  }

  @Test
  void shouldNotPublishAttestationsToForksThatAreNotActive() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);

    final GossipForkManager manager = managerForForks(firstFork, secondFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidatableAttestation attestation =
        ValidatableAttestation.fromValidator(
            spec,
            dataStructureUtil.randomAttestation(
                spec.computeStartSlotAtEpoch(secondFork.getActivationEpoch()).longValue()));

    manager.publishAttestation(attestation);

    verify(firstFork, never()).publishAttestation(attestation);
    verify(secondFork, never()).publishAttestation(attestation);
  }

  @Test
  void shouldPublishBlockToForkForBlockSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);

    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final SignedBeaconBlock firstForkBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final SignedBeaconBlock secondForkBlock =
        dataStructureUtil.randomSignedBeaconBlock(spec.computeStartSlotAtEpoch(UInt64.ONE));
    final SignedBeaconBlock thirdForkBlock =
        dataStructureUtil.randomSignedBeaconBlock(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));

    safeJoin(manager.publishBlock(firstForkBlock));
    verify(firstFork).publishBlock(firstForkBlock);
    verify(secondFork, never()).publishBlock(firstForkBlock);
    verify(thirdFork, never()).publishBlock(firstForkBlock);

    safeJoin(manager.publishBlock(secondForkBlock));
    verify(firstFork, never()).publishBlock(secondForkBlock);
    verify(secondFork).publishBlock(secondForkBlock);
    verify(thirdFork, never()).publishBlock(secondForkBlock);

    safeJoin(manager.publishBlock(thirdForkBlock));
    verify(firstFork, never()).publishBlock(thirdForkBlock);
    verify(secondFork, never()).publishBlock(thirdForkBlock);
    verify(thirdFork).publishBlock(thirdForkBlock);
  }

  @Test
  void shouldPublishExecutionPayloadBidToForkForBidSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);
    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedExecutionPayloadBid bid =
        gloasDataStructureUtil.randomSignedExecutionPayloadBid(
            gloasDataStructureUtil.randomExecutionPayloadBid(slot, UInt64.ONE));

    manager.publishExecutionPayloadBid(bid);

    verify(firstFork, never()).publishExecutionPayloadBid(bid);
    verify(secondFork).publishExecutionPayloadBid(bid);
    verify(thirdFork, never()).publishExecutionPayloadBid(bid);
  }

  @Test
  void shouldPublishSyncCommitteeMessageToForkForSignatureSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);

    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidatableSyncCommitteeMessage firstForkMessage =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(0));
    final ValidatableSyncCommitteeMessage secondForkMessage =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(spec.computeStartSlotAtEpoch(UInt64.ONE)));
    final ValidatableSyncCommitteeMessage thirdForkMessage =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(
                spec.computeStartSlotAtEpoch(UInt64.valueOf(2))));

    manager.publishSyncCommitteeMessage(firstForkMessage);
    verify(firstFork).publishSyncCommitteeMessage(firstForkMessage);
    verify(secondFork, never()).publishSyncCommitteeMessage(firstForkMessage);
    verify(thirdFork, never()).publishSyncCommitteeMessage(firstForkMessage);

    manager.publishSyncCommitteeMessage(secondForkMessage);
    verify(firstFork, never()).publishSyncCommitteeMessage(secondForkMessage);
    verify(secondFork).publishSyncCommitteeMessage(secondForkMessage);
    verify(thirdFork, never()).publishSyncCommitteeMessage(secondForkMessage);

    manager.publishSyncCommitteeMessage(thirdForkMessage);
    verify(firstFork, never()).publishSyncCommitteeMessage(thirdForkMessage);
    verify(secondFork, never()).publishSyncCommitteeMessage(thirdForkMessage);
    verify(thirdFork).publishSyncCommitteeMessage(thirdForkMessage);
  }

  @Test
  void shouldNotPublishSyncCommitteeMessagesToForksThatAreNotActive() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);

    final GossipForkManager manager = managerForForks(firstFork, secondFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(
                spec.computeStartSlotAtEpoch(secondFork.getActivationEpoch())));

    manager.publishSyncCommitteeMessage(message);

    verify(firstFork, never()).publishSyncCommitteeMessage(message);
    verify(secondFork, never()).publishSyncCommitteeMessage(message);
  }

  @Test
  void shouldPublishVoluntaryExitOnCapella() {
    final Spec specCapella = TestSpecFactory.createMinimalCapella();
    final GossipForkSubscriptions capellaFork = forkAtEpoch(0);
    final GossipForkManager.Builder builder =
        GossipForkManager.builder().recentChainData(recentChainData).spec(specCapella);
    Stream.of(capellaFork).forEach(builder::fork);
    final GossipForkManager manager = builder.build();

    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getGenesisTime()).thenReturn(UInt64.ZERO);
    when(store.getTimeSeconds()).thenReturn(UInt64.ONE);

    final VoluntaryExit voluntaryExit = new VoluntaryExit(UInt64.ZERO, UInt64.ONE);
    final SignedVoluntaryExit capellaVoluntaryExit =
        new SignedVoluntaryExit(voluntaryExit, dataStructureUtil.randomSignature());

    manager.configureGossipForEpoch(UInt64.ZERO);

    manager.publishVoluntaryExit(capellaVoluntaryExit);
    verify(capellaFork).publishVoluntaryExit(capellaVoluntaryExit);
  }

  @Test
  void shouldPublishCapellaVoluntaryExitAfterCapella() {
    final Spec specDeneb = TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.ONE);
    final GossipForkSubscriptions capellaFork = forkAtEpoch(0);
    final GossipForkSubscriptions denebFork = forkAtEpoch(1);
    final GossipForkManager.Builder builder =
        GossipForkManager.builder().recentChainData(recentChainData).spec(specDeneb);
    Stream.of(capellaFork, denebFork).forEach(builder::fork);
    final GossipForkManager manager = builder.build();

    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getGenesisTime()).thenReturn(UInt64.ZERO);
    when(store.getTimeSeconds()).thenReturn(UInt64.valueOf(9000));
    assertThat(specDeneb.getCurrentEpoch(store)).isGreaterThanOrEqualTo(UInt64.valueOf(3));

    final VoluntaryExit voluntaryExit = new VoluntaryExit(UInt64.ZERO, UInt64.ONE);
    final SignedVoluntaryExit capellaVoluntaryExit =
        new SignedVoluntaryExit(voluntaryExit, dataStructureUtil.randomSignature());
    assertEquals(
        SpecMilestone.CAPELLA,
        specDeneb.atEpoch(capellaVoluntaryExit.getMessage().getEpoch()).getMilestone());

    // Deneb
    // Previous subscriptions are stopped in 2 epochs after fork transition
    manager.configureGossipForEpoch(UInt64.valueOf(3));

    manager.publishVoluntaryExit(capellaVoluntaryExit);
    verify(capellaFork, never()).publishVoluntaryExit(capellaVoluntaryExit);
    verify(denebFork).publishVoluntaryExit(capellaVoluntaryExit);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldSubscribeToAttestationSubnetsPriorToStarting(final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions fork = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(fork);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.verifySubscribe(fork, 1);
    subscriptionType.verifySubscribe(fork, 2);
    subscriptionType.verifySubscribe(fork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldSubscribeToCurrentAttestationSubnetsWhenNewForkActivates(
      final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    manager.configureGossipForEpoch(UInt64.valueOf(8));

    verify(secondFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
    subscriptionType.verifySubscribe(secondFork, 1);
    subscriptionType.verifySubscribe(secondFork, 2);
    subscriptionType.verifySubscribe(secondFork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldSubscribeActiveForksToAttestationSubnets(final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    subscriptionType.verifySubscribe(firstFork, 1);
    subscriptionType.verifySubscribe(firstFork, 2);
    subscriptionType.verifySubscribe(firstFork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldUnsubscribeActiveForksFromAttestationSubnets(final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.verifySubscribe(firstFork, 1);

    subscriptionType.unsubscribe(manager, 1);
    subscriptionType.verifyUnsubscribe(firstFork, 1);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldNotSubscribeToSubnetThatWasUnsubscribedPriorToStarting(
      final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions fork = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(fork);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    subscriptionType.unsubscribe(manager, 2);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.verifySubscribe(fork, 1);
    subscriptionType.verifyNotSubscribed(fork, 2);
    subscriptionType.verifySubscribe(fork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldNotSubscribeToSubnetThatWasUnsubscribedWhenNewForkActivates(
      final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    subscriptionType.unsubscribe(manager, 2);

    manager.configureGossipForEpoch(UInt64.valueOf(8));

    verify(secondFork).startGossip(GENESIS_VALIDATORS_ROOT, false);
    subscriptionType.verifySubscribe(secondFork, 1);
    subscriptionType.verifyNotSubscribed(secondFork, 2);
    subscriptionType.verifySubscribe(secondFork, 5);
  }

  @Test
  void shouldStopAndRestartNonOptimisticSyncTopics() {
    final GossipForkSubscriptions subscriptions = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(subscriptions);

    manager.configureGossipForEpoch(UInt64.ZERO);
    verify(subscriptions, times(1)).startGossip(GENESIS_VALIDATORS_ROOT, false);

    manager.onOptimisticHeadChanged(true);
    verify(subscriptions).stopGossipForOptimisticSync();

    manager.onOptimisticHeadChanged(false);
    verify(subscriptions, times(2)).startGossip(GENESIS_VALIDATORS_ROOT, false);
  }

  @Test
  void shouldIgnoreOptimisticHeadChangesWhenNotStarted() {
    final GossipForkSubscriptions subscriptions = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(subscriptions);

    manager.onOptimisticHeadChanged(true);
    verify(subscriptions, never()).stopGossipForOptimisticSync();

    manager.onOptimisticHeadChanged(false);
    verify(subscriptions, never()).startGossip(any(), anyBoolean());
  }

  @Test
  void shouldStartSubscriptionsInOptimisticSyncMode() {
    when(recentChainData.isChainHeadOptimistic()).thenReturn(true);

    final GossipForkSubscriptions subscriptions = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(subscriptions);

    manager.configureGossipForEpoch(UInt64.ZERO);

    verify(subscriptions).startGossip(GENESIS_VALIDATORS_ROOT, true);
  }

  @Test
  void shouldStartSubscriptionsInNonOptimisticSyncModeWhenSyncStateChangedBeforeStart() {
    when(recentChainData.isChainHeadOptimistic()).thenReturn(true);

    final GossipForkSubscriptions subscriptions = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(subscriptions);

    manager.onOptimisticHeadChanged(false);
    manager.configureGossipForEpoch(UInt64.ZERO);

    verify(subscriptions).startGossip(GENESIS_VALIDATORS_ROOT, false);
  }

  @Test
  void shouldOverrideForkWithBpo() {
    final Spec spec = TestSpecFactory.createMinimalWithFuluForkEpoch(UInt64.valueOf(1));
    final GossipForkSubscriptions electraFork = forkAtEpoch(0);
    final GossipForkSubscriptions fuluFork = forkAtEpoch(1);
    final GossipForkManager.Builder builder =
        GossipForkManager.builder().recentChainData(recentChainData).spec(spec);
    Stream.of(electraFork, fuluFork).forEach(builder::fork);
    assertThatThrownBy(() -> builder.fork(fuluFork))
        .hasMessageContaining("Can not schedule two forks to activate at the same epoch")
        .isInstanceOf(IllegalStateException.class);
    final GossipForkSubscriptions fuluBpo1Fork = forkAtEpoch(1);
    final GossipForkSubscriptions fuluBpo2Fork = forkAtEpoch(2);
    Stream.of(fuluBpo1Fork, fuluBpo2Fork).forEach(builder::bpoFork);

    final GossipForkManager manager = builder.build();
    manager.configureGossipForEpoch(UInt64.valueOf(1));
    manager.configureGossipForEpoch(UInt64.valueOf(2));
  }

  private GossipForkSubscriptions forkAtEpoch(final long epoch) {
    final GossipForkSubscriptions subscriptions =
        mock(GossipForkSubscriptions.class, "subscriptionsForEpoch" + epoch);
    when(subscriptions.getActivationEpoch()).thenReturn(UInt64.valueOf(epoch));
    when(subscriptions.publishBlock(any())).thenReturn(SafeFuture.COMPLETE);
    when(subscriptions.publishBlobSidecar(any())).thenReturn(SafeFuture.COMPLETE);
    return subscriptions;
  }

  private GossipForkManager managerForForks(final GossipForkSubscriptions... subscriptions) {
    final GossipForkManager.Builder builder = builder();
    Stream.of(subscriptions).forEach(builder::fork);
    return builder.build();
  }

  private GossipForkManager.Builder builder() {
    return GossipForkManager.builder().recentChainData(recentChainData).spec(spec);
  }

  static Stream<SubscriptionType> subnetSubscriptionTypes() {
    return Stream.of(
        new SubscriptionType(
            "attestation",
            GossipForkManager::subscribeToAttestationSubnetId,
            GossipForkManager::unsubscribeFromAttestationSubnetId,
            (manager, subnetId) -> verify(manager).subscribeToAttestationSubnetId(subnetId),
            (manager, subnetId) ->
                verify(manager, never()).subscribeToAttestationSubnetId(subnetId),
            (manager, subnetId) -> verify(manager).unsubscribeFromAttestationSubnetId(subnetId)),
        new SubscriptionType(
            "sync committee",
            GossipForkManager::subscribeToSyncCommitteeSubnetId,
            GossipForkManager::unsubscribeFromSyncCommitteeSubnetId,
            (manager, subnetId) -> verify(manager).subscribeToSyncCommitteeSubnet(subnetId),
            (manager, subnetId) ->
                verify(manager, never()).subscribeToSyncCommitteeSubnet(subnetId),
            (manager, subnetId) -> verify(manager).unsubscribeFromSyncCommitteeSubnet(subnetId)));
  }

  private static class SubscriptionType {
    private final String type;
    private final BiConsumer<GossipForkManager, Integer> subscribeToSubnet;
    private final BiConsumer<GossipForkManager, Integer> unsubscribeFromSubnet;
    private final BiConsumer<GossipForkSubscriptions, Integer> verifySubscribeToSubnet;
    private final BiConsumer<GossipForkSubscriptions, Integer> verifyNotSubscribedToSubnet;
    private final BiConsumer<GossipForkSubscriptions, Integer> verifyUnsubscribeFromSubnet;

    private SubscriptionType(
        final String type,
        final BiConsumer<GossipForkManager, Integer> subscribeToSubnet,
        final BiConsumer<GossipForkManager, Integer> unsubscribeFromSubnet,
        final BiConsumer<GossipForkSubscriptions, Integer> verifySubscribeToSubnet,
        final BiConsumer<GossipForkSubscriptions, Integer> verifyNotSubscribedToSubnet,
        final BiConsumer<GossipForkSubscriptions, Integer> verifyUnsubscribeFromSubnet) {
      this.type = type;
      this.subscribeToSubnet = subscribeToSubnet;
      this.unsubscribeFromSubnet = unsubscribeFromSubnet;
      this.verifySubscribeToSubnet = verifySubscribeToSubnet;
      this.verifyNotSubscribedToSubnet = verifyNotSubscribedToSubnet;
      this.verifyUnsubscribeFromSubnet = verifyUnsubscribeFromSubnet;
    }

    public void subscribe(final GossipForkManager manager, final int subnetId) {
      subscribeToSubnet.accept(manager, subnetId);
    }

    public void unsubscribe(final GossipForkManager manager, final int subnetId) {
      unsubscribeFromSubnet.accept(manager, subnetId);
    }

    public void verifySubscribe(final GossipForkSubscriptions fork, final int subnetId) {
      verifySubscribeToSubnet.accept(fork, subnetId);
    }

    public void verifyNotSubscribed(final GossipForkSubscriptions fork, final int subnetId) {
      verifyNotSubscribedToSubnet.accept(fork, subnetId);
    }

    public void verifyUnsubscribe(final GossipForkSubscriptions fork, final int subnetId) {
      verifyUnsubscribeFromSubnet.accept(fork, subnetId);
    }

    @Override
    public String toString() {
      return type;
    }
  }
}
