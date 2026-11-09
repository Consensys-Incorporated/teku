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

package tech.pegasys.teku.validator.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class DvtAttestationAggregations {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final UInt64 epoch;
  private final Map<BeaconCommitteeSelectionProof, SafeFuture<BLSSignature>> pendingRequests =
      new ConcurrentHashMap<>();
  private final int expectedDutiesCount;
  private final AtomicBoolean submitted = new AtomicBoolean(false);
  private volatile boolean activated = false;

  public DvtAttestationAggregations(
      final ValidatorApiChannel validatorApiChannel,
      final UInt64 epoch,
      final int expectedDutiesCount) {
    this.validatorApiChannel = validatorApiChannel;
    this.epoch = epoch;
    this.expectedDutiesCount = expectedDutiesCount;
  }

  public SafeFuture<BLSSignature> getCombinedSelectionProofFuture(
      final int validatorIndex, final UInt64 slot, final BLSSignature partialProof) {

    LOG.debug(
        "Created pending request for DVT attestation aggregation proof (validator_id={}, slot={})",
        validatorIndex,
        slot);

    final BeaconCommitteeSelectionProof request =
        new BeaconCommitteeSelectionProof.Builder()
            .validatorIndex(validatorIndex)
            .slot(slot)
            .selectionProof(partialProof.toBytesCompressed().toHexString())
            .build();

    if (submitted.get()) {
      return SafeFuture.failedFuture(alreadySubmittedOrCancelledException());
    }

    final SafeFuture<BLSSignature> future = new SafeFuture<>();
    pendingRequests.put(request, future);

    if (submitted.get()) {
      pendingRequests.remove(request, future);
      future.completeExceptionally(alreadySubmittedOrCancelledException());
    } else if (activated && pendingRequests.size() >= expectedDutiesCount) {
      maybeSubmit();
    }

    return future;
  }

  private CancellationException alreadySubmittedOrCancelledException() {
    return new CancellationException(
        "DVT attestation aggregation already submitted or cancelled for epoch " + epoch);
  }

  public void activate() {
    activated = true;
    if (!pendingRequests.isEmpty()) {
      maybeSubmit();
    }
  }

  public void cancel() {
    if (submitted.compareAndSet(false, true)) {
      completeAllPendingFuturesExceptionally(
          new CancellationException(
              "DVT attestation aggregation duties rescheduled for epoch " + epoch));
    }
  }

  private void maybeSubmit() {
    if (submitted.compareAndSet(false, true)) {
      submitBatchRequests();
    }
  }

  private void submitBatchRequests() {
    validatorApiChannel
        .getBeaconCommitteeSelectionProof(pendingRequests.keySet().stream().toList())
        .thenAccept(
            response ->
                response.ifPresentOrElse(
                    this::handleBeaconCommitteeSelectionProofsResponse, this::handleEmptyResponse))
        .exceptionally(unexpectedErrorHandler())
        .finishStackTrace();
  }

  private void handleBeaconCommitteeSelectionProofsResponse(
      final List<BeaconCommitteeSelectionProof> proofsFromMiddleware) {
    LOG.debug("Processing response from middleware for {} requests", proofsFromMiddleware.size());

    pendingRequests.forEach(
        (request, future) -> {
          final Optional<BeaconCommitteeSelectionProof> completeProof =
              proofsFromMiddleware.stream().filter(matchingRequest(request)).findFirst();

          completeProof.ifPresentOrElse(
              resp -> {
                LOG.debug(
                    "Completing request for DVT attestation aggregation proof (validator_id={}, slot={})",
                    request.getValidatorIndex(),
                    request.getSlot());

                future.complete(resp.getSelectionProofSignature());
              },
              () ->
                  future.completeExceptionally(
                      new RuntimeException(
                          "No matching complete proof from DVT middleware for this request")));
        });
    pendingRequests.clear();
  }

  private static Predicate<BeaconCommitteeSelectionProof> matchingRequest(
      final BeaconCommitteeSelectionProof request) {
    return proof ->
        request.getValidatorIndex() == proof.getValidatorIndex()
            && request.getSlot().equals(proof.getSlot());
  }

  private void handleEmptyResponse() {
    LOG.warn(
        "Received empty response from DVT middleware for epoch {}. This will impact aggregation duties.",
        epoch);
    completeAllPendingFuturesExceptionally(
        new RuntimeException("Empty response from DVT middleware for epoch " + epoch));
  }

  private Function<Throwable, Void> unexpectedErrorHandler() {
    return (ex) -> {
      final String errorMsg = "Error getting DVT attestation aggregation complete proof";
      LOG.warn(errorMsg);
      LOG.debug(errorMsg, ex);

      completeAllPendingFuturesExceptionally(new RuntimeException(errorMsg, ex));
      return null;
    };
  }

  private void completeAllPendingFuturesExceptionally(final Throwable cause) {
    pendingRequests
        .values()
        .forEach(
            future -> {
              if (!future.isDone()) {
                future.completeExceptionally(cause);
              }
            });
    pendingRequests.clear();
  }
}
