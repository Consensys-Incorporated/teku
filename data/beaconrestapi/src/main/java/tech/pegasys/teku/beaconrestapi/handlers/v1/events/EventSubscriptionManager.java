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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TOPICS;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.sse.SseClient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
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
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationEventChannel;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

public class EventSubscriptionManager
    implements ChainHeadChannel,
        FinalizedCheckpointChannel,
        ReceivedBlockEventsChannel,
        ReceivedExecutionPayloadEventsChannel,
        ReceivedExecutionPayloadBidEventsChannel,
        FastConfirmationEventChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ConfigProvider configProvider;
  private final ChainDataProvider provider;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final int maxPendingEvents;
  // collection of subscribers
  private final Collection<EventSubscriber> eventSubscribers;

  // The v1 head event carries no payload status, so a Gloas empty -> full head update would produce
  // a byte identical duplicate. Only head_v2 is expected to emit a second event for the same beacon
  // block and slot (https://github.com/ethereum/beacon-APIs/pull/628), so the duplicate v1 event is
  // suppressed by comparing against the last emitted one. Only the immediately preceding event is
  // retained: reverting to an earlier head is a re-org, which must be re-emitted.
  private volatile HeadEvent.HeadData lastHeadEventData;

  public EventSubscriptionManager(
      final Spec spec,
      final NodeDataProvider nodeDataProvider,
      final ChainDataProvider chainDataProvider,
      final SyncDataProvider syncDataProvider,
      final ConfigProvider configProvider,
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels,
      final TimeProvider timeProvider,
      final int maxPendingEvents) {
    this.spec = spec;
    this.provider = chainDataProvider;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.maxPendingEvents = maxPendingEvents;
    this.eventSubscribers = new ConcurrentLinkedQueue<>();
    this.configProvider = configProvider;
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
    eventChannels.subscribe(ReceivedBlockEventsChannel.class, this);
    eventChannels.subscribe(ReceivedExecutionPayloadEventsChannel.class, this);
    eventChannels.subscribe(ReceivedExecutionPayloadBidEventsChannel.class, this);
    eventChannels.subscribe(FastConfirmationEventChannel.class, this);
    syncDataProvider.subscribeToSyncStateChanges(this::onSyncStateChange);
    nodeDataProvider.subscribeToReceivedBlobSidecar(this::onNewBlobSidecar);
    nodeDataProvider.subscribeToAttesterSlashing(this::onNewAttesterSlashing);
    nodeDataProvider.subscribeToProposerSlashing(this::onNewProposerSlashing);
    nodeDataProvider.subscribeToValidAttestations(this::onNewAttestation);
    nodeDataProvider.subscribeToNewVoluntaryExits(this::onNewVoluntaryExit);
    nodeDataProvider.subscribeToSyncCommitteeContributions(this::onSyncCommitteeContribution);
    nodeDataProvider.subscribeToNewBlsToExecutionChanges(this::onNewBlsToExecutionChange);
    nodeDataProvider.subscribeToForkChoiceUpdatedResult(this::onForkChoiceUpdatedResult);
    nodeDataProvider.subscribeToValidDataColumnSidecars(
        (dataColumnSidecar, remoteOrigin) -> onNewDataColumnSidecar(dataColumnSidecar));
    nodeDataProvider.subscribeToPayloadAttestationMessages(this::onNewPayloadAttestationMessage);
    nodeDataProvider.subscribeToProposerPreferences(this::onNewProposerPreferences);
  }

  public void registerClient(final SseClient sseClient) {
    LOG.trace("SSE client connected " + sseClient.hashCode());
    final List<String> allTopicsInContext =
        ListQueryParameterUtils.getParameterAsStringList(sseClient.ctx().queryParamMap(), TOPICS);
    final EventSubscriber subscriber =
        new EventSubscriber(
            allTopicsInContext,
            sseClient,
            () -> {
              eventSubscribers.removeIf(sub -> sub.getSseClient().equals(sseClient));
              LOG.trace("disconnected " + sseClient.hashCode());
            },
            asyncRunner,
            timeProvider,
            maxPendingEvents);
    eventSubscribers.add(subscriber);
    subscriber.sendReadyComment();
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus,
      final Optional<ReorgContext> optionalReorgContext) {
    optionalReorgContext.ifPresent(
        context -> {
          final ChainReorgEvent reorgEvent =
              new ChainReorgEvent(
                  slot,
                  slot.minus(context.getCommonAncestorSlot()),
                  context.getOldBestBlockRoot(),
                  bestBlockRoot,
                  context.getOldBestStateRoot(),
                  stateRoot,
                  configProvider.computeEpochAtSlot(slot),
                  executionOptimistic);
          notifySubscribersOfEvent(EventType.chain_reorg, reorgEvent);
        });

    final HeadEvent headEvent =
        new HeadEvent(
            slot,
            bestBlockRoot,
            stateRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot);
    if (!headEvent.getData().equals(lastHeadEventData)) {
      lastHeadEventData = headEvent.getData();
      notifySubscribersOfEvent(EventType.head, headEvent);
    }

    final HeadV2Event headV2Event =
        HeadV2Event.create(
            spec.atSlot(slot).getMilestone(),
            slot,
            bestBlockRoot,
            stateRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            payloadStatus.orElse(ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING));
    notifySubscribersOfEvent(EventType.head_v2, headV2Event);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    Optional<Bytes32> stateRoot = provider.getStateRootFromBlockRoot(checkpoint.getRoot());
    final FinalizedCheckpointEvent event =
        new FinalizedCheckpointEvent(
            checkpoint.getRoot(),
            stateRoot.orElse(Bytes32.ZERO),
            checkpoint.getEpoch(),
            fromOptimisticBlock);
    notifySubscribersOfEvent(EventType.finalized_checkpoint, event);
  }

  @Override
  public void onFastConfirmation(
      final Bytes32 confirmedRoot, final UInt64 confirmedSlot, final UInt64 currentSlot) {
    notifySubscribersOfEvent(
        EventType.fast_confirmation,
        new FastConfirmationEvent(confirmedRoot, confirmedSlot, currentSlot));
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    onNewBlockGossip(block);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    onNewBlock(block, executionOptimistic);
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {
    onNewExecutionPayloadGossip(executionPayload);
  }

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    onNewExecutionPayload(executionPayload, executionOptimistic);
    // TODO-GLOAS: potentially we can emit this event earlier when blob availability and
    // verification is complete (before importing)
    onExecutionPayloadAvailable(executionPayload);
  }

  @Override
  public void onExecutionPayloadBidValidated(final SignedExecutionPayloadBid executionPayloadBid) {
    onExecutionPayloadBid(executionPayloadBid);
  }

  protected void onNewVoluntaryExit(
      final SignedVoluntaryExit exit,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    final VoluntaryExitEvent voluntaryExitEvent = new VoluntaryExitEvent(exit);
    notifySubscribersOfEvent(EventType.voluntary_exit, voluntaryExitEvent);
  }

  protected void onNewBlsToExecutionChange(
      final SignedBlsToExecutionChange blsToExecutionChange,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      final BlsToExecutionChangeEvent blsToExecutionChangeEvent =
          new BlsToExecutionChangeEvent(blsToExecutionChange);
      notifySubscribersOfEvent(EventType.bls_to_execution_change, blsToExecutionChangeEvent);
    }
  }

  protected void onSyncCommitteeContribution(
      final SignedContributionAndProof proof,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      final ContributionAndProofEvent signedContributionAndProof =
          new ContributionAndProofEvent(proof);
      notifySubscribersOfEvent(EventType.contribution_and_proof, signedContributionAndProof);
    }
  }

  protected void onNewAttestation(final ValidatableAttestation attestation) {
    final Attestation actualAttestation = attestation.getUnconvertedAttestation();
    if (!actualAttestation.isSingleAttestation()) {
      final AttestationEvent attestationEvent = new AttestationEvent(actualAttestation);
      notifySubscribersOfEvent(EventType.attestation, attestationEvent);
    } else {
      final SingleAttestationEvent attestationEvent =
          new SingleAttestationEvent(actualAttestation.toSingleAttestationRequired());
      notifySubscribersOfEvent(EventType.single_attestation, attestationEvent);
    }
  }

  protected void onNewBlock(final SignedBeaconBlock block, final boolean executionOptimistic) {
    final BlockEvent blockEvent = new BlockEvent(block, executionOptimistic);
    notifySubscribersOfEvent(EventType.block, blockEvent);
  }

  protected void onNewBlockGossip(final SignedBeaconBlock block) {
    final BlockGossipEvent blockGossipEvent = new BlockGossipEvent(block);
    notifySubscribersOfEvent(EventType.block_gossip, blockGossipEvent);
  }

  protected void onNewBlobSidecar(final BlobSidecar blobSidecar) {
    final BlobSidecarEvent blobSidecarEvent = BlobSidecarEvent.create(spec, blobSidecar);
    notifySubscribersOfEvent(EventType.blob_sidecar, blobSidecarEvent);
  }

  protected void onNewDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarEvent dataColumnSidecarEvent =
        DataColumnSidecarEvent.create(dataColumnSidecar);
    notifySubscribersOfEvent(EventType.data_column_sidecar, dataColumnSidecarEvent);
  }

  protected void onNewAttesterSlashing(
      final AttesterSlashing attesterSlashing,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      notifySubscribersOfEvent(
          EventType.attester_slashing, new AttesterSlashingEvent(attesterSlashing));
    }
  }

  protected void onNewProposerSlashing(
      final ProposerSlashing proposerSlashing,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      notifySubscribersOfEvent(
          EventType.proposer_slashing, new ProposerSlashingEvent(proposerSlashing));
    }
  }

  protected void onForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification) {
    forkChoiceUpdatedResultNotification
        .payloadAttributes()
        .ifPresent(
            payloadAttributes -> {
              final SpecMilestone milestone =
                  spec.atSlot(payloadAttributes.proposalSlot()).getMilestone();
              final PayloadAttributesEvent payloadAttributesEvent =
                  PayloadAttributesEvent.create(
                      milestone,
                      payloadAttributes,
                      forkChoiceUpdatedResultNotification.forkChoiceState());
              notifySubscribersOfEvent(EventType.payload_attributes, payloadAttributesEvent);
            });
  }

  protected void onSyncStateChange(final SyncState syncState) {
    notifySubscribersOfEvent(EventType.sync_state, new SyncStateChangeEvent(syncState.name()));
  }

  protected void onNewExecutionPayloadGossip(
      final SignedExecutionPayloadEnvelope executionPayload) {
    final ExecutionPayloadGossipEvent executionPayloadGossipEvent =
        new ExecutionPayloadGossipEvent(executionPayload);
    notifySubscribersOfEvent(EventType.execution_payload_gossip, executionPayloadGossipEvent);
  }

  protected void onNewExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    final ExecutionPayloadEvent executionPayloadEvent =
        new ExecutionPayloadEvent(executionPayload, executionOptimistic);
    notifySubscribersOfEvent(EventType.execution_payload, executionPayloadEvent);
  }

  protected void onExecutionPayloadAvailable(
      final SignedExecutionPayloadEnvelope executionPayload) {
    final ExecutionPayloadAvailableEvent executionPayloadAvailableEvent =
        new ExecutionPayloadAvailableEvent(
            executionPayload.getSlot(), executionPayload.getBeaconBlockRoot());
    notifySubscribersOfEvent(EventType.execution_payload_available, executionPayloadAvailableEvent);
  }

  protected void onExecutionPayloadBid(final SignedExecutionPayloadBid executionPayloadBid) {
    notifySubscribersOfEvent(
        EventType.execution_payload_bid, new ExecutionPayloadBidEvent(executionPayloadBid));
  }

  protected void onNewPayloadAttestationMessage(
      final PayloadAttestationMessage payloadAttestationMessage,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      notifySubscribersOfEvent(
          EventType.payload_attestation_message,
          new PayloadAttestationMessageEvent(payloadAttestationMessage));
    }
  }

  protected void onNewProposerPreferences(
      final SignedProposerPreferences proposerPreferences,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      notifySubscribersOfEvent(
          EventType.proposer_preferences, new ProposerPreferencesEvent(proposerPreferences));
    }
  }

  private void notifySubscribersOfEvent(final EventType eventType, final Event<?> event) {
    final EventSource<?> eventSource = new EventSource<>(event);
    try {
      for (EventSubscriber subscriber : eventSubscribers) {
        subscriber.onEvent(eventType, eventSource);
      }
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize event", e);
    }
  }

  public static class EventSource<T> {
    private final Event<T> event;
    private Bytes value;

    public EventSource(final Event<T> event) {
      this.event = event;
    }

    public Bytes get() throws JsonProcessingException {
      if (value == null) {
        value =
            Bytes.wrap(
                JsonUtil.serialize(event.getData(), event.getJsonTypeDefinition()).getBytes(UTF_8));
      }
      return value;
    }
  }
}
