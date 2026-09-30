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

package tech.pegasys.teku.api;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache.TRACKED_EPOCHS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.inclusionlist.SignedInclusionListListener;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool.NewBlobSidecarSubscriber;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarManager;
import tech.pegasys.teku.statetransition.datacolumns.ValidDataColumnSidecarsListener;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.forkchoice.RegisteredValidatorInfo;
import tech.pegasys.teku.statetransition.inclusionlist.InclusionListManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class NodeDataProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool;
  private final SyncCommitteeContributionPool syncCommitteeContributionPool;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final AttestationManager attestationManager;
  private final ActiveValidatorChannel activeValidatorChannel;
  private final boolean isLivenessTrackingEnabled;
  private final ProposersDataManager proposersDataManager;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final RecentChainData recentChainData;
  private final DataColumnSidecarManager dataColumnSidecarManager;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final PayloadAttestationPool payloadAttestationPool;
  private final ProposerPreferencesManager proposerPreferencesManager;
  private final InclusionListManager inclusionListManager;
  private final Spec spec;

  public NodeDataProvider(
      final AggregatingAttestationPool attestationPool,
      final InclusionListManager inclusionListManager,
      final OperationPool<AttesterSlashing> attesterSlashingsPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      final SyncCommitteeContributionPool syncCommitteeContributionPool,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final AttestationManager attestationManager,
      final boolean isLivenessTrackingEnabled,
      final ActiveValidatorChannel activeValidatorChannel,
      final ProposersDataManager proposersDataManager,
      final ForkChoiceNotifier forkChoiceNotifier,
      final RecentChainData recentChainData,
      final DataColumnSidecarManager dataColumnSidecarManager,
      final CustodyGroupCountManager custodyGroupCountManager,
      final PayloadAttestationPool payloadAttestationPool,
      final ProposerPreferencesManager proposerPreferencesManager,
      final Spec spec) {
    this.attestationPool = attestationPool;
    this.inclusionListManager = inclusionListManager;
    this.attesterSlashingPool = attesterSlashingsPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.blsToExecutionChangePool = blsToExecutionChangePool;
    this.syncCommitteeContributionPool = syncCommitteeContributionPool;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.attestationManager = attestationManager;
    this.activeValidatorChannel = activeValidatorChannel;
    this.isLivenessTrackingEnabled = isLivenessTrackingEnabled;
    this.proposersDataManager = proposersDataManager;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.recentChainData = recentChainData;
    this.dataColumnSidecarManager = dataColumnSidecarManager;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.payloadAttestationPool = payloadAttestationPool;
    this.proposerPreferencesManager = proposerPreferencesManager;
    this.spec = spec;
  }

  public List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {
    return attestationPool.getAttestations(maybeSlot, maybeCommitteeIndex);
  }

  public SafeFuture<Optional<SszBitvector>> getInclusionListBits(
      final UInt64 proposalSlot, final Bytes32 parentRoot) {
    return inclusionListManager.getInclusionListBits(proposalSlot, parentRoot);
  }

  public List<SignedInclusionList> getInclusionLists(
      final UInt64 slot, final Bytes32 dependentRoot, final SszBitvector committeeIndices) {
    return inclusionListManager.getInclusionLists(slot, dependentRoot, committeeIndices);
  }

  public ObjectAndMetaData<List<Attestation>> getAttestationsAndMetaData(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {
    return lookupMetaData(
        attestationPool.getAttestations(maybeSlot, maybeCommitteeIndex), maybeSlot);
  }

  public Set<UInt64> getCustodyColumnIndices() {
    return custodyGroupCountManager.getCustodyColumnIndices();
  }

  private ObjectAndMetaData<List<Attestation>> lookupMetaData(
      final List<Attestation> attestations, final Optional<UInt64> maybeSlot) {
    final UInt64 slot =
        getSlot(attestations, attestation -> attestation.getData().getSlot(), maybeSlot);
    return new ObjectAndMetaData<>(
        attestations, spec.atSlot(slot).getMilestone(), false, false, false);
  }

  private <T> UInt64 getSlot(
      final List<T> items, final Function<T, UInt64> itemToSlot, final Optional<UInt64> maybeSlot) {
    return maybeSlot
        .or(() -> items.stream().findFirst().map(itemToSlot))
        .orElseGet(() -> recentChainData.getCurrentSlot().orElse(UInt64.ZERO));
  }

  public List<AttesterSlashing> getAttesterSlashings() {
    return new ArrayList<>(attesterSlashingPool.getAll());
  }

  public ObjectAndMetaData<List<AttesterSlashing>> getAttesterSlashingsAndMetaData() {
    final List<AttesterSlashing> attesterSlashings = new ArrayList<>(attesterSlashingPool.getAll());
    final UInt64 slot =
        getSlot(
            attesterSlashings,
            attesterSlashing -> attesterSlashing.getAttestation1().getData().getSlot(),
            Optional.empty());
    return new ObjectAndMetaData<>(
        attesterSlashings, spec.atSlot(slot).getMilestone(), false, false, false);
  }

  public List<ProposerSlashing> getProposerSlashings() {
    return new ArrayList<>(proposerSlashingPool.getAll());
  }

  public List<SignedVoluntaryExit> getVoluntaryExits() {
    return new ArrayList<>(voluntaryExitPool.getAll());
  }

  public ObjectAndMetaData<List<PayloadAttestation>> getPayloadAttestationsAndMetaData(
      final Optional<UInt64> maybeSlot) {
    final List<PayloadAttestation> payloadAttestations =
        recentChainData
            .getBestState()
            .map(SafeFuture::join)
            .map(payloadAttestationPool::getPayloadAttestations)
            .orElse(List.of());
    final List<PayloadAttestation> filteredPayloadAttestations =
        maybeSlot
            .map(
                slot ->
                    payloadAttestations.stream()
                        .filter(
                            payloadAttestation ->
                                payloadAttestation.getData().getSlot().equals(slot))
                        .toList())
            .orElse(payloadAttestations);
    final UInt64 slot =
        getSlot(
            filteredPayloadAttestations,
            payloadAttestation -> payloadAttestation.getData().getSlot(),
            maybeSlot);
    return new ObjectAndMetaData<>(
        filteredPayloadAttestations, spec.atSlot(slot).getMilestone(), false, false, false);
  }

  public SafeFuture<InternalValidationResult> postVoluntaryExit(final SignedVoluntaryExit exit) {
    final Optional<SafeFuture<BeaconState>> maybeFutureState = recentChainData.getBestState();

    return maybeFutureState
        .map(
            beaconStateSafeFuture ->
                beaconStateSafeFuture
                    .thenApply(
                        state -> {
                          final SszList<Validator> validators = state.getValidators();
                          final UInt64 validatorId = exit.getValidatorId();
                          if (validatorId.isGreaterThanOrEqualTo(validators.size())) {
                            return InternalValidationResult.reject(
                                "Validator index %s was not found", exit.getValidatorId());
                          } else if (validators
                              .get(validatorId.intValue())
                              .getExitEpoch()
                              .isLessThan(FAR_FUTURE_EPOCH)) {
                            return InternalValidationResult.reject(
                                "Validator index %s is already exiting (or exited)",
                                exit.getValidatorId());
                          }
                          return InternalValidationResult.ACCEPT;
                        })
                    .thenApply(
                        result -> {
                          if (result.isAccept()) {
                            try {
                              // if we can't add this in a reasonable time we should fail.
                              return voluntaryExitPool.addLocal(exit).get(5, TimeUnit.SECONDS);
                            } catch (InterruptedException
                                | ExecutionException
                                | TimeoutException e) {
                              return InternalValidationResult.reject(
                                  "Failed to add voluntary exit for validator index %s to pool: %s",
                                  exit.getValidatorId(), e.getMessage());
                            }
                          }
                          return result;
                        }))
        .orElseGet(() -> SafeFuture.failedFuture(new ServiceUnavailableException()));
  }

  public SafeFuture<InternalValidationResult> postAttesterSlashing(
      final AttesterSlashing slashing) {
    return attesterSlashingPool.addLocal(slashing);
  }

  public SafeFuture<InternalValidationResult> postProposerSlashing(
      final ProposerSlashing slashing) {
    return proposerSlashingPool.addLocal(slashing);
  }

  public List<SignedBlsToExecutionChange> getBlsToExecutionChanges(
      final Optional<Boolean> locallySubmitted) {
    if (locallySubmitted.isPresent() && locallySubmitted.get()) {
      return new ArrayList<>(blsToExecutionChangePool.getLocallySubmitted());
    }
    return new ArrayList<>(blsToExecutionChangePool.getAll());
  }

  public SafeFuture<List<SubmitDataError>> postBlsToExecutionChanges(
      final List<SignedBlsToExecutionChange> blsToExecutionChanges) {
    final List<SafeFuture<Optional<SubmitDataError>>> maybeFutureErrors = new ArrayList<>();

    for (int i = 0; i < blsToExecutionChanges.size(); i++) {
      maybeFutureErrors.add(addBlsToExecutionChange(i, blsToExecutionChanges.get(i)));
    }

    return SafeFuture.collectAll(maybeFutureErrors.stream())
        .thenApply(entries -> entries.stream().flatMap(Optional::stream).toList());
  }

  private SafeFuture<Optional<SubmitDataError>> addBlsToExecutionChange(
      final int index, final SignedBlsToExecutionChange signedBlsToExecutionChange) {
    return blsToExecutionChangePool
        .addLocal(signedBlsToExecutionChange)
        .thenApply(
            internalValidationResult -> {
              if (internalValidationResult.isAccept()) {
                return Optional.empty();
              }
              LOG.debug(
                  "BlsToExecutionChange failed status {} {}",
                  internalValidationResult.code(),
                  internalValidationResult.getDescription().orElse(""));
              return Optional.of(
                  new SubmitDataError(
                      UInt64.valueOf(index),
                      internalValidationResult
                          .getDescription()
                          .orElse(
                              "Invalid BlsToExecutionChange, it will never pass validation so it's rejected")));
            });
  }

  public void subscribeToReceivedBlobSidecar(final NewBlobSidecarSubscriber listener) {
    blockBlobSidecarsTrackersPool.subscribeNewBlobSidecar(listener);
  }

  public void subscribeToValidDataColumnSidecars(final ValidDataColumnSidecarsListener listener) {
    dataColumnSidecarManager.subscribeToValidDataColumnSidecars(listener);
  }

  public void subscribeToAttesterSlashing(
      final OperationAddedSubscriber<AttesterSlashing> listener) {
    attesterSlashingPool.subscribeOperationAdded(listener);
  }

  public void subscribeToProposerSlashing(
      final OperationAddedSubscriber<ProposerSlashing> listener) {
    proposerSlashingPool.subscribeOperationAdded(listener);
  }

  public void subscribeToValidAttestations(final ProcessedAttestationListener listener) {
    attestationManager.subscribeToAllValidAttestations(listener);
  }

  public void subscribeToNewVoluntaryExits(
      final OperationAddedSubscriber<SignedVoluntaryExit> listener) {
    voluntaryExitPool.subscribeOperationAdded(listener);
  }

  public void subscribeToNewBlsToExecutionChanges(
      final OperationAddedSubscriber<SignedBlsToExecutionChange> listener) {
    blsToExecutionChangePool.subscribeOperationAdded(listener);
  }

  public void subscribeToSyncCommitteeContributions(
      final OperationAddedSubscriber<SignedContributionAndProof> listener) {
    syncCommitteeContributionPool.subscribeOperationAdded(listener);
  }

  public void subscribeToForkChoiceUpdatedResult(final ForkChoiceUpdatedResultSubscriber listener) {
    forkChoiceNotifier.subscribeToForkChoiceUpdatedResult(listener);
  }

  public void subscribeToPayloadAttestationMessages(
      final OperationAddedSubscriber<PayloadAttestationMessage> listener) {
    payloadAttestationPool.subscribeOperationAdded(listener);
  }

  public void subscribeToProposerPreferences(
      final OperationAddedSubscriber<SignedProposerPreferences> listener) {
    proposerPreferencesManager.subscribeOperationAdded(listener);
  }

  public void subscribeToNewInclusionList(final SignedInclusionListListener listener) {
    inclusionListManager.subscribeToInclusionLists(listener);
  }

  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorLiveness(
      final List<UInt64> validatorIndices,
      final UInt64 epoch,
      final Optional<UInt64> maybeCurrentEpoch) {
    if (!isLivenessTrackingEnabled) {
      return SafeFuture.failedFuture(
          new BadRequestException(
              "Validator liveness tracking is not enabled on this beacon node, cannot service request"));
    }
    // if no validator indices were requested, that's a bad request.
    if (validatorIndices.isEmpty()) {
      return SafeFuture.failedFuture(
          new BadRequestException("No validator indices posted in validator liveness request"));
    }
    if (maybeCurrentEpoch.isEmpty()) {
      return SafeFuture.failedFuture(new ServiceUnavailableException());
    }

    final UInt64 currentEpoch = maybeCurrentEpoch.get();
    if (currentEpoch.isLessThan(epoch)) {
      return SafeFuture.failedFuture(
          new BadRequestException(
              String.format(
                  "Current node epoch %s, cannot check liveness for a future epoch %s",
                  currentEpoch, epoch)));
    } else if (currentEpoch.minusMinZero(TRACKED_EPOCHS).isGreaterThan(epoch)) {
      return SafeFuture.failedFuture(
          new BadRequestException(
              String.format(
                  "Current node epoch %s, cannot check liveness for an epoch (%s) more than %d in the past",
                  currentEpoch, epoch, TRACKED_EPOCHS)));
    }

    return activeValidatorChannel
        .validatorsLiveAtEpoch(validatorIndices, epoch)
        .thenApply(
            validatorLivenessMap -> {
              final List<ValidatorLivenessAtEpoch> livenessAtEpochs = new ArrayList<>();
              validatorLivenessMap.forEach(
                  (validatorIndex, liveness) ->
                      livenessAtEpochs.add(new ValidatorLivenessAtEpoch(validatorIndex, liveness)));
              return Optional.of(livenessAtEpochs);
            });
  }

  public Map<UInt64, PreparedProposerInfo> getPreparedProposerInfo() {
    return proposersDataManager.getPreparedProposerInfo();
  }

  public Map<UInt64, RegisteredValidatorInfo> getValidatorRegistrationInfo() {
    return proposersDataManager.getValidatorRegistrationInfo();
  }

  public boolean isProposerDefaultFeeRecipientDefined() {
    return proposersDataManager.isProposerDefaultFeeRecipientDefined();
  }
}
