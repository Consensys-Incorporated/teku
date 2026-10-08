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

package tech.pegasys.teku.networking.eth2.gossip.forks;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Tracks activation of forks and updates the gossip topics that are subscribed to as the fork
 * changes.
 *
 * <p>Each fork needs to have a ForkGossip provided and it's startGossip will be called a few epochs
 * before the fork activates (or at startup if it already has. The stopGossip will be called when
 * the fork is no longer active or during shutdown.
 */
public class GossipForkManager {

  private static final Logger LOG = LogManager.getLogger();
  private static final int EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE = 2;
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final NavigableMap<UInt64, GossipForkSubscriptions> forksByActivationEpoch;
  private final Set<GossipForkSubscriptions> activeSubscriptions = new HashSet<>();
  private final IntSet currentAttestationSubnets = new IntOpenHashSet();
  private final IntSet currentSyncCommitteeSubnets = new IntOpenHashSet();
  private final IntSet currentDataColumnSidecarSubnets = new IntOpenHashSet();
  private final IntSet currentExecutionProofSubnets = new IntOpenHashSet();

  private Optional<UInt64> currentEpoch = Optional.empty();
  private boolean isHeadOptimistic;

  private GossipForkManager(
      final Spec spec,
      final RecentChainData recentChainData,
      final NavigableMap<UInt64, GossipForkSubscriptions> forksByActivationEpoch) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.forksByActivationEpoch = forksByActivationEpoch;
    this.isHeadOptimistic = recentChainData.isChainHeadOptimistic();
  }

  public static GossipForkManager.Builder builder() {
    return new GossipForkManager.Builder();
  }

  public synchronized void configureGossipForEpoch(final UInt64 newEpoch) {
    Optional<UInt64> previousEpoch = currentEpoch;
    if (previousEpoch.isPresent() && previousEpoch.get().isGreaterThanOrEqualTo(newEpoch)) {
      return;
    }
    currentEpoch = Optional.of(newEpoch);

    // Start gossip on current fork
    if (previousEpoch.isEmpty()) {
      // If this is the first call, activate the subscription at the current epoch
      // and any subscriptions for forks happening soon
      startSubscriptions(
          getSubscriptionActiveAtEpoch(newEpoch)
              .orElseThrow(() -> new IllegalStateException("No fork active at epoch " + newEpoch)));
      forksByActivationEpoch
          .subMap(newEpoch, false, newEpoch.plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE), true)
          .values()
          .forEach(this::startSubscriptions);
      return;
    }

    // Find subscriptions that are no longer required
    // First find the new forks that activated at least two epochs ago
    final Set<GossipForkSubscriptions> subscriptionsToStop =
        forksByActivationEpoch
            .subMap(
                previousEpoch.get().minusMinZero(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
                false,
                newEpoch.minusMinZero(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
                true)
            .keySet()
            .stream()
            // Deactivate the fork prior to the newly activated one if any
            .map(forksByActivationEpoch::lowerEntry)
            .filter(Objects::nonNull)
            .map(Map.Entry::getValue)
            .collect(Collectors.toSet());

    // Start subscriptions that will activate soon

    forksByActivationEpoch
        .subMap(
            previousEpoch.get().plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
            false,
            newEpoch.plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
            true)
        .values()
        .stream()
        // Don't bother starting subscriptions that will be immediately stopped
        .filter(subscription -> !subscriptionsToStop.contains(subscription))
        .forEach(this::startSubscriptions);

    subscriptionsToStop.forEach(this::stopSubscriptions);
  }

  public synchronized void stopGossip() {
    // Stop all active gossips
    activeSubscriptions.forEach(GossipForkSubscriptions::stopGossip);
    activeSubscriptions.clear();
    // Ensure we will create new active subscriptions if we are started again in the same epoch
    currentEpoch = Optional.empty();
  }

  public synchronized void onOptimisticHeadChanged(final boolean isHeadOptimistic) {
    this.isHeadOptimistic = isHeadOptimistic;
    if (isHeadOptimistic) {
      activeSubscriptions.forEach(GossipForkSubscriptions::stopGossipForOptimisticSync);
    } else {
      activeSubscriptions.forEach(
          subscriptions ->
              subscriptions.startGossip(
                  recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot(),
                  false));
    }
  }

  public void publishAttestation(final ValidatableAttestation attestation) {
    publishMessage(
        attestation.getData().getSlot(),
        attestation,
        "attestation",
        GossipForkSubscriptions::publishAttestation);
  }

  public SafeFuture<Void> publishBlock(final SignedBeaconBlock block) {
    return publishMessageWithFeedback(
        block.getSlot(), block, "block", GossipForkSubscriptions::publishBlock);
  }

  public SafeFuture<Void> publishBlobSidecar(final BlobSidecar blobSidecar) {
    return publishMessageWithFeedback(
        blobSidecar.getSlot(),
        blobSidecar,
        "blob sidecar",
        GossipForkSubscriptions::publishBlobSidecar);
  }

  public void publishDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
    publishMessage(
        dataColumnSidecar.getSlot(),
        dataColumnSidecar,
        "data column sidecar",
        GossipForkSubscriptions::publishDataColumnSidecar);
  }

  public void publishExecutionProof(final ExecutionProof executionProof) {
    // for now we don't have a slot in the message data (execution proof) to use
    // I believe it's safe to just check the current epoch
    // TODO: talk to Kev and see if it makes sense to include the slot in the message
    UInt64 slot = spec.computeStartSlotAtEpoch(spec.getCurrentEpoch(recentChainData.getStore()));
    publishMessage(
        slot, executionProof, "execution proof", GossipForkSubscriptions::publishExecutionProof);
  }

  public SafeFuture<Void> publishExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return publishMessageWithFeedback(
        executionPayload.getMessage().getSlot(),
        executionPayload,
        "execution payload",
        GossipForkSubscriptions::publishExecutionPayload);
  }

  public void publishSyncCommitteeMessage(final ValidatableSyncCommitteeMessage message) {
    publishMessage(
        message.getSlot(),
        message,
        "sync committee message",
        GossipForkSubscriptions::publishSyncCommitteeMessage);
  }

  public void publishSyncCommitteeContribution(final SignedContributionAndProof message) {
    publishMessage(
        message.getMessage().getContribution().getSlot(),
        message,
        "sync committee contribution",
        GossipForkSubscriptions::publishSyncCommitteeContribution);
  }

  public void publishProposerSlashing(final ProposerSlashing message) {
    publishMessage(
        message.getHeader1().getMessage().getSlot(),
        message,
        "proposer slashing",
        GossipForkSubscriptions::publishProposerSlashing);
  }

  public void publishAttesterSlashing(final AttesterSlashing message) {
    publishMessage(
        message.getAttestation1().getData().getSlot(),
        message,
        "attester slashing",
        GossipForkSubscriptions::publishAttesterSlashing);
  }

  public void publishVoluntaryExit(final SignedVoluntaryExit message) {
    final SpecMilestone currentMilestone =
        spec.atEpoch(spec.getCurrentEpoch(recentChainData.getStore())).getMilestone();
    final UInt64 publishingSlot;
    if (currentMilestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      publishingSlot =
          spec.computeStartSlotAtEpoch(spec.getCurrentEpoch(recentChainData.getStore()));
    } else {
      publishingSlot = spec.computeStartSlotAtEpoch(message.getMessage().getEpoch());
    }
    publishMessage(
        publishingSlot, message, "voluntary exit", GossipForkSubscriptions::publishVoluntaryExit);
  }

  public void publishSignedBlsToExecutionChanges(final SignedBlsToExecutionChange message) {
    publishMessage(
        spec.computeStartSlotAtEpoch(currentEpoch.orElseThrow()),
        message,
        "signed bls to execution change",
        GossipForkSubscriptions::publishSignedBlsToExecutionChangeMessage);
  }

  public void publishPayloadAttestationMessage(final PayloadAttestationMessage message) {
    publishMessage(
        message.getData().getSlot(),
        message,
        "payload attestation message",
        GossipForkSubscriptions::publishPayloadAttestationMessage);
  }

  public void publishExecutionPayloadBid(final SignedExecutionPayloadBid message) {
    publishMessage(
        message.getMessage().getSlot(),
        message,
        "execution payload bid",
        GossipForkSubscriptions::publishExecutionPayloadBid);
  }

  public void publishProposerPreferences(final SignedProposerPreferences message) {
    publishMessage(
        message.getMessage().getProposalSlot(),
        message,
        "proposer preferences",
        GossipForkSubscriptions::publishProposerPreferences);
  }

  public void publishLightClientFinalityUpdate(final LightClientFinalityUpdate message) {
    publishMessage(
        message.getAttestedHeader().getBeacon().getSlot(),
        message,
        "light client finality update",
        GossipForkSubscriptions::publishLightClientFinalityUpdate);
  }

  public void publishLightClientOptimisticUpdate(final LightClientOptimisticUpdate message) {
    publishMessage(
        message.getAttestedHeader().getBeacon().getSlot(),
        message,
        "light client optimistic update",
        GossipForkSubscriptions::publishLightClientOptimisticUpdate);
  }

  private synchronized <T> void publishMessage(
      final UInt64 slot,
      final T message,
      final String type,
      final BiConsumer<GossipForkSubscriptions, T> publisher) {
    getSubscriptionActiveAtSlot(slot)
        .filter(this::isActive)
        .ifPresentOrElse(
            subscription -> publisher.accept(subscription, message),
            () ->
                LOG.warn(
                    "Not publishing {} because no gossip subscriptions are active for slot {}",
                    type,
                    slot));
  }

  private synchronized <T> SafeFuture<Void> publishMessageWithFeedback(
      final UInt64 slot,
      final T message,
      final String type,
      final BiFunction<GossipForkSubscriptions, T, SafeFuture<Void>> publisher) {
    final Optional<GossipForkSubscriptions> gossipForkSubscriptions =
        getSubscriptionActiveAtSlot(slot).filter(this::isActive);

    if (gossipForkSubscriptions.isEmpty()) {
      LOG.warn(
          "Not publishing {} because no gossip subscriptions are active for slot {}", type, slot);
      return SafeFuture.COMPLETE;
    }

    return publisher.apply(gossipForkSubscriptions.get(), message);
  }

  public synchronized void subscribeToAttestationSubnetId(final int subnetId) {
    if (currentAttestationSubnets.add(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.subscribeToAttestationSubnetId(subnetId));
    }
  }

  public synchronized void unsubscribeFromAttestationSubnetId(final int subnetId) {
    if (currentAttestationSubnets.remove(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.unsubscribeFromAttestationSubnetId(subnetId));
    }
  }

  public synchronized void subscribeToSyncCommitteeSubnetId(final int subnetId) {
    if (currentSyncCommitteeSubnets.add(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.subscribeToSyncCommitteeSubnet(subnetId));
    }
  }

  public synchronized void unsubscribeFromSyncCommitteeSubnetId(final int subnetId) {
    if (currentSyncCommitteeSubnets.remove(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.unsubscribeFromSyncCommitteeSubnet(subnetId));
    }
  }

  public void subscribeToDataColumnSidecarSubnetId(final int subnetId) {
    if (currentDataColumnSidecarSubnets.add(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.subscribeToDataColumnSidecarSubnet(subnetId));
    }
  }

  public void unsubscribeFromDataColumnSidecarSubnetId(final int subnetId) {
    if (currentDataColumnSidecarSubnets.remove(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.unsubscribeFromDataColumnSidecarSubnet(subnetId));
    }
  }

  public void subscribeToExecutionProofSubnetId(final int subnetId) {
    if (currentExecutionProofSubnets.add(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.subscribeToExecutionProofSubnet(subnetId));
    }
  }

  public void unsubscribeFromExecutionProofSubnetId(final int subnetId) {
    if (currentExecutionProofSubnets.remove(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.unsubscribeFromExecutionProofSubnet(subnetId));
    }
  }

  private boolean isActive(final GossipForkSubscriptions subscriptions) {
    return activeSubscriptions.contains(subscriptions);
  }

  private void startSubscriptions(final GossipForkSubscriptions subscription) {
    if (activeSubscriptions.add(subscription)) {
      subscription.startGossip(
          recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot(),
          isHeadOptimistic);
      currentAttestationSubnets.forEach(subscription::subscribeToAttestationSubnetId);
      currentSyncCommitteeSubnets.forEach(subscription::subscribeToSyncCommitteeSubnet);
      currentDataColumnSidecarSubnets.forEach(subscription::subscribeToDataColumnSidecarSubnet);
      currentExecutionProofSubnets.forEach(subscription::subscribeToExecutionProofSubnet);
    }
  }

  private void stopSubscriptions(final GossipForkSubscriptions subscriptions) {
    if (activeSubscriptions.remove(subscriptions)) {
      subscriptions.stopGossip();
    }
  }

  private Optional<GossipForkSubscriptions> getSubscriptionActiveAtSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    return getSubscriptionActiveAtEpoch(epoch);
  }

  private Optional<GossipForkSubscriptions> getSubscriptionActiveAtEpoch(final UInt64 epoch) {
    return Optional.ofNullable(forksByActivationEpoch.floorEntry(epoch)).map(Map.Entry::getValue);
  }

  public static class Builder {

    private Spec spec;
    private RecentChainData recentChainData;
    private final NavigableMap<UInt64, GossipForkSubscriptions> forksByActivationEpoch =
        new TreeMap<>();

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder recentChainData(final RecentChainData recentChainData) {
      this.recentChainData = recentChainData;
      return this;
    }

    public Builder fork(final GossipForkSubscriptions forkSubscriptions) {
      return fork(forkSubscriptions, false);
    }

    public Builder bpoFork(final GossipForkSubscriptions forkSubscriptions) {
      return fork(forkSubscriptions, true);
    }

    private Builder fork(
        final GossipForkSubscriptions forkSubscriptions, final boolean skipReplaceCheck) {
      final UInt64 activationEpoch = forkSubscriptions.getActivationEpoch();
      if (!skipReplaceCheck) {
        checkState(
            !forksByActivationEpoch.containsKey(activationEpoch),
            "Can not schedule two forks to activate at the same epoch");
      }
      forksByActivationEpoch.put(activationEpoch, forkSubscriptions);
      return this;
    }

    public GossipForkManager build() {
      checkNotNull(spec, "Must supply spec");
      checkNotNull(recentChainData, "Must supply recentChainData");
      checkState(!forksByActivationEpoch.isEmpty(), "Must specify at least one fork");
      return new GossipForkManager(spec, recentChainData, forksByActivationEpoch);
    }
  }
}
