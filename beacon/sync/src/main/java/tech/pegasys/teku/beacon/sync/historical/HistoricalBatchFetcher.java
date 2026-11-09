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

package tech.pegasys.teku.beacon.sync.historical;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBlockHashCalculator;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/** Fetches a target batch of blocks from a peer. */
public class HistoricalBatchFetcher {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_REQUESTS = 2;

  private final StorageUpdateChannel storageUpdateChannel;
  private final Eth2Peer peer;
  private final UInt64 maxSlot;
  private final Bytes32 lastBlockRoot;
  private final UInt64 batchSize;
  private final int maxRequests;
  private final Optional<SignedBeaconBlock> nextBlock;

  private final Spec spec;
  private final BlobSidecarManager blobSidecarManager;
  private final SafeFuture<SignedBeaconBlock> future = new SafeFuture<>();
  private final Deque<SignedBeaconBlock> blocksToImport = new ConcurrentLinkedDeque<>();
  private final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlotToImport =
      new ConcurrentHashMap<>();
  private final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>
      blindedExecutionPayloadsByBlockRootToImport = new ConcurrentHashMap<>();
  private Optional<UInt64> maybeEarliestBlobSidecarSlot = Optional.empty();
  private final AtomicInteger requestCount = new AtomicInteger(0);
  private final AsyncBLSSignatureVerifier signatureVerificationService;
  private final CombinedChainDataClient chainDataClient;

  /**
   * @param storageUpdateChannel The storage channel where finalized blocks will be imported
   * @param peer The peer to request blocks from
   * @param maxSlot The maxSlot to pull
   * @param lastBlockRoot The block root that defines the last block in our batch
   * @param batchSize The number of blocks to sync (assuming all slots are filled)
   * @param nextBlock The block immediately following the batch (used to validate execution payload
   *     delivery for the last block in the batch), or empty if unavailable
   */
  public HistoricalBatchFetcher(
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final Eth2Peer peer,
      final UInt64 maxSlot,
      final Bytes32 lastBlockRoot,
      final UInt64 batchSize,
      final Optional<SignedBeaconBlock> nextBlock) {
    this(
        storageUpdateChannel,
        signatureVerifier,
        chainDataClient,
        spec,
        blobSidecarManager,
        peer,
        maxSlot,
        lastBlockRoot,
        batchSize,
        nextBlock,
        MAX_REQUESTS);
  }

  @VisibleForTesting
  HistoricalBatchFetcher(
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final Eth2Peer peer,
      final UInt64 maxSlot,
      final Bytes32 lastBlockRoot,
      final UInt64 batchSize,
      final Optional<SignedBeaconBlock> nextBlock,
      final int maxRequests) {
    this.storageUpdateChannel = storageUpdateChannel;
    this.signatureVerificationService = signatureVerifier;
    this.chainDataClient = chainDataClient;
    this.spec = spec;
    this.blobSidecarManager = blobSidecarManager;
    this.peer = peer;
    this.maxSlot = maxSlot;
    this.lastBlockRoot = lastBlockRoot;
    this.batchSize = batchSize;
    this.nextBlock = nextBlock;
    this.maxRequests = maxRequests;
  }

  /**
   * Fetch the batch of blocks (and blob sidecars if required) up to {@link #maxSlot}, save them to
   * the database, and return the new value for the earliest block.
   *
   * @return A future that resolves with the earliest block pulled and saved.
   */
  public SafeFuture<SignedBeaconBlock> run() {
    SafeFuture.asyncDoWhile(this::requestBlocksAndBlobSidecarsByRange)
        .thenCompose(
            __ -> {
              if (blocksToImport.isEmpty()) {
                // If we've received no blocks, this range of blocks may be empty
                // Try to look up the next block by root
                return requestBlockByRoot();
              } else {
                return SafeFuture.COMPLETE;
              }
            })
        .thenCompose(__ -> complete())
        .finish(this::handleRequestError);

    return future;
  }

  private SafeFuture<Void> complete() {
    final Optional<SignedBeaconBlock> latestBlock = getLatestReceivedBlock();

    if (latestBlockCompletesBatch(latestBlock)) {
      LOG.trace("Import batch of {} blocks", blocksToImport.size());
      return importBatch();
    } else if (latestBlockShouldCompleteBatch(latestBlock)) {
      // Nothing left to request but the batch is incomplete
      // It appears our peer is on a different chain
      LOG.warn("Received invalid blocks from a different chain. Disconnecting peer: " + peer);
      peer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).finishStackTrace();
      throw new InvalidResponseException("Received invalid blocks from a different chain");
    } else {
      // We haven't completed the batch, and we've hit our request limit
      throw new InvalidResponseException("Failed to deliver full batch");
    }
  }

  private void handleRequestError(final Throwable throwable) {
    final Throwable rootCause = Throwables.getRootCause(throwable);
    if (rootCause instanceof InvalidResponseException) {
      // Disconnect misbehaving peer
      LOG.debug("Received invalid response from peer. Disconnecting: {}", peer, throwable);
      peer.disconnectCleanly(DisconnectReason.REMOTE_FAULT).finishStackTrace();
      future.completeExceptionally(throwable);
    } else {
      LOG.debug(
          "Error processing historical batch from peer {} targeting block root {}.",
          peer.getId(),
          lastBlockRoot,
          throwable);
      future.completeExceptionally(throwable);
    }
  }

  private boolean batchIsComplete() {
    return getLatestReceivedBlock()
        .map(SignedBeaconBlock::getRoot)
        .map(r -> r.equals(lastBlockRoot))
        .orElse(false);
  }

  private SafeFuture<Boolean> requestBlocksAndBlobSidecarsByRange() {
    final RequestParameters requestParams = calculateRequestParams();
    if (requestParams.getCount().equals(UInt64.ZERO)) {
      // Nothing left to request
      return SafeFuture.completedFuture(false);
    }

    final UInt64 endSlot = requestParams.getEndSlot();

    final RequestManager requestManager =
        new RequestManager(
            lastBlockRoot,
            getLatestReceivedBlock(),
            blocksToImport::addLast,
            this::processBlobSidecar,
            this::processExecutionPayload);

    LOG.trace(
        "Request {} blocks from {} to {}",
        requestParams.getCount(),
        requestParams.getStartSlot(),
        endSlot);

    final SafeFuture<Void> blocksRequest =
        peer.requestBlocksByRange(
            requestParams.getStartSlot(), requestParams.getCount(), requestManager::processBlock);

    final SafeFuture<Void> blobSidecarsRequest;
    if (blobSidecarManager.isAvailabilityRequiredAtSlot(endSlot)
        || blobSidecarManager.isAvailabilityRequiredAtSlot(requestParams.getStartSlot())) {
      maybeEarliestBlobSidecarSlot =
          Optional.of(
              requestParams
                  .getStartSlot()
                  .max(spec.computeFirstSlotWithBlobSupport().orElseThrow()));
      LOG.trace(
          "Request {} blob sidecars from {} to {}",
          requestParams.getCount(),
          requestParams.getStartSlot(),
          endSlot);
      blobSidecarsRequest =
          peer.requestBlobSidecarsByRange(
              requestParams.getStartSlot(),
              requestParams.getCount(),
              requestManager::processBlobSidecar);
    } else {
      blobSidecarsRequest = SafeFuture.COMPLETE;
    }

    final SafeFuture<Void> executionPayloadsRequest;
    if (spec.isExecutionPayloadEnvelopeAvailableAtSlot(endSlot)
        || spec.isExecutionPayloadEnvelopeAvailableAtSlot(requestParams.getStartSlot())) {
      LOG.trace(
          "Request {} execution payload envelopes from {} to {}",
          requestParams.getCount(),
          requestParams.getStartSlot(),
          endSlot);
      executionPayloadsRequest =
          peer.requestExecutionPayloadEnvelopesByRange(
              requestParams.getStartSlot(),
              requestParams.getCount(),
              requestManager::processExecutionPayload);
    } else {
      executionPayloadsRequest = SafeFuture.COMPLETE;
    }

    return SafeFuture.allOfFailFast(blocksRequest, blobSidecarsRequest, executionPayloadsRequest)
        .thenApply(__ -> shouldRetryByRangeRequest());
  }

  private void processBlobSidecar(final BlobSidecar blobSidecar) {
    blobSidecarsBySlotToImport
        .computeIfAbsent(blobSidecar.getSlotAndBlockRoot(), __ -> new ArrayList<>())
        .add(blobSidecar);
  }

  @VisibleForTesting
  void processExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope) {
    validateExecutionPayloadBlockHash(signedExecutionPayloadEnvelope);
    blindedExecutionPayloadsByBlockRootToImport.put(
        signedExecutionPayloadEnvelope.getBeaconBlockRoot(),
        blindExecutionPayloadEnvelope(signedExecutionPayloadEnvelope));
  }

  private void validateExecutionPayloadBlockHash(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope) {
    final ExecutionPayloadEnvelope envelope = signedExecutionPayloadEnvelope.getMessage();
    final Bytes32 computedBlockHash =
        ExecutionPayloadBlockHashCalculator.computeGloasBlockHash(
            envelope, spec.getExecutionRequestsDataCodec(envelope.getSlot()));
    final Bytes32 payloadBlockHash = envelope.getPayload().getBlockHash();
    if (!computedBlockHash.equals(payloadBlockHash)) {
      throw new IllegalArgumentException(
          String.format(
              "Execution payload block hash mismatch for block root %s at slot %s: expected %s but computed %s",
              signedExecutionPayloadEnvelope.getBeaconBlockRoot(),
              envelope.getSlot(),
              payloadBlockHash,
              computedBlockHash));
    }
  }

  private boolean shouldRetryByRangeRequest() {
    return !batchIsComplete() && requestCount.incrementAndGet() < maxRequests;
  }

  private SafeFuture<Void> requestBlockByRoot() {
    LOG.trace("Request next historical block directly by root {}", lastBlockRoot);
    return peer.requestBlockByRoot(lastBlockRoot)
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isPresent()) {
                return processReceivedBlockByRoot(maybeBlock.get());
              }
              return SafeFuture.COMPLETE;
            });
  }

  private SafeFuture<Void> processReceivedBlockByRoot(final SignedBeaconBlock block) {
    blocksToImport.add(block);
    final SafeFuture<Void> blobSidecarsFetch;
    if (blobSidecarManager.isAvailabilityRequiredAtSlot(block.getSlot())) {
      final int numberOfKzgCommitments =
          block
              .getMessage()
              .getBody()
              .toVersionDeneb()
              .orElseThrow()
              .getBlobKzgCommitments()
              .size();
      if (numberOfKzgCommitments == 0) {
        LOG.trace(
            "Requesting blob sidecars for block with root {} is not necessary because there are no kzg commitments in the block",
            block.getRoot());
        blobSidecarsFetch = SafeFuture.COMPLETE;
      } else {
        blobSidecarsFetch = requestBlobSidecarsByRoot(block.getRoot(), numberOfKzgCommitments);
      }
    } else {
      blobSidecarsFetch = SafeFuture.COMPLETE;
    }

    final SafeFuture<Void> executionPayloadEnvelopeFetch;
    if (spec.isExecutionPayloadEnvelopeAvailableAtSlot(block.getSlot())) {
      LOG.trace(
          "Request associated execution payload envelope for historical block with root {}",
          block.getRoot());
      executionPayloadEnvelopeFetch =
          peer.requestExecutionPayloadEnvelopeByRoot(block.getRoot())
              .thenAccept(maybePayload -> maybePayload.ifPresent(this::processExecutionPayload));
    } else {
      executionPayloadEnvelopeFetch = SafeFuture.COMPLETE;
    }

    return SafeFuture.allOfFailFast(blobSidecarsFetch, executionPayloadEnvelopeFetch);
  }

  private SafeFuture<Void> requestBlobSidecarsByRoot(
      final Bytes32 blockRoot, final int requiredBlobSidecars) {
    LOG.trace(
        "Request {} associated blob sidecars for historical block with root {}",
        requiredBlobSidecars,
        blockRoot);
    final List<BlobIdentifier> blobIdentifiers =
        IntStream.range(0, requiredBlobSidecars)
            .mapToObj(index -> new BlobIdentifier(blockRoot, UInt64.valueOf(index)))
            .toList();
    return peer.requestBlobSidecarsByRoot(
        blobIdentifiers,
        blobSidecar -> {
          processBlobSidecar(blobSidecar);
          return SafeFuture.COMPLETE;
        });
  }

  private SafeFuture<Void> importBatch() {
    // send to signature verification and blob sidecars / execution payload validation and only
    // store blocks, blob sidecars and execution payloads if all checks pass, or if one fails we
    // reject the entire response
    //
    // Note: execution payload envelope signatures are deliberately NOT verified here. Gloas
    // builder indices are reusable (see apply_deposit_for_builder in the consensus spec), so the
    // builder_index -> pubkey mapping must be resolved against the state at the envelope's slot.
    // Non-archival nodes don't have historical states, and resolving against the current head
    // state would use the wrong pubkey once an index has been recycled. Envelope consistency with
    // the block's bid is still checked in validateExecutionPayloadEnvelopes.
    pruneExecutionPayloadsNotInBatch();
    return chainDataClient
        .getBestState()
        .orElseThrow()
        .thenCompose(bestState -> batchVerifyHistoricalBlockSignature(blocksToImport, bestState))
        .thenCompose(
            __ -> {
              final UInt64 latestSlotInBatch = blocksToImport.getLast().getSlot();
              validateBlobSidecars(
                  blocksToImport.getFirst().getSlot(), latestSlotInBatch, blocksToImport);
              validateExecutionPayloadEnvelopes(blocksToImport);

              final SignedBeaconBlock newEarliestBlock = blocksToImport.getFirst();
              return storageUpdateChannel
                  .onFinalizedBlocks(
                      blocksToImport,
                      new HashMap<>(blobSidecarsBySlotToImport),
                      new HashMap<>(blindedExecutionPayloadsByBlockRootToImport),
                      maybeEarliestBlobSidecarSlot)
                  .thenRun(
                      () -> {
                        LOG.trace("Earliest block is now from slot {}", newEarliestBlock.getSlot());
                        future.complete(newEarliestBlock);
                      });
            })
        // always clear the blob sidecars and execution payloads caches
        .alwaysRun(
            () -> {
              blobSidecarsBySlotToImport.clear();
              blindedExecutionPayloadsByBlockRootToImport.clear();
              maybeEarliestBlobSidecarSlot = Optional.empty();
            });
  }

  private SafeFuture<Void> batchVerifyHistoricalBlockSignature(
      final Collection<SignedBeaconBlock> blocks, final BeaconState bestState) {
    final List<BLSSignature> signatures = new ArrayList<>();
    final List<Bytes> signingRoots = new ArrayList<>();
    final List<List<BLSPublicKey>> proposerPublicKeys = new ArrayList<>();

    final Bytes32 genesisValidatorsRoot = bestState.getForkInfo().getGenesisValidatorsRoot();

    blocks.forEach(
        signedBlock -> {
          final BeaconBlock block = signedBlock.getMessage();
          if (block.getSlot().isGreaterThan(SpecConfig.GENESIS_SLOT)) {
            final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot());
            final Fork fork = spec.fork(epoch);
            final Bytes32 domain =
                spec.getDomain(Domain.BEACON_PROPOSER, epoch, fork, genesisValidatorsRoot);
            signatures.add(signedBlock.getSignature());
            signingRoots.add(spec.computeSigningRoot(block, domain));
            final BLSPublicKey proposerPublicKey =
                spec.getValidatorPubKey(bestState, block.getProposerIndex())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Proposer has to be in the state since state is more recent than the block proposed"));
            proposerPublicKeys.add(List.of(proposerPublicKey));
          }
        });

    if (signatures.isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    return signatureVerificationService
        .verify(proposerPublicKeys, signingRoots, signatures)
        .thenAccept(
            signaturesValid -> {
              if (!signaturesValid) {
                throw new IllegalArgumentException("Batch signature verification failed");
              }
            });
  }

  private void pruneExecutionPayloadsNotInBatch() {
    if (blindedExecutionPayloadsByBlockRootToImport.isEmpty()) {
      return;
    }
    final Set<Bytes32> blockRoots =
        blocksToImport.stream().map(SignedBeaconBlock::getRoot).collect(Collectors.toSet());
    blindedExecutionPayloadsByBlockRootToImport.keySet().retainAll(blockRoots);
  }

  private void validateExecutionPayloadEnvelopes(final Collection<SignedBeaconBlock> blocks) {
    if (!blindedExecutionPayloadsByBlockRootToImport.isEmpty()) {
      LOG.trace("Validating execution payload envelopes for a batch");
      final Map<Bytes32, SignedBeaconBlock> blocksByRoot =
          blocks.stream()
              .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
      blindedExecutionPayloadsByBlockRootToImport.forEach(
          (blockRoot, signedEnvelope) ->
              validateExecutionPayloadEnvelope(
                  signedEnvelope,
                  Optional.ofNullable(blocksByRoot.get(blockRoot))
                      .orElseThrow(
                          () ->
                              new IllegalArgumentException(
                                  String.format(
                                      "Received execution payload envelope for unknown block root %s",
                                      blockRoot)))));
    }

    validateExecutionPayloadEnvelopesPresence(blocks);
  }

  private SignedBlindedExecutionPayloadEnvelope blindExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            spec.atSlot(signedExecutionPayloadEnvelope.getMessage().getSlot())
                .getSchemaDefinitions());
    return signedExecutionPayloadEnvelope.blind(schemaDefinitions);
  }

  @VisibleForTesting
  void validateExecutionPayloadEnvelopesPresence(final Collection<SignedBeaconBlock> blocks) {
    // For each Gloas block in the batch, verify whether its payload was delivered by comparing the
    // next block's bid.parentBlockHash with this block's bid.blockHash
    final List<SignedBeaconBlock> orderedBlocks =
        blocks.stream().sorted(Comparator.comparing(SignedBeaconBlock::getSlot)).toList();
    if (orderedBlocks.isEmpty()
        || !spec.isExecutionPayloadEnvelopeAvailableAtSlot(orderedBlocks.getLast().getSlot())) {
      // Entire batch is pre Gloas (or empty), nothing to check
      return;
    }
    for (int i = 1; i < orderedBlocks.size(); i++) {
      final SignedBeaconBlock previousBlock = orderedBlocks.get(i - 1);
      final SignedBeaconBlock currentBlock = orderedBlocks.get(i);
      final Optional<ExecutionPayloadBid> previousBid = getExecutionPayloadBid(previousBlock);
      final Optional<ExecutionPayloadBid> currentBid = getExecutionPayloadBid(currentBlock);
      if (previousBid.isEmpty() || currentBid.isEmpty()) {
        continue;
      }
      assertPreviousEnvelopePresentIfDelivered(previousBlock, previousBid.get(), currentBid.get());
    }
    // Check the last block in the batch against the batch's successor (if available), so it is not
    // skipped by the pairwise loop above
    nextBlock.ifPresent(
        successor -> {
          final SignedBeaconBlock lastBlockInBatch = orderedBlocks.getLast();
          final Optional<ExecutionPayloadBid> lastBid = getExecutionPayloadBid(lastBlockInBatch);
          final Optional<ExecutionPayloadBid> successorBid = getExecutionPayloadBid(successor);
          if (lastBid.isEmpty() || successorBid.isEmpty()) {
            return;
          }
          assertPreviousEnvelopePresentIfDelivered(
              lastBlockInBatch, lastBid.get(), successorBid.get());
        });
  }

  private Optional<ExecutionPayloadBid> getExecutionPayloadBid(final SignedBeaconBlock block) {
    return block
        .getMessage()
        .getBody()
        .toVersionGloas()
        .map(body -> body.getSignedExecutionPayloadBid().getMessage());
  }

  private void assertPreviousEnvelopePresentIfDelivered(
      final SignedBeaconBlock previousBlock,
      final ExecutionPayloadBid previousBid,
      final ExecutionPayloadBid currentBid) {
    if (previousBlock.getSlot().equals(SpecConfig.GENESIS_SLOT)) {
      // Genesis state
      return;
    }
    if (!currentBid.getParentBlockHash().equals(previousBid.getBlockHash())) {
      // Execution chain did not advance, so previous block's payload was not delivered
      return;
    }
    if (blindedExecutionPayloadsByBlockRootToImport.containsKey(previousBlock.getRoot())) {
      return;
    }
    throw new IllegalArgumentException(
        String.format(
            "Missing execution payload envelope for block root %s at slot %s (next block's bid.parentBlockHash indicates payload was delivered)",
            previousBlock.getRoot(), previousBlock.getSlot()));
  }

  @VisibleForTesting
  void validateExecutionPayloadEnvelope(
      final SignedBlindedExecutionPayloadEnvelope signedEnvelope, final SignedBeaconBlock block) {
    final BlindedExecutionPayloadEnvelope envelope = signedEnvelope.getMessage();
    final Bytes32 blockRoot = envelope.getBeaconBlockRoot();
    requireEqual("slot", envelope.getSlot(), block.getSlot(), blockRoot);
    final ExecutionPayloadBid bid =
        block
            .getMessage()
            .getBody()
            .toVersionGloas()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Block with root %s at slot %s does not contain a Gloas body",
                            block.getRoot(), block.getSlot())))
            .getSignedExecutionPayloadBid()
            .getMessage();
    final ExecutionPayloadHeader payloadHeader = envelope.getPayloadHeader();
    requireEqual("builder index", envelope.getBuilderIndex(), bid.getBuilderIndex(), blockRoot);
    requireEqual(
        "parent block hash", payloadHeader.getParentHash(), bid.getParentBlockHash(), blockRoot);
    requireEqual("block hash", payloadHeader.getBlockHash(), bid.getBlockHash(), blockRoot);
    requireEqual("prev randao", payloadHeader.getPrevRandao(), bid.getPrevRandao(), blockRoot);
    requireEqual("gas limit", payloadHeader.getGasLimit(), bid.getGasLimit(), blockRoot);
    requireEqual(
        "execution requests root",
        envelope.getExecutionRequests().hashTreeRoot(),
        bid.getExecutionRequestsRoot(),
        blockRoot);
  }

  private static void requireEqual(
      final String fieldName,
      final Object envelopeValue,
      final Object expectedValue,
      final Bytes32 blockRoot) {
    if (!envelopeValue.equals(expectedValue)) {
      throw new IllegalArgumentException(
          String.format(
              "Execution payload envelope %s %s does not match expected %s for block root %s",
              fieldName, envelopeValue, expectedValue, blockRoot));
    }
  }

  private void validateBlobSidecars(
      final UInt64 firstSlotInBatch,
      final UInt64 latestSlotInBatch,
      final Collection<SignedBeaconBlock> blocks) {
    if (!blobSidecarManager.isAvailabilityRequiredAtSlot(firstSlotInBatch)
        && !blobSidecarManager.isAvailabilityRequiredAtSlot(latestSlotInBatch)) {
      return;
    }
    LOG.trace("Validating blob sidecars for a batch");
    blocks.forEach(this::validateBlobSidecars);
  }

  private void validateBlobSidecars(final SignedBeaconBlock block) {
    // while we know that start or end of batch fulfills requirement, other side of the batch could
    // be outside the requirement bounds
    if (!blobSidecarManager.isAvailabilityRequiredAtSlot(block.getSlot())) {
      return;
    }

    final List<BlobSidecar> blobSidecars =
        blobSidecarsBySlotToImport.getOrDefault(
            block.getSlotAndBlockRoot(), Collections.emptyList());

    LOG.trace("Validating {} blob sidecars for block {}", blobSidecars.size(), block.getRoot());
    final DataAndValidationResult<BlobSidecar> validationResult =
        blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(block, blobSidecars);

    if (validationResult.isFailure()) {
      final String causeMessage =
          validationResult
              .cause()
              .map(cause -> " (" + ExceptionUtil.getRootCauseMessage(cause) + ")")
              .orElse("");
      throw new IllegalArgumentException(
          String.format(
              "Blob sidecars validation for block %s failed: %s%s",
              block.getRoot(), validationResult.validationResult(), causeMessage));
    }
  }

  private RequestParameters calculateRequestParams() {
    final UInt64 startSlot = getStartSlot();
    final UInt64 count = maxSlot.plus(1).minus(startSlot);
    return new RequestParameters(startSlot, count);
  }

  private UInt64 getStartSlot() {
    if (blocksToImport.isEmpty()) {
      return maxSlot.plus(1).minusMinZero(batchSize);
    }

    return blocksToImport.getLast().getSlot().plus(1);
  }

  private Optional<SignedBeaconBlock> getLatestReceivedBlock() {
    return Optional.ofNullable(blocksToImport.peekLast());
  }

  private boolean latestBlockCompletesBatch(final Optional<SignedBeaconBlock> latestBlock) {
    return latestBlock
        .map(SignedBeaconBlock::getRoot)
        .map(r -> r.equals(lastBlockRoot))
        .orElse(false);
  }

  private boolean latestBlockShouldCompleteBatch(final Optional<SignedBeaconBlock> latestBlock) {
    return latestBlock
        .map(SignedBeaconBlock::getSlot)
        .map(s -> s.isGreaterThanOrEqualTo(maxSlot))
        .orElse(false);
  }

  private static class RequestManager {
    private final Bytes32 lastBlockRoot;
    private final Optional<SignedBeaconBlock> previousBlock;
    private final Consumer<SignedBeaconBlock> blockProcessor;
    private final Consumer<BlobSidecar> blobSidecarProcessor;
    private final Consumer<SignedExecutionPayloadEnvelope> executionPayloadProcessor;

    private final AtomicInteger blocksReceived = new AtomicInteger(0);
    private final AtomicBoolean foundLastBlock = new AtomicBoolean(false);

    private RequestManager(
        final Bytes32 lastBlockRoot,
        final Optional<SignedBeaconBlock> previousBlock,
        final Consumer<SignedBeaconBlock> blockProcessor,
        final Consumer<BlobSidecar> blobSidecarProcessor,
        final Consumer<SignedExecutionPayloadEnvelope> executionPayloadProcessor) {
      this.lastBlockRoot = lastBlockRoot;
      this.previousBlock = previousBlock;
      this.blockProcessor = blockProcessor;
      this.blobSidecarProcessor = blobSidecarProcessor;
      this.executionPayloadProcessor = executionPayloadProcessor;
    }

    private SafeFuture<?> processBlock(final SignedBeaconBlock block) {
      return SafeFuture.of(
          () -> {
            if (blocksReceived.incrementAndGet() == 1 && previousBlock.isPresent()) {
              if (!block.getParentRoot().equals(previousBlock.get().getRoot())) {
                throw new InvalidResponseException(
                    "Expected first block to descend from last received block.");
              }
            }

            // Only process blocks up to the last block - ignore any extra blocks
            if (!foundLastBlock.get()) {
              blockProcessor.accept(block);
            }
            if (block.getRoot().equals(lastBlockRoot)) {
              foundLastBlock.set(true);
            }

            return SafeFuture.COMPLETE;
          });
    }

    private SafeFuture<?> processBlobSidecar(final BlobSidecar blobSidecar) {
      blobSidecarProcessor.accept(blobSidecar);
      return SafeFuture.COMPLETE;
    }

    private SafeFuture<?> processExecutionPayload(
        final SignedExecutionPayloadEnvelope executionPayload) {
      executionPayloadProcessor.accept(executionPayload);
      return SafeFuture.COMPLETE;
    }
  }

  private static class RequestParameters {
    private final UInt64 startSlot;
    private final UInt64 count;

    private RequestParameters(final UInt64 startSlot, final UInt64 count) {
      this.startSlot = startSlot;
      this.count = count;
    }

    public UInt64 getStartSlot() {
      return startSlot;
    }

    public UInt64 getCount() {
      return count;
    }

    public UInt64 getEndSlot() {
      return startSlot.plus(count).minusMinZero(1);
    }
  }
}
