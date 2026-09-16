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

package tech.pegasys.teku.beaconrestapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.statetransition.MappedOperationPool;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactory;
import tech.pegasys.teku.validator.coordinator.ValidatorApiHandler;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.publisher.BlockPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisher;

@SuppressWarnings("unchecked")
public abstract class AbstractDataBackedRestAPIIntegrationTest {

  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);

  protected Spec spec;
  protected SpecConfig specConfig;

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final MediaType SSZ = MediaType.parse("application/octet-stream");
  private static final BeaconRestApiConfig CONFIG =
      BeaconRestApiConfig.builder()
          .restApiPort(0)
          .restApiEnabled(true)
          .restApiLightClientEnabled(true)
          .restApiDocsEnabled(true)
          .restApiHostAllowlist(List.of("127.0.0.1", "localhost"))
          .restApiCorsAllowedOrigins(new ArrayList<>())
          .beaconLivenessTrackingEnabled(true)
          .eth1DepositContractAddress(Eth1Address.ZERO)
          .build();

  protected ActiveValidatorChannel activeValidatorChannel;

  protected StubAsyncRunner asyncRunner = new StubAsyncRunner();

  // Mocks
  protected final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  protected final SyncService syncService = mock(SyncService.class);
  protected ValidatorApiHandler validatorApiHandler = mock(ValidatorApiHandler.class);
  protected ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  protected final EventChannels eventChannels = mock(EventChannels.class);
  protected final AggregatingAttestationPool attestationPool =
      mock(AggregatingAttestationPool.class);
  protected final AttestationManager attestationManager = mock(AttestationManager.class);
  protected final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  protected final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  protected final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);

  protected final BlockProductionAndPublishingPerformanceFactory
      blockProductionAndPublishingFactory =
          mock(BlockProductionAndPublishingPerformanceFactory.class);
  protected final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  protected final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  protected final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  protected final SyncStateProvider syncStateProvider = mock(SyncStateProvider.class);
  protected final BlockFactory blockFactory = mock(BlockFactory.class);
  protected final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);
  protected final SyncCommitteeMessagePool syncCommitteeMessagePool =
      mock(SyncCommitteeMessagePool.class);
  protected final BlockPublisher blockPublisher = mock(BlockPublisher.class);
  protected final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);
  protected final ActiveValidatorTracker activeValidatorTracker =
      mock(ActiveValidatorTracker.class);
  protected final DutyMetrics dutyMetrics = mock(DutyMetrics.class);
  protected final PerformanceTracker performanceTracker = mock(PerformanceTracker.class);
  protected final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
      mock(SyncCommitteeSubscriptionManager.class);
  protected final PayloadAttestationPool payloadAttestationPool =
      mock(PayloadAttestationPool.class);
  protected final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);
  protected final ExecutionPayloadFactory executionPayloadFactory =
      mock(ExecutionPayloadFactory.class);
  protected final ExecutionPayloadPublisher executionPayloadPublisher =
      mock(ExecutionPayloadPublisher.class);
  protected final ExecutionPayloadBidManager executionPayloadBidManager =
      mock(ExecutionPayloadBidManager.class);
  protected final ExecutionProofManager executionProofManager = mock(ExecutionProofManager.class);
  protected final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);
  protected RewardCalculator rewardCalculator = mock(RewardCalculator.class);

  protected OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool;

  protected final SignedBlsToExecutionChangeValidator validator =
      mock(SignedBlsToExecutionChangeValidator.class);

  protected final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();
  protected final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  protected final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  protected final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  protected final Eth1DataProvider eth1DataProvider = mock(Eth1DataProvider.class);
  protected final DataColumnSidecarManager dataColumnSidecarManager =
      mock(DataColumnSidecarManager.class);
  private StorageSystem storageSystem;

  protected RecentChainData recentChainData;
  protected CombinedChainDataClient combinedChainDataClient;

  // Update utils
  protected ChainBuilder chainBuilder;
  protected LightClientUpdateStore lightClientUpdateStore;
  protected ChainUpdater chainUpdater;

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected DataProvider dataProvider;
  protected BeaconRestApi beaconRestApi;

  protected OkHttpClient client;

  protected ForkChoice forkChoice;

  private void setupStorage(
      final StateStorageMode storageMode,
      final boolean useMockForkChoice,
      final SpecMilestone specMilestone) {
    setupStorage(storageMode, useMockForkChoice, specMilestone, false);
  }

  private void setupStorage(
      final StateStorageMode storageMode,
      final boolean useMockForkChoice,
      final SpecMilestone specMilestone,
      final boolean storeNonCanonicalBlocks) {
    this.spec = TestSpecFactory.createMinimal(specMilestone);
    this.specConfig = spec.getGenesisSpecConfig();
    this.storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(storageMode)
            .storeNonCanonicalBlocks(storeNonCanonicalBlocks)
            .build();
    activeValidatorChannel = new ActiveValidatorCache(spec, 10);
    recentChainData = storageSystem.recentChainData();
    chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    forkChoice =
        useMockForkChoice
            ? mock(ForkChoice.class)
            : new ForkChoice(
                spec,
                new InlineEventThread(),
                recentChainData,
                new NoopForkChoiceNotifier(),
                new MergeTransitionBlockValidator(spec, recentChainData),
                storageSystem.getMetricsSystem());
    final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
        slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();

    blsToExecutionChangePool =
        new MappedOperationPool<>(
            "BlsOperationPool",
            stubMetricsSystem,
            beaconBlockSchemaSupplier
                .andThen(BeaconBlockBodySchema::toVersionCapella)
                .andThen(Optional::orElseThrow)
                .andThen(BeaconBlockBodySchemaCapella::getBlsToExecutionChangesSchema),
            validator,
            asyncRunner,
            new SystemTimeProvider());
  }

  private void setupAndStartRestAPI(final BeaconRestApiConfig config) {
    lightClientUpdateStore = new LightClientUpdateStore(spec);
    combinedChainDataClient = storageSystem.combinedChainDataClient();
    dataProvider =
        DataProvider.builder()
            .spec(spec)
            .recentChainData(recentChainData)
            .combinedChainDataClient(combinedChainDataClient)
            .blobSidecarReconstructionProvider(mock(BlobSidecarReconstructionProvider.class))
            .p2pNetwork(eth2P2PNetwork)
            .syncService(syncService)
            .validatorApiChannel(validatorApiChannel)
            .blockBlobSidecarsTrackersPool(BlockBlobSidecarsTrackersPool.NOOP)
            .attestationManager(attestationManager)
            .activeValidatorChannel(activeValidatorChannel)
            .attestationPool(attestationPool)
            .attesterSlashingPool(attesterSlashingPool)
            .proposerSlashingPool(proposerSlashingPool)
            .voluntaryExitPool(voluntaryExitPool)
            .blsToExecutionChangePool(blsToExecutionChangePool)
            .syncCommitteeContributionPool(syncCommitteeContributionPool)
            .proposersDataManager(proposersDataManager)
            .forkChoiceNotifier(forkChoiceNotifier)
            .rewardCalculator(rewardCalculator)
            .dataColumnSidecarManager(dataColumnSidecarManager)
            .payloadAttestationPool(payloadAttestationPool)
            .proposerPreferencesManager(proposerPreferencesManager)
            .lightClientUpdateStore(lightClientUpdateStore)
            .build();

    beaconRestApi =
        new JsonTypeDefinitionBeaconRestApi(
            dataProvider,
            eth1DataProvider,
            config,
            eventChannels,
            asyncRunner,
            StubTimeProvider.withTimeInMillis(1000),
            spec);
    assertThat(beaconRestApi.start()).isCompleted();
    client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();
  }

  private void setupAndStartRestAPI() {
    setupAndStartRestAPI(CONFIG);
  }

  protected void startPreGenesisRestAPIWithConfig(final BeaconRestApiConfig config) {
    setupStorage(StateStorageMode.ARCHIVE, false, SpecMilestone.PHASE0);
    // Start API
    setupAndStartRestAPI(config);
  }

  protected void startRestApiAtGenesisWithValidatorApiHandler(final SpecMilestone specMilestone) {

    setupStorage(StateStorageMode.ARCHIVE, false, specMilestone, true);
    validatorApiHandler =
        new ValidatorApiHandler(
            chainDataProvider,
            nodeDataProvider,
            networkDataProvider,
            storageSystem.combinedChainDataClient(),
            syncStateProvider,
            blockFactory,
            attestationPool,
            attestationManager,
            attestationTopicSubscriber,
            activeValidatorTracker,
            dutyMetrics,
            performanceTracker,
            spec,
            forkChoiceTrigger,
            proposersDataManager,
            syncCommitteeMessagePool,
            syncCommitteeContributionPool,
            syncCommitteeSubscriptionManager,
            blockProductionAndPublishingFactory,
            blockPublisher,
            payloadAttestationPool,
            DataAvailabilitySampler.NOOP,
            executionPayloadManager,
            executionPayloadFactory,
            executionPayloadPublisher,
            executionPayloadBidManager,
            proposerPreferencesManager,
            executionProofManager);
    validatorApiChannel = validatorApiHandler;
    chainUpdater.initializeGenesis();
    setupAndStartRestAPI();
  }

  protected void startRestAPIAtGenesis(final SpecMilestone specMilestone) {
    startRestAPIAtGenesis(StateStorageMode.ARCHIVE, specMilestone, CONFIG);
  }

  protected void startRestAPIAtGenesis(
      final StateStorageMode storageMode, final SpecMilestone specMilestone) {
    startRestAPIAtGenesis(storageMode, specMilestone, CONFIG);
  }

  protected void startRestAPIAtGenesis() {
    startRestAPIAtGenesis(StateStorageMode.ARCHIVE, SpecMilestone.PHASE0, CONFIG);
  }

  protected void startRestAPIAtGenesisWithHighestSupportedMilestone() {
    startRestAPIAtGenesis(StateStorageMode.ARCHIVE, SpecMilestone.getHighestMilestone(), CONFIG);
  }

  protected void startRestApiAtGenesisStoringNonCanonicalBlocks(final SpecMilestone specMilestone) {
    setupStorage(StateStorageMode.ARCHIVE, false, specMilestone, true);
    chainUpdater.initializeGenesis();
    setupAndStartRestAPI();
  }

  protected void startRestAPIAtGenesis(
      final StateStorageMode storageMode,
      final SpecMilestone specMilestone,
      final BeaconRestApiConfig config) {
    // Initialize genesis
    setupStorage(storageMode, false, specMilestone);
    chainUpdater.initializeGenesis();
    // Start API
    setupAndStartRestAPI(config);
  }

  public List<SignedBlockAndState> createBlocksAtSlots(final long... slots) {
    final UInt64[] unsignedSlots =
        Arrays.stream(slots).mapToObj(UInt64::valueOf).toArray(UInt64[]::new);
    return createBlocksAtSlots(unsignedSlots);
  }

  public void setCurrentSlot(final long slot) {
    chainUpdater.setCurrentSlot(UInt64.valueOf(slot));
  }

  public List<SignedBlockAndState> createBlocksAtSlots(final UInt64... slots) {
    final ArrayList<SignedBlockAndState> results = new ArrayList<>();
    for (UInt64 slot : slots) {
      final SignedBlockAndState block = chainUpdater.advanceChain(slot);
      chainUpdater.updateBestBlock(block);
      results.add(block);
    }
    return results;
  }

  protected void assertBadRequest(final Response response) {
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  protected void assertNotFound(final Response response) {
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  protected void assertForbidden(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_FORBIDDEN);
    assertThat(response.body().string()).contains("Host not authorized");
  }

  protected Response getResponse(final String path, final String contentType, final String encoding)
      throws IOException {
    final Request request =
        new Request.Builder()
            .url(getUrl(path))
            .header("Accept", contentType)
            .header("Accept-Encoding", encoding)
            .build();
    return client.newCall(request).execute();
  }

  protected Response getResponse(final String path, final String contentType) throws IOException {
    final Request request =
        new Request.Builder().url(getUrl(path)).header("Accept", contentType).build();
    return client.newCall(request).execute();
  }

  protected Response getResponse(final String path) throws IOException {
    return getResponseFromUrl(getUrl(path));
  }

  protected Response getResponseFromUrl(final String url) throws IOException {
    final Request request = new Request.Builder().url(url).build();
    return client.newCall(request).execute();
  }

  protected String getUrl(final String path) {
    return "http://localhost:" + beaconRestApi.getListenPort() + path;
  }

  protected Response getResponse(
      final String route, final Map<String, String> getParams, final String contentType)
      throws IOException {
    return getResponse(route + prepareQueryParams(getParams), contentType);
  }

  protected Response getResponse(final String route, final Map<String, String> getParams)
      throws IOException {
    return getResponse(route + prepareQueryParams(getParams));
  }

  protected Response post(
      final String route, final String postData, final Map<String, String> postParams)
      throws IOException {
    return post(route + prepareQueryParams(postParams), postData, Optional.empty());
  }

  protected Response post(
      final String route,
      final String postData,
      final Map<String, String> postParams,
      final Optional<String> milestone)
      throws IOException {
    return post(route + prepareQueryParams(postParams), postData, milestone);
  }

  protected Response post(final String route, final String postData) throws IOException {
    return post(route, postData, Optional.empty());
  }

  protected Response post(
      final String route, final String postData, final Optional<String> milestone)
      throws IOException {
    return post(
        route,
        postData,
        requestBuilder ->
            milestone.ifPresent(m -> requestBuilder.header(HEADER_CONSENSUS_VERSION, m)));
  }

  protected Response post(
      final String route, final String postData, final Consumer<Request.Builder> requestModifier)
      throws IOException {
    final RequestBody body = RequestBody.create(postData, JSON);
    final Request.Builder requestBuilder = new Request.Builder().url(getUrl() + route).post(body);
    requestModifier.accept(requestBuilder);
    return client.newCall(requestBuilder.build()).execute();
  }

  protected Response postSsz(
      final String route,
      final byte[] postData,
      final Map<String, String> postParams,
      final Optional<String> milestoneHeader)
      throws IOException {
    return postSsz(route + prepareQueryParams(postParams), postData, milestoneHeader);
  }

  protected Response postSsz(
      final String route, final byte[] postData, final Optional<String> milestoneHeader)
      throws IOException {
    final RequestBody body = RequestBody.create(postData, SSZ);

    final Request.Builder requestBuilder = new Request.Builder().url(getUrl() + route).post(body);
    milestoneHeader.ifPresent(m -> requestBuilder.header(HEADER_CONSENSUS_VERSION, m));
    return client.newCall(requestBuilder.build()).execute();
  }

  private String getUrl() {
    return "http://localhost:" + beaconRestApi.getListenPort();
  }

  private String prepareQueryParams(final Map<String, String> params) {
    if (params.isEmpty()) {
      return "";
    }

    return "?"
        + params.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
  }

  protected JsonNode getResponseData(final Response response) throws IOException {
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());
    return body.get("data");
  }

  protected void checkEmptyBodyToRoute(final String route, final int expectedResponseCode)
      throws IOException {
    final Response response = post(route, OBJECT_MAPPER.writeValueAsString(""));
    Assertions.assertThat(response.code()).isEqualTo(expectedResponseCode);
  }

  @AfterEach
  public void tearDown() throws Exception {
    assertThat(beaconRestApi.stop()).isCompleted();
    if (client != null) {
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
    if (storageSystem != null) {
      storageSystem.close();
    }
  }
}
