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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadHeaderGloas;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {GLOAS})
public class HistoricalBatchFetcherGloasTest {

  private final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>>
      blindedExecutionPayloadsCaptor = ArgumentCaptor.forClass(Map.class);

  private final int maxRequests = 5;

  private Spec spec;
  private ChainBuilder chainBuilder;
  private StorageSystem storageSystem;
  private CombinedChainDataClient chainDataClient;
  private List<SignedBeaconBlock> blockBatch;
  private Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> expectedBlindedPayloads;
  private SignedBeaconBlock firstBlockInBatch;
  private SignedBeaconBlock lastBlockInBatch;
  private HistoricalBatchFetcher fetcher;
  private RespondingEth2Peer peer;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec);
    storageSystem = InMemoryStorageSystemBuilder.create().specProvider(spec).build();
    storageSystem.chainUpdater().initializeGenesis();

    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
    when(storageUpdateChannel.onFinalizedBlocks(any(), any(), any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(20);

    blockBatch =
        chainBuilder
            .streamBlocksAndStates(10, 20)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    lastBlockInBatch = chainBuilder.getLatestBlockAndState().getBlock();
    firstBlockInBatch = blockBatch.getFirst();

    final SchemaDefinitionsGloas schemaDefinitionsGloas =
        SchemaDefinitionsGloas.required(
            spec.atSlot(lastBlockInBatch.getSlot()).getSchemaDefinitions());
    expectedBlindedPayloads =
        chainBuilder
            .streamExecutionPayloads(10, 20)
            .collect(
                Collectors.toMap(
                    SignedExecutionPayloadEnvelope::getBeaconBlockRoot,
                    envelope -> envelope.blind(schemaDefinitionsGloas)));

    chainDataClient =
        new CombinedChainDataClient(
            storageSystem.recentChainData(), mock(StorageQueryChannel.class), spec);

    peer = RespondingEth2Peer.create(spec, chainBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            Optional.empty(),
            maxRequests);

    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true));
  }

  @TestTemplate
  public void run_returnAllBlocksAndExecutionPayloadEnvelopesOnFirstRequest() {
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(2);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(), any(), blindedExecutionPayloadsCaptor.capture(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
    assertThat(blindedExecutionPayloadsCaptor.getValue()).isEqualTo(expectedBlindedPayloads);
    // Envelope signatures are intentionally not verified (builder indices are reusable in Gloas),
    // so the signature verifier is called only once (for block signatures)
    verify(signatureVerifier, times(1)).verify(any(), any(), anyList());
  }

  @TestTemplate
  public void processExecutionPayload_throwsWhenPayloadBlockHashDoesNotMatchComputedHash() {
    final SignedExecutionPayloadEnvelope original =
        chainBuilder.getExecutionPayloadAtSlot(UInt64.valueOf(15)).orElseThrow();
    final ExecutionPayloadEnvelope originalEnvelope = original.getMessage();
    final ExecutionPayload tamperedPayload =
        copyPayloadWithStateRoot(
            originalEnvelope.getPayload(), originalEnvelope.getPayload().getStateRoot().not());
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            spec.atSlot(originalEnvelope.getSlot()).getSchemaDefinitions());
    final ExecutionPayloadEnvelope tamperedEnvelope =
        schemaDefinitions
            .getExecutionPayloadEnvelopeSchema()
            .create(
                tamperedPayload,
                originalEnvelope.getExecutionRequests(),
                originalEnvelope.getBuilderIndex(),
                originalEnvelope.getBeaconBlockRoot(),
                originalEnvelope.getParentBeaconBlockRoot());
    final SignedExecutionPayloadEnvelope signedTamperedEnvelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(tamperedEnvelope, original.getSignature());

    assertThatThrownBy(() -> fetcher.processExecutionPayload(signedTamperedEnvelope))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Execution payload block hash mismatch")
        .hasMessageContaining(originalEnvelope.getBeaconBlockRoot().toHexString());
  }

  @TestTemplate
  public void validateExecutionPayloadEnvelopesPresence_throwsWhenDeliveredEnvelopeIsMissing() {
    final SignedBeaconBlock withheldBlock = chainBuilder.getBlockAtSlot(15);

    chainBuilder
        .streamExecutionPayloads(10, 20)
        .filter(envelope -> !envelope.getBeaconBlockRoot().equals(withheldBlock.getRoot()))
        .forEach(fetcher::processExecutionPayload);

    assertThatThrownBy(() -> fetcher.validateExecutionPayloadEnvelopesPresence(blockBatch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing execution payload envelope")
        .hasMessageContaining(withheldBlock.getRoot().toHexString());
  }

  @TestTemplate
  public void
      validateExecutionPayloadEnvelopesPresence_handlesUnorderedBlocksWhenDeliveredEnvelopeIsMissing() {
    final SignedBeaconBlock withheldBlock = chainBuilder.getBlockAtSlot(15);

    chainBuilder
        .streamExecutionPayloads(10, 20)
        .filter(envelope -> !envelope.getBeaconBlockRoot().equals(withheldBlock.getRoot()))
        .forEach(fetcher::processExecutionPayload);

    final List<SignedBeaconBlock> unorderedBlockBatch = new ArrayList<>(blockBatch);
    Collections.reverse(unorderedBlockBatch);

    assertThatThrownBy(() -> fetcher.validateExecutionPayloadEnvelopesPresence(unorderedBlockBatch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing execution payload envelope")
        .hasMessageContaining(withheldBlock.getRoot().toHexString());
  }

  @TestTemplate
  public void
      validateExecutionPayloadEnvelopesPresence_throwsWhenLastBlockEnvelopeIsMissingAndNextBlockDeliveredIt() {
    chainBuilder.generateBlockAtSlot(21);
    final SignedBeaconBlock successor = chainBuilder.getBlockAtSlot(21);
    final HistoricalBatchFetcher fetcherWithSuccessor =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            Optional.of(successor),
            maxRequests);

    // Withhold only the envelope for the last block in the batch, so the loop inside the batch
    // cannot catch it
    chainBuilder
        .streamExecutionPayloads(10, 20)
        .filter(envelope -> !envelope.getBeaconBlockRoot().equals(lastBlockInBatch.getRoot()))
        .forEach(fetcherWithSuccessor::processExecutionPayload);

    assertThatThrownBy(
            () -> fetcherWithSuccessor.validateExecutionPayloadEnvelopesPresence(blockBatch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing execution payload envelope")
        .hasMessageContaining(lastBlockInBatch.getRoot().toHexString());
  }

  @TestTemplate
  public void validateExecutionPayloadEnvelope_acceptsMatchingEnvelope() {
    final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(15);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(block.getSlot()).getSchemaDefinitions());
    final SignedBlindedExecutionPayloadEnvelope blindedEnvelope =
        chainBuilder.getExecutionPayload(block.getRoot()).orElseThrow().blind(schemaDefinitions);

    fetcher.validateExecutionPayloadEnvelope(blindedEnvelope, block);
  }

  @TestTemplate
  public void validateExecutionPayloadEnvelope_throwsWhenPayloadHeaderDoesNotMatchBid() {
    final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(16);
    final SignedBeaconBlock otherBlock = chainBuilder.getBlockAtSlot(15);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(block.getSlot()).getSchemaDefinitions());
    final SignedBlindedExecutionPayloadEnvelope originalBlindedEnvelope =
        chainBuilder
            .getExecutionPayload(otherBlock.getRoot())
            .orElseThrow()
            .blind(schemaDefinitions);
    final ExecutionPayloadHeader targetSlotPayloadHeader =
        copyPayloadHeaderWithSlot(
            schemaDefinitions,
            originalBlindedEnvelope.getMessage().getPayloadHeader(),
            block.getSlot());
    final ExecutionPayloadBid blockBid = getExecutionPayloadBid(block);
    final BlindedExecutionPayloadEnvelope tamperedEnvelope =
        schemaDefinitions
            .getBlindedExecutionPayloadEnvelopeSchema()
            .create(
                targetSlotPayloadHeader,
                originalBlindedEnvelope.getMessage().getExecutionRequests(),
                blockBid.getBuilderIndex(),
                block.getRoot(),
                block.getParentRoot());
    final SignedBlindedExecutionPayloadEnvelope signedTampered =
        schemaDefinitions
            .getSignedBlindedExecutionPayloadEnvelopeSchema()
            .create(tamperedEnvelope, originalBlindedEnvelope.getSignature());

    assertThatThrownBy(() -> fetcher.validateExecutionPayloadEnvelope(signedTampered, block))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parent block hash")
        .hasMessageContaining(block.getRoot().toHexString());
  }

  private ExecutionPayloadBid getExecutionPayloadBid(final SignedBeaconBlock block) {
    return block
        .getMessage()
        .getBody()
        .toVersionGloas()
        .orElseThrow()
        .getSignedExecutionPayloadBid()
        .getMessage();
  }

  private ExecutionPayload copyPayloadWithStateRoot(
      final ExecutionPayload payload, final Bytes32 stateRoot) {
    final ExecutionPayloadGloas gloasPayload = ExecutionPayloadGloas.required(payload);
    return gloasPayload
        .getSchema()
        .createExecutionPayload(
            builder ->
                builder
                    .parentHash(gloasPayload.getParentHash())
                    .feeRecipient(gloasPayload.getFeeRecipient())
                    .stateRoot(stateRoot)
                    .receiptsRoot(gloasPayload.getReceiptsRoot())
                    .logsBloom(gloasPayload.getLogsBloom())
                    .prevRandao(gloasPayload.getPrevRandao())
                    .blockNumber(gloasPayload.getBlockNumber())
                    .gasLimit(gloasPayload.getGasLimit())
                    .gasUsed(gloasPayload.getGasUsed())
                    .timestamp(gloasPayload.getTimestamp())
                    .extraData(gloasPayload.getExtraData())
                    .baseFeePerGas(gloasPayload.getBaseFeePerGas())
                    .blockHash(gloasPayload.getBlockHash())
                    .transactions(
                        gloasPayload.getTransactions().stream().map(Transaction::getBytes).toList())
                    .withdrawals(gloasPayload.getWithdrawals()::asList)
                    .blobGasUsed(gloasPayload::getBlobGasUsed)
                    .excessBlobGas(gloasPayload::getExcessBlobGas)
                    .blockAccessList(() -> gloasPayload.getBlockAccessList().getBytes())
                    .slotNumber(gloasPayload::getSlotNumber));
  }

  private ExecutionPayloadHeader copyPayloadHeaderWithSlot(
      final SchemaDefinitionsGloas schemaDefinitions,
      final ExecutionPayloadHeader payloadHeader,
      final UInt64 slot) {
    final ExecutionPayloadHeaderGloas gloasPayloadHeader =
        ExecutionPayloadHeaderGloas.required(payloadHeader);
    return schemaDefinitions
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            builder ->
                builder
                    .parentHash(gloasPayloadHeader.getParentHash())
                    .feeRecipient(gloasPayloadHeader.getFeeRecipient())
                    .stateRoot(gloasPayloadHeader.getStateRoot())
                    .receiptsRoot(gloasPayloadHeader.getReceiptsRoot())
                    .logsBloom(gloasPayloadHeader.getLogsBloom())
                    .prevRandao(gloasPayloadHeader.getPrevRandao())
                    .blockNumber(gloasPayloadHeader.getBlockNumber())
                    .gasLimit(gloasPayloadHeader.getGasLimit())
                    .gasUsed(gloasPayloadHeader.getGasUsed())
                    .timestamp(gloasPayloadHeader.getTimestamp())
                    .extraData(gloasPayloadHeader.getExtraData())
                    .baseFeePerGas(gloasPayloadHeader.getBaseFeePerGas())
                    .blockHash(gloasPayloadHeader.getBlockHash())
                    .transactionsRoot(gloasPayloadHeader.getTransactionsRoot())
                    .withdrawalsRoot(gloasPayloadHeader::getWithdrawalsRoot)
                    .blobGasUsed(gloasPayloadHeader::getBlobGasUsed)
                    .excessBlobGas(gloasPayloadHeader::getExcessBlobGas)
                    .blockAccessListRoot(gloasPayloadHeader::getBlockAccessListRoot)
                    .slotNumber(() -> slot));
  }
}
