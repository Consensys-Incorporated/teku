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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import com.google.common.base.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(milestone = {FULU, GLOAS})
public class DataColumnSidecarsByRootMessageHandlerTest {

  private final UInt64 genesisTime = UInt64.valueOf(1982239L);
  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private DataColumnSidecarsByRootRequestMessageSchema messageSchema;
  private DataColumnsByRootIdentifierSchema identifierSchema;
  private final ArgumentCaptor<DataColumnSidecar> datacolumnSidecarCaptor =
      ArgumentCaptor.forClass(DataColumnSidecar.class);
  private final Optional<RequestKey> allowedRequest = Optional.of(new RequestKey(ZERO, 100));

  @SuppressWarnings("unchecked")
  private final ResponseCallback<DataColumnSidecar> callback = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final NodeId nodeId = new MockNodeId(1);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier =
      () -> custodyGroupCountManager;
  private final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor =
      mock(DataColumnSidecarArchiveReconstructor.class);
  private String protocolId;
  private DataStructureUtil dataStructureUtil;
  private DataColumnSidecarsByRootMessageHandler handler;
  private Spec spec;

  @BeforeEach
  public void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA ->
              throw new IllegalArgumentException("Milestone is not supported");
          case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
          case GLOAS -> TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
          case HEZE -> TestSpecFactory.createMinimalWithHezeForkEpoch(currentForkEpoch);
        };
    dataStructureUtil = new DataStructureUtil(spec);
    final SchemaDefinitionsFulu schemaDefinitionsFulu =
        spec.atEpoch(currentForkEpoch).getSchemaDefinitions().toVersionFulu().orElseThrow();
    messageSchema = schemaDefinitionsFulu.getDataColumnSidecarsByRootRequestMessageSchema();
    identifierSchema = schemaDefinitionsFulu.getDataColumnsByRootIdentifierSchema();
    final UInt64 currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    final RpcEncoding rpcEncoding =
        RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
    protocolId = BeaconChainMethodIds.getDataColumnSidecarsByRootMethodId(1, rpcEncoding);
    handler =
        new DataColumnSidecarsByRootMessageHandler(
            spec,
            metricsSystem,
            combinedChainDataClient,
            custodyGroupCountManagerSupplier,
            dataColumnSidecarArchiveReconstructor,
            DasReqRespLogger.NOOP);

    when(peer.getId()).thenReturn(nodeId);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(dataStructureUtil.randomUInt256()));

    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveDataColumnSidecarsRequest(any(), anyLong())).thenReturn(allowedRequest);
    reset(combinedChainDataClient);
    when(combinedChainDataClient.getSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(currentForkFirstSlot)));
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot))));
    // fulu fork epoch is finalized
    when(combinedChainDataClient.getFinalizedBlock())
        .thenReturn(Optional.of(dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot)));
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);

    // mock store
    when(store.getGenesisTime()).thenReturn(genesisTime);
    // current epoch is 2
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(
                currentForkEpoch.increment().times(spec.getSlotsPerEpoch(ZERO)), genesisTime));

    // custodying everything by default
    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(
            IntStream.of(0, 128).mapToObj(UInt64::valueOf).collect(Collectors.toUnmodifiableSet()));
  }

  @TestTemplate
  public void shouldNotSendDataColumnSidecarsIfPeerIsRateLimited() {

    when(peer.approveDataColumnSidecarsRequest(any(), anyLong())).thenReturn(Optional.empty());

    final DataColumnSidecarsByRootRequestMessage request =
        messageSchema.of(generateDataColumnsByRootIdentifiers(3, 2));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    // Requesting 6 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(6)));
    // No adjustment
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());

    final long rateLimitedCount =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requests_total",
            "rate_limited");

    assertThat(rateLimitedCount).isOne();

    verifyNoInteractions(callback);
  }

  @TestTemplate
  public void shouldSendAvailableOnlyResources() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 4).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();

    // the second block root can't be found in the database
    final Bytes32 secondBlockRoot = dataColumnsByRootIdentifiers[1].getBlockRoot();
    when(combinedChainDataClient.getSlotByBlockRoot(secondBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(combinedChainDataClient.getSidecar(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnSlotAndIdentifier slotAndIdentifier = invocation.getArgument(0);
              if (slotAndIdentifier.blockRoot().equals(secondBlockRoot)) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              for (int i = 0; i < 4; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(slotAndIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 3 data column sidecars
    verify(peer).adjustDataColumnSidecarsRequest(eq(allowedRequest.get()), eq(Long.valueOf(3)));

    verify(callback, times(3)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBeaconBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots =
        List.of(
            generatedSidecars.get(0).getBeaconBlockRoot(),
            generatedSidecars.get(2).getBeaconBlockRoot(),
            generatedSidecars.get(3).getBeaconBlockRoot());

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);
  }

  @TestTemplate
  public void shouldSilentlySkipBlockRootOutsideMinimumRequestEpoch() {
    // 1 million epoch — makes slot 100 (epoch 12) far outside the availability window
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(
                UInt64.valueOf(1_000_000).times(spec.getSlotsPerEpoch(ZERO)), genesisTime));

    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);

    // an old block out of availability window — all 4 roots resolve to this slot
    when(combinedChainDataClient.getSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.valueOf(100))));

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // 0 served vs 4 requested — rate-limit token adjusted
    verify(peer).adjustDataColumnSidecarsRequest(eq(allowedRequest.get()), eq(0L));

    verify(callback, never()).respond(any());
    verify(callback, never()).completeWithErrorResponse(any());
    verify(callback).completeSuccessfully();
  }

  @TestTemplate
  public void shouldSilentlySkipBlockRootBeforeFuluForkEpoch() {
    // Skip when Fulu activates at epoch 0 (e.g. GLOAS test config) — no pre-Fulu epochs exist.
    // Before the fix, atEpoch(preFuluEpoch) threw IllegalArgumentException -> SERVER_ERROR (2).
    // After the fix, the pre-Fulu epoch guard causes the root to be silently skipped.
    final UInt64 fuluForkEpoch = spec.atEpoch(UInt64.ZERO).getConfig().getFuluForkEpoch();
    assumeTrue(fuluForkEpoch.isGreaterThan(UInt64.ZERO), "No pre-Fulu epochs in this config");

    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(1, 1);

    when(combinedChainDataClient.getSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.ZERO)));

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    verify(callback, never()).respond(any());
    verify(callback, never()).completeWithErrorResponse(any());
    verify(callback).completeSuccessfully();
  }

  @TestTemplate
  public void shouldServeInWindowRootsAndSkipOutOfWindowRootInMixedRequest() {
    // Current epoch 5000: min_request_epoch = max(5000 - 4096, FULU_FORK_EPOCH) = 904 for both
    // FULU (FULU_FORK_EPOCH=1) and GLOAS (FULU_FORK_EPOCH=0).
    final UInt64 currentEpochForTest = UInt64.valueOf(5000);
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(
                currentEpochForTest.times(spec.getSlotsPerEpoch(ZERO)), genesisTime));

    // In-window: epoch 1000 (5000 - 1000 = 4000 <= 4096)
    final UInt64 inWindowSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(1000));
    // Out-of-window: epoch 100 (5000 - 100 = 4900 > 4096)
    final UInt64 outOfWindowSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(100));

    final DataColumnsByRootIdentifier inWindowA = generateDataColumnsByRootIdentifiers(1, 1)[0];
    final DataColumnsByRootIdentifier outOfWindowB = generateDataColumnsByRootIdentifiers(1, 1)[0];
    final DataColumnsByRootIdentifier inWindowC = generateDataColumnsByRootIdentifiers(1, 1)[0];

    final DataColumnSidecar sidecarA = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecarC = dataStructureUtil.randomDataColumnSidecar();

    when(combinedChainDataClient.getSlotByBlockRoot(inWindowA.getBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(inWindowSlot)));
    when(combinedChainDataClient.getSlotByBlockRoot(outOfWindowB.getBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(outOfWindowSlot)));
    when(combinedChainDataClient.getSlotByBlockRoot(inWindowC.getBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(inWindowSlot)));

    when(combinedChainDataClient.getSidecar(
            argThat(id -> id != null && id.blockRoot().equals(inWindowA.getBlockRoot()))))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecarA)));
    when(combinedChainDataClient.getSidecar(
            argThat(id -> id != null && id.blockRoot().equals(inWindowC.getBlockRoot()))))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecarC)));

    handler.onIncomingMessage(
        protocolId,
        peer,
        messageSchema.of(new DataColumnsByRootIdentifier[] {inWindowA, outOfWindowB, inWindowC}),
        callback);

    // 2 served vs 3 requested — rate-limit token adjusted
    verify(peer).adjustDataColumnSidecarsRequest(eq(allowedRequest.get()), eq(2L));
    verify(callback, times(2)).respond(datacolumnSidecarCaptor.capture());
    verify(callback, never()).completeWithErrorResponse(any());
    verify(callback).completeSuccessfully();

    assertThat(datacolumnSidecarCaptor.getAllValues()).containsExactly(sidecarA, sidecarC);
  }

  @TestTemplate
  public void shouldSendCustodyOnlyResources() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        new DataColumnsByRootIdentifier[4];
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiersFirst =
        generateDataColumnsByRootIdentifiers(3, 1);
    dataColumnsByRootIdentifiers[0] = dataColumnsByRootIdentifiersFirst[0];
    dataColumnsByRootIdentifiers[1] = dataColumnsByRootIdentifiersFirst[1];
    dataColumnsByRootIdentifiers[2] = dataColumnsByRootIdentifiersFirst[2];
    // not in custody
    dataColumnsByRootIdentifiers[3] =
        identifierSchema.create(dataStructureUtil.randomBytes32(), ONE);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 4).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();
    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(Set.of(dataColumnsByRootIdentifiers[0].getColumns().getFirst()));

    when(combinedChainDataClient.getSidecar(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnSlotAndIdentifier slotAndIdentifier = invocation.getArgument(0);
              // it will not reach this step
              assertThat(slotAndIdentifier.blockRoot())
                  .isNotEqualTo(dataColumnsByRootIdentifiers[3].getBlockRoot());
              for (int i = 0; i < 3; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(slotAndIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              // not requesting #3
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 3 data column sidecars
    verify(peer).adjustDataColumnSidecarsRequest(eq(allowedRequest.get()), eq(Long.valueOf(3)));

    verify(callback, times(3)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBeaconBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots =
        List.of(
            generatedSidecars.get(0).getBeaconBlockRoot(),
            generatedSidecars.get(1).getBeaconBlockRoot(),
            generatedSidecars.get(2).getBeaconBlockRoot());

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);
  }

  @TestTemplate
  public void shouldSendToPeerRequestedDataColumnSidecars() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 4).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();

    when(combinedChainDataClient.getSidecar(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnSlotAndIdentifier slotAndIdentifier = invocation.getArgument(0);
              for (int i = 0; i < 4; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(slotAndIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 3 data column sidecars
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());

    verify(combinedChainDataClient, never()).getFinalizedBlockSlot();
    verify(combinedChainDataClient, never()).getFinalizedBlock();
    verify(callback, times(4)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBeaconBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots =
        List.of(
            generatedSidecars.get(0).getBeaconBlockRoot(),
            generatedSidecars.get(1).getBeaconBlockRoot(),
            generatedSidecars.get(2).getBeaconBlockRoot(),
            generatedSidecars.get(3).getBeaconBlockRoot());

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);
  }

  @TestTemplate
  public void shouldWaitForPreviousResponseWriteBeforeSendingNextSidecar() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(2, 1);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 2).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();
    final SafeFuture<Void> firstWriteFuture = new SafeFuture<>();

    when(callback.respond(any())).thenReturn(firstWriteFuture).thenReturn(SafeFuture.COMPLETE);
    when(combinedChainDataClient.getSidecar(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnSlotAndIdentifier slotAndIdentifier = invocation.getArgument(0);
              for (int i = 0; i < 2; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(slotAndIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    verify(callback, times(1)).respond(any());
    verify(callback, never()).completeSuccessfully();

    firstWriteFuture.complete(null);

    verify(callback, times(2)).respond(any());
    verify(callback).completeSuccessfully();
  }

  @TestTemplate
  public void shouldTryToReconstructArchiveDataColumnSidecars() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);

    when(combinedChainDataClient.getSidecar(any(DataColumnSlotAndIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(dataColumnSidecarArchiveReconstructor.isSidecarPruned(any(), any())).thenReturn(true);
    when(dataColumnSidecarArchiveReconstructor.reconstructDataColumnSidecar(any(), any(), anyInt()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 0 data column sidecars, archive reconstructed empty
    verify(peer).adjustDataColumnSidecarsRequest(any(), eq(Long.valueOf(0)));

    verify(combinedChainDataClient, never()).getFinalizedBlockSlot();
    verify(combinedChainDataClient, never()).getFinalizedBlock();
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBeaconBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots = List.of();

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);

    verify(dataColumnSidecarArchiveReconstructor, times(4))
        .reconstructDataColumnSidecar(any(), any(), anyInt());
    verify(dataColumnSidecarArchiveReconstructor).onRequestCompleted(anyInt());
  }

  @TestTemplate
  public void shouldSendReconstructedArchiveDataColumnSidecars() {
    // four requested (custodied) columns whose canonical sidecars are pruned but reconstruct
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);

    when(combinedChainDataClient.getSidecar(any(DataColumnSlotAndIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(dataColumnSidecarArchiveReconstructor.isSidecarPruned(any(), any())).thenReturn(true);
    when(dataColumnSidecarArchiveReconstructor.reconstructDataColumnSidecar(any(), any(), anyInt()))
        .thenAnswer(
            invocation -> {
              final SignedBeaconBlock block = invocation.getArgument(0);
              final UInt64 index = invocation.getArgument(1);
              return SafeFuture.completedFuture(
                  Optional.of(dataStructureUtil.randomDataColumnSidecar(block, index)));
            });

    final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarsByRootMessageHandler.class, Level.DEBUG);
    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // reconstruction and serving of each column is observable in the logs at debug level
    assertThat(logCaptor.getDebugLogs())
        .filteredOn(log -> log.contains("Serving reconstructed data column sidecar"))
        .hasSize(4);
    logCaptor.close();

    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // all four columns reconstructed and served, so no downward adjustment happens
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());
    verify(callback, times(4)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();
    verify(dataColumnSidecarArchiveReconstructor, times(4))
        .reconstructDataColumnSidecar(any(), any(), anyInt());
    verify(dataColumnSidecarArchiveReconstructor).onRequestCompleted(anyInt());

    // every served sidecar is a genuine reconstruction of the requested column index
    assertThat(datacolumnSidecarCaptor.getAllValues())
        .hasSize(4)
        .allSatisfy(sidecar -> assertThat(sidecar.getIndex()).isEqualTo(ZERO));
  }

  @TestTemplate
  public void shouldNotReconstructWhenBlockIsMissing() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);

    when(combinedChainDataClient.getSidecar(any(DataColumnSlotAndIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(dataColumnSidecarArchiveReconstructor.isSidecarPruned(any(), any())).thenReturn(true);
    // block for the requested root is not available, so reconstruction cannot run
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    verify(peer).adjustDataColumnSidecarsRequest(any(), eq(Long.valueOf(0)));
    verify(callback, never()).respond(any());
    verify(callback).completeSuccessfully();
    verify(dataColumnSidecarArchiveReconstructor, never())
        .reconstructDataColumnSidecar(any(), any(), anyInt());
  }

  @TestTemplate
  public void shouldNotAdjustIfAnErrorOccurs() {
    // Be protective: do not adjust due to error
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);

    final RuntimeException error = new RuntimeException("Fatal error");

    when(combinedChainDataClient.getSidecar(any())).thenReturn(SafeFuture.failedFuture(error));

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    verify(callback)
        .completeWithUnexpectedError(argThat(exception -> exception.getCause().equals(error)));
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());
  }

  @TestTemplate
  public void shouldServeSidecarForNotYetImportedBlockViaRecentlyValidatedSlotFallback() {
    // End-to-end exercise of the new slot-resolution fallback: the block is gossip-validated but
    // not yet imported, so fork choice and the finalized DB return no slot; only the
    // recently-validated index does. Use a real CombinedChainDataClient (over mocked
    // recentChainData
    // and historicalChainData) so the actual getSlotByBlockRoot fallback runs, then assert the
    // handler serves the custody column the DB returns.
    final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
    final CombinedChainDataClient realClient =
        new CombinedChainDataClient(recentChainData, historicalChainData, spec);
    final DataColumnSidecarsByRootMessageHandler handlerWithRealClient =
        new DataColumnSidecarsByRootMessageHandler(
            spec,
            metricsSystem,
            realClient,
            custodyGroupCountManagerSupplier,
            dataColumnSidecarArchiveReconstructor,
            DasReqRespLogger.NOOP);

    final UInt64 currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final DataColumnsByRootIdentifier identifier =
        identifierSchema.create(blockRoot, List.of(UInt64.valueOf(0)));
    final DataColumnSidecar sidecar = dataStructureUtil.randomDataColumnSidecar();

    // Not imported: fork choice and the finalized DB miss.
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedSlotByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    // Only the recently-validated index can supply the slot.
    when(recentChainData.getRecentlyValidatedSlotByBlockRoot(blockRoot))
        .thenReturn(Optional.of(currentForkFirstSlot));
    when(recentChainData.getStore()).thenReturn(store);
    // Custody already persisted the column to the DB, keyed by the resolved slot.
    when(historicalChainData.getSidecar(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecar)));

    handlerWithRealClient.onIncomingMessage(
        protocolId,
        peer,
        messageSchema.of(new DataColumnsByRootIdentifier[] {identifier}),
        callback);

    verify(callback, times(1)).respond(sidecar);
    verify(callback).completeSuccessfully();
  }

  @TestTemplate
  public void shouldCacheBlockRootSlotResolutionAcrossRequests() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final DataColumnsByRootIdentifier[] identifiers = {
      identifierSchema.create(blockRoot, List.of(UInt64.valueOf(0)))
    };

    when(combinedChainDataClient.getSidecar(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomDataColumnSidecar())));

    // First request
    handler.onIncomingMessage(protocolId, peer, messageSchema.of(identifiers), callback);
    verify(combinedChainDataClient, times(1)).getSlotByBlockRoot(blockRoot);

    // Second request with the same block root
    handler.onIncomingMessage(protocolId, peer, messageSchema.of(identifiers), callback);
    // Still only one call - the second request used the cache
    verify(combinedChainDataClient, times(1)).getSlotByBlockRoot(blockRoot);
  }

  private DataColumnsByRootIdentifier[] generateDataColumnsByRootIdentifiers(
      final int numberOfBlocks, final int columnIndicesPerBlock) {
    return IntStream.range(0, numberOfBlocks)
        .mapToObj(
            __ ->
                identifierSchema.create(
                    dataStructureUtil.randomBytes32(),
                    IntStream.range(0, columnIndicesPerBlock).mapToObj(UInt64::valueOf).toList()))
        .toArray(DataColumnsByRootIdentifier[]::new);
  }
}
