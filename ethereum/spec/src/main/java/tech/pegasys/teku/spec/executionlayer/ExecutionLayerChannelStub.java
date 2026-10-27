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

package tech.pegasys.teku.spec.executionlayer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.time.SystemTimeProvider.SYSTEM_TIME_PROVIDER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBlockHashCalculator;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundleDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.fulu.BlobsBundleFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.BlobsUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ExecutionLayerChannelStub implements ExecutionLayerChannel {

  private static final Logger LOG = LogManager.getLogger();
  private static final ClientVersion STUB_CLIENT_VERSION =
      new ClientVersion("SB", ExecutionLayerChannel.STUB_ENDPOINT_PREFIX, "0.0.0", Bytes4.ZERO);

  private final TimeProvider timeProvider;
  private final Map<Bytes32, PowBlock> knownBlocks = new ConcurrentHashMap<>();
  private final Map<Bytes32, PayloadStatus> knownPosBlocks = new ConcurrentHashMap<>();
  private final LRUCache<Bytes8, HeadAndAttributes> payloadIdToHeadAndAttrsCache;
  private final AtomicLong payloadIdCounter = new AtomicLong(0);
  private final Set<Bytes32> requestedPowBlocks = new HashSet<>();
  private final Spec spec;
  private final BlobsUtil blobsUtil;
  private final Random random = new Random();

  private PayloadStatus payloadStatus = PayloadStatus.VALID;
  private Optional<Integer> blobsToGenerate = Optional.empty();

  // transition emulation
  private static final Bytes32 TERMINAL_BLOCK_PARENT_HASH = Bytes32.ZERO;
  private final boolean transitionEmulationEnabled;
  private boolean bellatrixActivationDetected = false;
  private Bytes32 terminalBlockHash;
  private PowBlock terminalBlockParent;
  private PowBlock terminalBlock;
  private boolean terminalBlockSent;
  private UInt64 transitionTime;

  // block, payload and blobs tracking
  private Optional<ExecutionPayload> lastBuilderPayloadToBeUnblinded = Optional.empty();
  private Optional<tech.pegasys.teku.spec.datastructures.builder.BlobsBundle>
      lastBuilderBlobsBundle = Optional.empty();
  private Optional<PowBlock> lastValidBlock = Optional.empty();

  private boolean online = true;

  public ExecutionLayerChannelStub(
      final Spec spec,
      final TimeProvider timeProvider,
      final List<String> additionalConfigs,
      final boolean enableTransitionEmulation) {
    this.payloadIdToHeadAndAttrsCache = LRUCache.create(10);
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.transitionEmulationEnabled = enableTransitionEmulation;
    this.blobsUtil = new BlobsUtil(spec);

    applyAdditionalConfig(additionalConfigs);
  }

  public ExecutionLayerChannelStub(final Spec spec, final boolean enableTransitionEmulation) {
    this(spec, SYSTEM_TIME_PROVIDER, List.of(), enableTransitionEmulation);
  }

  public void addPowBlock(final PowBlock block) {
    knownBlocks.put(block.getBlockHash(), block);
  }

  public void addPosBlock(final Bytes32 blockHash, final PayloadStatus payloadStatus) {
    knownPosBlocks.put(blockHash, payloadStatus);
  }

  private void applyAdditionalConfig(final List<String> additionalConfigs) {
    additionalConfigs.forEach(
        config -> {
          if (config.startsWith("blobs=")) {
            final String status = config.substring("blobs=".length());
            blobsToGenerate = Optional.of(Integer.valueOf(status));
          } else {
            LOG.warn("Unknown additional config: {}", config);
          }
        });
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    offlineCheck();

    if (!transitionEmulationEnabled) {
      requestedPowBlocks.add(blockHash);
      return SafeFuture.completedFuture(Optional.ofNullable(knownBlocks.get(blockHash)));
    }

    checkBellatrixActivation();

    if (blockHash.equals(TERMINAL_BLOCK_PARENT_HASH)) {
      return SafeFuture.completedFuture(Optional.of(terminalBlockParent));
    }
    if (blockHash.equals(terminalBlockHash)) {
      // TBH flow
      LOG.debug("TBH: sending terminal block hash " + terminalBlockHash);
      terminalBlockSent = true;
      return SafeFuture.completedFuture(Optional.of(terminalBlock));
    }

    return SafeFuture.failedFuture(
        new UnsupportedOperationException(
            "getPowBlock supported for terminalBlockParent or terminalBlock only. Requested block: "
                + blockHash));
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    offlineCheck();

    if (!transitionEmulationEnabled) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException("getPowChainHead not supported"));
    }

    checkBellatrixActivation();

    if (terminalBlockSent) {
      return SafeFuture.completedFuture(lastValidBlock.orElse(terminalBlock));
    }
    if (timeProvider.getTimeInSeconds().isGreaterThanOrEqualTo(transitionTime)) {
      // TTD flow
      LOG.debug("TTD: sending terminal block hash " + terminalBlockHash);
      terminalBlockSent = true;
      return SafeFuture.completedFuture(terminalBlock);
    }
    return SafeFuture.completedFuture(terminalBlockParent);
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    offlineCheck();

    if (!bellatrixActivationDetected) {
      LOG.debug(
          "forkChoiceUpdated received before terminalBlock has been sent. Assuming transition already happened");

      // do the activation check to be able to respond to terminal block verification
      checkBellatrixActivation();
    }

    final ForkChoiceUpdatedResult forkChoiceUpdatedResult =
        new ForkChoiceUpdatedResult(
            PayloadStatus.VALID,
            payloadBuildingAttributes.map(
                payloadAttributes -> {
                  final Bytes8 payloadId =
                      Bytes8.leftPad(Bytes.ofUnsignedInt(payloadIdCounter.incrementAndGet()));
                  payloadIdToHeadAndAttrsCache.invalidateWithNewValue(
                      payloadId,
                      new HeadAndAttributes(
                          forkChoiceState.headExecutionBlockHash(), payloadAttributes));
                  return payloadId;
                }));

    if (!LOG.isDebugEnabled()) {
      LOG.info(
          "EL Stub FCU head: {}:{}, payload: {}:{}",
          forkChoiceState.headBlockSlot(),
          forkChoiceState.headBlock(),
          forkChoiceState.headExecutionBlockNumber(),
          forkChoiceState.headExecutionBlockHash());
    }
    LOG.debug(
        "forkChoiceUpdated: forkChoiceState: {} payloadBuildingAttributes: {} -> forkChoiceUpdatedResult: {}",
        forkChoiceState,
        payloadBuildingAttributes,
        forkChoiceUpdatedResult);

    return SafeFuture.completedFuture(forkChoiceUpdatedResult);
  }

  @Override
  public SafeFuture<GetPayloadResponse> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final BeaconState state) {
    offlineCheck();

    if (!bellatrixActivationDetected) {
      LOG.debug(
          "getPayload received before terminalBlock has been sent. Assuming transition already happened");

      // do the activation check to be able to respond to terminal block verification
      checkBellatrixActivation();
    }
    final UInt64 slot = state.getSlot();

    final Optional<SchemaDefinitionsBellatrix> schemaDefinitionsBellatrix =
        spec.atSlot(slot).getSchemaDefinitions().toVersionBellatrix();

    if (schemaDefinitionsBellatrix.isEmpty()) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException(
              "getPayload not supported for non-Bellatrix milestones"));
    }

    final HeadAndAttributes headAndAttrs = getCachedHeadAndAttributes(executionPayloadContext);
    final PayloadBuildingAttributes payloadAttributes = headAndAttrs.attributes;

    final List<Bytes> transactions = generateTransactions(slot, headAndAttrs);
    final Bytes logsBloom = Bytes.random(256);
    final Optional<ExecutionRequests> maybeExecutionRequests = getExecutionRequests(slot);

    final Function<Bytes32, ExecutionPayload> createExecutionPayload =
        blockHash ->
            schemaDefinitionsBellatrix
                .orElseThrow()
                .getExecutionPayloadSchema()
                .createExecutionPayload(
                    builder ->
                        builder
                            .parentHash(headAndAttrs.head)
                            .feeRecipient(payloadAttributes.feeRecipient())
                            .stateRoot(Bytes32.ZERO)
                            .receiptsRoot(Bytes32.ZERO)
                            .logsBloom(logsBloom)
                            .prevRandao(payloadAttributes.prevRandao())
                            .blockNumber(UInt64.valueOf(payloadIdCounter.get()))
                            .gasLimit(UInt64.ONE)
                            .gasUsed(UInt64.ZERO)
                            .timestamp(payloadAttributes.timestamp())
                            .extraData(Bytes.EMPTY)
                            .baseFeePerGas(UInt256.ONE)
                            .blockHash(blockHash)
                            .transactions(transactions)
                            .withdrawals(() -> payloadAttributes.withdrawals().orElse(List.of()))
                            .blobGasUsed(() -> UInt64.ZERO)
                            .excessBlobGas(() -> UInt64.ZERO)
                            .blockAccessList(() -> Bytes32.ZERO)
                            .slotNumber(() -> slot));
    final ExecutionPayload randomBlockHashExecutionPayload =
        createExecutionPayload.apply(Bytes32.random());
    // Gloas envelopes are only accepted when the block hash matches the payload content
    final ExecutionPayload executionPayload =
        spec.isExecutionPayloadEnvelopeAvailableAtSlot(slot)
            ? createExecutionPayload.apply(
                ExecutionPayloadBlockHashCalculator.computeGloasBlockHash(
                    randomBlockHashExecutionPayload,
                    maybeExecutionRequests.orElseThrow(),
                    payloadAttributes.parentBeaconBlock().blockRoot(),
                    spec.getExecutionRequestsDataCodec(slot)))
            : randomBlockHashExecutionPayload;

    // we assume all blocks are produced locally
    lastValidBlock =
        Optional.of(
            new PowBlock(
                executionPayload.getBlockHash(),
                executionPayload.getParentHash(),
                payloadAttributes.timestamp()));

    headAndAttrs.currentExecutionPayload = Optional.of(executionPayload);

    LOG.info(
        "slot: {}, payloadId: {}, Payload hash: {}",
        state.getSlot(),
        executionPayloadContext.getPayloadId(),
        executionPayload.getBlockHash());

    final GetPayloadResponse getPayloadResponse =
        headAndAttrs
            .currentBlobsBundle
            .map(
                blobsBundle -> {
                  LOG.debug("getPayload: blobsBundle: {}", blobsBundle.toBriefString());
                  return maybeExecutionRequests
                      .map(
                          executionRequests ->
                              new GetPayloadResponse(
                                  executionPayload,
                                  UInt256.valueOf(424242424242424242L),
                                  blobsBundle,
                                  false,
                                  executionRequests))
                      .orElseGet(
                          () ->
                              new GetPayloadResponse(
                                  executionPayload,
                                  UInt256.valueOf(424242424242424242L),
                                  blobsBundle,
                                  false));
                })
            .orElse(new GetPayloadResponse(executionPayload, UInt256.valueOf(434242424242424242L)));

    return SafeFuture.completedFuture(getPayloadResponse);
  }

  private Optional<ExecutionRequests> getExecutionRequests(final UInt64 slot) {
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      final ExecutionRequestsSchema<?> executionRequestsSchema =
          SchemaDefinitionsElectra.required(spec.atSlot(slot).getSchemaDefinitions())
              .getExecutionRequestsSchema();
      return Optional.of(executionRequestsSchema.createBuilder().build());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(
      final NewPayloadRequest newPayloadRequest, final UInt64 slot) {
    offlineCheck();

    final ExecutionPayload executionPayload = newPayloadRequest.getExecutionPayload();
    final PayloadStatus returnedStatus =
        Optional.ofNullable(knownPosBlocks.get(executionPayload.getBlockHash()))
            .orElse(payloadStatus);
    LOG.debug(
        "newPayload: executionPayload blockHash: {}  versionedHashes: {} parentBeaconBlockRoot: {} -> {}",
        executionPayload.getBlockHash(),
        newPayloadRequest.getVersionedHashes(),
        newPayloadRequest.getParentBeaconBlockRoot(),
        returnedStatus);
    return SafeFuture.completedFuture(returnedStatus);
  }

  @Override
  public SafeFuture<List<ClientVersion>> engineGetClientVersion(final ClientVersion clientVersion) {
    offlineCheck();

    return SafeFuture.completedFuture(List.of(STUB_CLIENT_VERSION));
  }

  @Override
  public SafeFuture<List<Optional<BlobAndProof>>> engineGetBlobAndProofs(
      final List<VersionedHash> blobVersionedHashes, final UInt64 slot) {
    return SafeFuture.completedFuture(
        blobVersionedHashes.stream().map(e -> Optional.<BlobAndProof>empty()).toList());
  }

  @Override
  public SafeFuture<List<BlobAndCellProofs>> engineGetBlobAndCellProofsList(
      final List<VersionedHash> blobVersionedHashes, final UInt64 slot) {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public SafeFuture<List<ExecutionPayloadBody>> engineGetPayloadBodiesByHash(
      final List<Bytes32> blockHashes) {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public SafeFuture<Void> builderRegisterValidators(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations, final UInt64 slot) {
    offlineCheck();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<BuilderBidOrFallbackData> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    offlineCheck();

    final UInt64 slot = state.getSlot();
    LOG.debug(
        "getPayloadHeader: payloadId: {} slot: {} ... delegating to getPayload ...",
        executionPayloadContext,
        slot);

    final SchemaDefinitionsBellatrix schemaDefinitions =
        SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions());

    return engineGetPayload(executionPayloadContext, state)
        .alwaysRun(blockProductionPerformance::engineGetPayload)
        .thenApply(
            getPayloadResponse -> {
              final ExecutionPayload executionPayload = getPayloadResponse.getExecutionPayload();
              LOG.debug(
                  "getPayloadHeader: payloadId: {} slot: {} -> executionPayload blockHash: {}",
                  executionPayloadContext,
                  slot,
                  executionPayload.getBlockHash());
              lastBuilderPayloadToBeUnblinded = Optional.of(executionPayload);
              final ExecutionPayloadHeader payloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createFromExecutionPayload(executionPayload);

              final Optional<SszList<SszKZGCommitment>> blobKzgCommitments =
                  getPayloadResponse
                      .getBlobsBundle()
                      .map(
                          blobsBundle -> {
                            final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
                                SchemaDefinitionsDeneb.required(schemaDefinitions);
                            lastBuilderBlobsBundle =
                                Optional.of(
                                    schemaDefinitionsDeneb
                                        .getBlobsBundleSchema()
                                        .create(
                                            blobsBundle.getCommitments(),
                                            blobsBundle.getProofs(),
                                            blobsBundle.getBlobs()));
                            return schemaDefinitionsDeneb
                                .getBlobKzgCommitmentsSchema()
                                .createFromBlobsBundle(blobsBundle);
                          });

              final Optional<ExecutionRequests> executionRequests =
                  schemaDefinitions
                      .toVersionElectra()
                      .map(SchemaDefinitionsElectra::getExecutionRequestsSchema)
                      .map(
                          executionRequestsSchema ->
                              executionRequestsSchema.createBuilder().build());

              final BuilderBid builderBid =
                  schemaDefinitions
                      .getBuilderBidSchema()
                      .createBuilderBid(
                          builder -> {
                            builder.header(payloadHeader);
                            blobKzgCommitments.ifPresent(builder::blobKzgCommitments);
                            builder.value(getPayloadResponse.getExecutionPayloadValue());
                            // using an empty public key for the stub
                            builder.publicKey(BLSPublicKey.empty());
                            executionRequests.ifPresent(builder::executionRequests);
                          });
              return BuilderBidOrFallbackData.create(builderBid);
            })
        .alwaysRun(blockProductionPerformance::builderGetHeader);
  }

  @Override
  public SafeFuture<BuilderPayloadOrFallbackData> builderGetPayload(
      final SignedBeaconBlock signedBeaconBlock,
      final Function<UInt64, Optional<ExecutionPayloadResult>> getCachedPayloadResultFunction) {
    offlineCheck();

    final UInt64 slot = signedBeaconBlock.getSlot();
    final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
    final Optional<SchemaDefinitionsBellatrix> schemaDefinitionsBellatrix =
        schemaDefinitions.toVersionBellatrix();

    checkState(
        schemaDefinitionsBellatrix.isPresent(),
        "proposeBlindedBlock not supported for non-Bellatrix milestones");

    checkState(
        signedBeaconBlock.isBlinded(),
        "proposeBlindedBlock requires a signed blinded beacon block");

    checkState(
        lastBuilderPayloadToBeUnblinded.isPresent(),
        "proposeBlindedBlock requires a previous call to getPayloadHeader");

    final ExecutionPayloadHeader executionPayloadHeader =
        signedBeaconBlock.getMessage().getBody().getOptionalExecutionPayloadHeader().orElseThrow();

    checkState(
        executionPayloadHeader
            .hashTreeRoot()
            .equals(lastBuilderPayloadToBeUnblinded.get().hashTreeRoot()),
        "provided signed blinded block contains an execution payload header not matching the previously retrieved "
            + "execution payload via getPayloadHeader");

    LOG.debug(
        "proposeBlindedBlock: slot: {} block: {} -> unblinded executionPayload blockHash: {}",
        slot,
        signedBeaconBlock.getRoot(),
        lastBuilderPayloadToBeUnblinded.get().getBlockHash());

    final BuilderPayload builderPayload =
        lastBuilderBlobsBundle
            // post Deneb
            .map(
                blobsBundle -> {
                  checkState(
                      signedBeaconBlock
                              .getMessage()
                              .getBody()
                              .getOptionalBlobKzgCommitments()
                              .orElseThrow()
                              .size()
                          == blobsBundle.getNumberOfBlobs(),
                      "provided signed blinded block contains different number of kzg commitments than the expected %s",
                      blobsBundle.getNumberOfBlobs());
                  return (BuilderPayload)
                      SchemaDefinitionsDeneb.required(schemaDefinitions)
                          .getExecutionPayloadAndBlobsBundleSchema()
                          .create(lastBuilderPayloadToBeUnblinded.get(), blobsBundle);
                })
            // pre Deneb
            .orElse(lastBuilderPayloadToBeUnblinded.get());

    return SafeFuture.completedFuture(BuilderPayloadOrFallbackData.create(builderPayload));
  }

  public void setPayloadStatus(final PayloadStatus payloadStatus) {
    this.payloadStatus = payloadStatus;
  }

  /** Set to empty to restore random number of blobs for each block */
  public void setBlobsToGenerate(final Optional<Integer> blobsToGenerate) {
    this.blobsToGenerate = blobsToGenerate;
  }

  public Set<Bytes32> getRequestedPowBlocks() {
    return requestedPowBlocks;
  }

  public void setOnline(final boolean online) {
    this.online = online;
  }

  @SuppressWarnings("unused")
  private static class HeadAndAttributes {
    private final Bytes32 head;
    private final PayloadBuildingAttributes attributes;
    private Optional<ExecutionPayload> currentExecutionPayload = Optional.empty();
    private Optional<BlobsBundle> currentBlobsBundle = Optional.empty();

    private HeadAndAttributes(final Bytes32 head, final PayloadBuildingAttributes attributes) {
      this.head = head;
      this.attributes = attributes;
    }
  }

  private void offlineCheck() {
    if (!online) {
      throw new RuntimeException("stub is offline");
    }
  }

  private void checkBellatrixActivation() {
    if (!bellatrixActivationDetected) {
      LOG.debug("Bellatrix activation detected");
      bellatrixActivationDetected = true;
      prepareTransitionBlocks(timeProvider.getTimeInSeconds());
    }
  }

  private void prepareTransitionBlocks(final UInt64 bellatrixActivationTime) {
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.BELLATRIX);
    checkNotNull(specVersion, "Bellatrix must be scheduled to for transition emulation");
    final SpecConfigBellatrix specConfigBellatrix =
        specVersion.getConfig().toVersionBellatrix().orElseThrow();

    LOG.debug("Preparing transition blocks using spec");
    final Bytes32 configTerminalBlockHash = specConfigBellatrix.getTerminalBlockHash();

    LOG.debug("Preparing transition via TBH: {}", configTerminalBlockHash);
    this.transitionTime = bellatrixActivationTime;
    this.terminalBlockHash = configTerminalBlockHash;

    this.terminalBlockParent = new PowBlock(TERMINAL_BLOCK_PARENT_HASH, Bytes32.ZERO, UInt64.ZERO);
    this.terminalBlock =
        new PowBlock(terminalBlockHash, TERMINAL_BLOCK_PARENT_HASH, transitionTime);
  }

  private HeadAndAttributes getCachedHeadAndAttributes(
      final ExecutionPayloadContext executionPayloadContext) {
    final Bytes8 payloadId = executionPayloadContext.getPayloadId();
    return payloadIdToHeadAndAttrsCache
        .getCached(payloadId)
        .orElseThrow(
            () ->
                new RuntimeException(String.format("payloadId %s not found in cache", payloadId)));
  }

  private List<Bytes> generateTransactions(
      final UInt64 slot, final HeadAndAttributes headAndAttrs) {
    final List<Bytes> transactions = new ArrayList<>();
    transactions.add(Bytes.fromHexString("0x0edf"));

    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      transactions.add(generateBlobsAndTransaction(slot, headAndAttrs, milestone));
    }

    transactions.add(Bytes.fromHexString("0xedf0"));
    return transactions;
  }

  private Bytes generateBlobsAndTransaction(
      final UInt64 slot, final HeadAndAttributes headAndAttrs, final SpecMilestone milestone) {

    final List<Blob> blobs =
        blobsUtil.generateBlobs(
            slot,
            blobsToGenerate.orElseGet(
                () -> random.nextInt(spec.getMaxBlobsPerBlockAtSlot(slot).orElseThrow() + 1)));
    final List<KZGCommitment> commitments = blobsUtil.blobsToKzgCommitments(blobs);
    final BlobsBundle blobsBundle;
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      // cell proofs
      final List<KZGProof> proofs =
          blobs.stream().flatMap(blob -> blobsUtil.computeKzgCellProofs(blob).stream()).toList();
      blobsBundle = new BlobsBundleFulu(commitments, proofs, blobs);
    } else {
      final List<KZGProof> proofs = blobsUtil.computeKzgProofs(blobs, commitments);
      blobsBundle = new BlobsBundleDeneb(commitments, proofs, blobs);
    }

    headAndAttrs.currentBlobsBundle = Optional.of(blobsBundle);

    return blobsUtil.generateRawBlobTransactionFromKzgCommitments(commitments);
  }
}
