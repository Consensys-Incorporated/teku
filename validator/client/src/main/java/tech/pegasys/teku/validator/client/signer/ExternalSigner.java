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

package tech.pegasys.teku.validator.client.signer;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PRECONDITION_FAILED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.validator.api.signer.AggregationSlotWrapper;
import tech.pegasys.teku.validator.api.signer.RandaoRevealWrapper;
import tech.pegasys.teku.validator.api.signer.SignType;
import tech.pegasys.teku.validator.api.signer.SyncAggregatorSelectionDataWrapper;
import tech.pegasys.teku.validator.api.signer.SyncCommitteeMessageWrapper;

public class ExternalSigner implements Signer {
  public static final String EXTERNAL_SIGNER_ENDPOINT = "/api/v1/eth2/sign";
  public static final String FORK_INFO = "fork_info";
  private final URL signingServiceUrl;
  private final URI uri;
  private final Duration timeout;
  private final Spec spec;
  private final HttpClient httpClient;
  private final ThrottlingTaskQueueWithPriority taskQueue;
  private final SigningRootUtil signingRootUtil;
  private final SchemaDefinitionCache schemaDefinitionCache;

  private final Counter successCounter;
  private final Counter failedCounter;
  private final Counter timeoutCounter;

  public ExternalSigner(
      final Spec spec,
      final HttpClient httpClient,
      final URL signingServiceUrl,
      final BLSPublicKey blsPublicKey,
      final Duration timeout,
      final ThrottlingTaskQueueWithPriority taskQueue,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.httpClient = httpClient;
    this.signingServiceUrl = signingServiceUrl;
    this.timeout = timeout;
    this.taskQueue = taskQueue;
    this.signingRootUtil = new SigningRootUtil(spec);

    final LabelledMetric<Counter> labelledCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            "external_signer_requests_total",
            "Completed external signer counts",
            "result");
    successCounter = labelledCounter.labels("success");
    failedCounter = labelledCounter.labels("failed");
    timeoutCounter = labelledCounter.labels("timeout");
    this.schemaDefinitionCache = new SchemaDefinitionCache(spec);
    try {
      uri =
          signingServiceUrl
              .toURI()
              .resolve(
                  EXTERNAL_SIGNER_ENDPOINT + "/" + blsPublicKey.toBytesCompressed().toString());
    } catch (final URISyntaxException e) {
      throw new RuntimeException("Unable to determine signer URI", e);
    }
  }

  @Override
  public void delete() {}

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return sign(
        signingRootUtil.signingRootForRandaoReveal(epoch, forkInfo),
        SignType.RANDAO_REVEAL,
        Map.of(
            SignType.RANDAO_REVEAL.getName(), new RandaoRevealWrapper(epoch), FORK_INFO, forkInfo),
        slashableGenericMessage("randao reveal"));
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    final ExternalSignerBlockRequestProvider blockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);

    return sign(
        signingRootUtil.signingRootForSignBlock(block, forkInfo),
        blockRequestProvider.getSignType(),
        blockRequestProvider.getBlockMetadata(Map.of(FORK_INFO, forkInfo)),
        slashableBlockMessage(block.getSlot()));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return sign(
        signingRootUtil.signingRootForSignAttestationData(attestationData, forkInfo),
        SignType.ATTESTATION,
        Map.of(SignType.ATTESTATION.getName(), attestationData, FORK_INFO, forkInfo),
        slashableAttestationMessage(attestationData));
  }

  private void recordMetrics(final BLSSignature result, final Throwable error) {
    if (error != null) {
      if (Throwables.getRootCause(error) instanceof HttpTimeoutException) {
        timeoutCounter.inc();
      } else {
        failedCounter.inc();
      }
    } else {
      successCounter.inc();
    }
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return taskQueue.queueTask(
        () ->
            sign(
                signingRootUtil.signingRootForSignAggregationSlot(slot, forkInfo),
                SignType.AGGREGATION_SLOT,
                Map.of(
                    SignType.AGGREGATION_SLOT.getName(),
                    new AggregationSlotWrapper(slot),
                    FORK_INFO,
                    forkInfo),
                slashableGenericMessage("aggregation slot")),
        true);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return sign(
        signingRootUtil.signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo),
        SignType.AGGREGATE_AND_PROOF,
        Map.of(SignType.AGGREGATE_AND_PROOF.getName(), aggregateAndProof, FORK_INFO, forkInfo),
        slashableGenericMessage("aggregate and proof"));
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return sign(
        signingRootUtil.signingRootForSignVoluntaryExit(voluntaryExit, forkInfo),
        SignType.VOLUNTARY_EXIT,
        Map.of(SignType.VOLUNTARY_EXIT.getName(), voluntaryExit, FORK_INFO, forkInfo),
        slashableGenericMessage("voluntary exit"));
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final ForkInfo forkInfo) {
    return signingRootFromSyncCommitteeUtils(
            slot,
            utils ->
                utils.getSyncCommitteeMessageSigningRoot(
                    beaconBlockRoot, spec.computeEpochAtSlot(slot), forkInfo))
        .thenCompose(
            signingRoot ->
                sign(
                    signingRoot,
                    SignType.SYNC_COMMITTEE_MESSAGE,
                    Map.of(
                        SignType.SYNC_COMMITTEE_MESSAGE.getName(),
                        new SyncCommitteeMessageWrapper(beaconBlockRoot, slot),
                        FORK_INFO,
                        forkInfo),
                    slashableGenericMessage("sync committee message")));
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      final SyncAggregatorSelectionData selectionData, final ForkInfo forkInfo) {
    return signingRootFromSyncCommitteeUtils(
            selectionData.getSlot(),
            utils -> utils.getSyncAggregatorSelectionDataSigningRoot(selectionData, forkInfo))
        .thenCompose(
            signingRoot ->
                sign(
                    signingRoot,
                    SignType.SYNC_COMMITTEE_SELECTION_PROOF,
                    Map.of(
                        SignType.SYNC_AGGREGATOR_SELECTION_DATA.getName(),
                        new SyncAggregatorSelectionDataWrapper(
                            selectionData.getSlot(), selectionData.getSubcommitteeIndex()),
                        FORK_INFO,
                        forkInfo),
                    slashableGenericMessage("sync committee selection proof")));
  }

  @Override
  public SafeFuture<BLSSignature> signContributionAndProof(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    return signingRootFromSyncCommitteeUtils(
            contributionAndProof.getContribution().getSlot(),
            utils -> utils.getContributionAndProofSigningRoot(contributionAndProof, forkInfo))
        .thenCompose(
            signingRoot ->
                sign(
                    signingRoot,
                    SignType.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF,
                    Map.of(
                        SignType.CONTRIBUTION_AND_PROOF.getName(),
                        contributionAndProof,
                        FORK_INFO,
                        forkInfo),
                    slashableGenericMessage("sync committee contribution and proof")));
  }

  @Override
  public SafeFuture<BLSSignature> signValidatorRegistration(
      final ValidatorRegistration validatorRegistration) {
    return taskQueue.queueTask(
        () ->
            sign(
                signingRootUtil.signingRootForValidatorRegistration(validatorRegistration),
                SignType.VALIDATOR_REGISTRATION,
                Map.of(SignType.VALIDATOR_REGISTRATION.getName(), validatorRegistration),
                slashableGenericMessage("validator registration")));
  }

  // TODO-GLOAS: https://github.com/ethereum/remote-signing-api/issues/23
  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadBid(
      final ExecutionPayloadBid bid, final ForkInfo forkInfo) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelope envelope, final ForkInfo forkInfo) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
  }

  @Override
  public SafeFuture<BLSSignature> signPayloadAttestationData(
      final PayloadAttestationData payloadAttestationData, final ForkInfo forkInfo) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
  }

  @Override
  public SafeFuture<BLSSignature> signProposerPreferences(
      final ProposerPreferences proposerPreferences, final ForkInfo forkInfo) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
  }

  @Override
  public SafeFuture<BLSSignature> signBuilderRequestAuth(
      final BuilderRequestAuth builderRequestAuth) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
  }

  @Override
  public Optional<URL> getSigningServiceUrl() {
    return Optional.of(signingServiceUrl);
  }

  private SafeFuture<Bytes> signingRootFromSyncCommitteeUtils(
      final UInt64 slot, final Function<SyncCommitteeUtil, Bytes> createSigningRoot) {
    return SafeFuture.of(() -> createSigningRoot.apply(spec.getSyncCommitteeUtilRequired(slot)));
  }

  private SafeFuture<BLSSignature> sign(
      final Bytes signingRoot,
      final SignType type,
      final Map<String, Object> metadata,
      final Supplier<String> slashableMessage) {

    return SafeFuture.of(
            () -> {
              final String requestBody = createSigningRequestBody(signingRoot, type, metadata);
              final HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(uri)
                      .timeout(timeout)
                      .header("Content-Type", "application/json")
                      .POST(BodyPublishers.ofString(requestBody))
                      .build();

              return httpClient
                  .sendAsync(request, BodyHandlers.ofString())
                  .handleAsync(
                      (response, error) ->
                          this.getBlsSignatureResponder(
                              uri, type, response, error, slashableMessage));
            })
        .whenComplete(this::recordMetrics);
  }

  private String createSigningRequestBody(
      final Bytes signingRoot, final SignType type, final Map<String, Object> metadata) {
    try {
      final SigningRequestBody request = new SigningRequestBody(signingRoot, type, metadata);
      final SchemaDefinitions schemaDefinitions =
          getSpecVersionFromForkInfo(Optional.ofNullable((ForkInfo) metadata.get(FORK_INFO)));
      return JsonUtil.serialize(request, request.getJsonTypeDefinition(schemaDefinitions));
    } catch (final JsonProcessingException e) {
      throw new ExternalSignerException("Unable to create external signing request", e);
    }
  }

  private SchemaDefinitions getSpecVersionFromForkInfo(final Optional<ForkInfo> maybeForkInfo) {
    final Optional<Bytes4> maybeFork = maybeForkInfo.map(f -> f.getFork().getCurrentVersion());
    if (maybeFork.isEmpty()) {
      return schemaDefinitionCache.getSchemaDefinition(
          schemaDefinitionCache.getSupportedMilestones().getFirst());
    }
    final Bytes4 currentFork = maybeFork.orElseThrow();
    return spec.getEnabledMilestones().stream()
        .filter(f -> f.getFork().getCurrentVersion().equals(currentFork))
        .map(f -> schemaDefinitionCache.getSchemaDefinition(f.getSpecMilestone()))
        .findFirst()
        .orElse(
            schemaDefinitionCache.getSchemaDefinition(
                schemaDefinitionCache.getSupportedMilestones().getFirst()));
  }

  private BLSSignature getBlsSignatureResponder(
      final URI url,
      final SignType type,
      final HttpResponse<String> response,
      final Throwable throwable,
      final Supplier<String> slashableMessage) {
    if (throwable != null) {
      throw new ExternalSignerException(url, type, throwable.getMessage(), throwable);
    }

    if (response.statusCode() == SC_PRECONDITION_FAILED) {
      throw new ExternalSignerException(slashableMessage.get());
    }

    if (response.statusCode() != SC_OK) {
      throw new ExternalSignerException(
          url, type, "Invalid response status code: " + response.statusCode());
    }

    try {
      final String returnedContentType = response.headers().firstValue("Content-Type").orElse("");
      if (returnedContentType.startsWith("application/json")) {
        return JsonUtil.parse(response.body(), SigningResponseBody.getJsonTypeDefinition())
            .signature();
      }
      final String signatureHexStr = response.body();
      final Bytes signature = Bytes.fromHexString(signatureHexStr);
      return BLSSignature.fromBytesCompressed(signature);
    } catch (final IllegalArgumentException | JsonProcessingException e) {
      throw new ExternalSignerException(
          url, type, "Returned an invalid signature: " + e.getMessage(), e);
    }
  }

  @VisibleForTesting
  static Supplier<String> slashableBlockMessage(final UInt64 slot) {
    return () ->
        String.format(
            "External signer refused to sign block at slot %s as it may violate a slashing condition",
            slot);
  }

  @VisibleForTesting
  static Supplier<String> slashableAttestationMessage(final AttestationData attestationData) {
    return () ->
        "External signer refused to sign attestation at slot "
            + attestationData.getSlot()
            + " with source epoch "
            + attestationData.getSource().getEpoch()
            + " and target epoch "
            + attestationData.getTarget().getEpoch()
            + " because it may violate a slashing condition";
  }

  @VisibleForTesting
  static Supplier<String> slashableGenericMessage(final String type) {
    return () ->
        "External signer refused to sign " + type + " because it may violate a slashing condition";
  }
}
