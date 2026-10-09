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

package tech.pegasys.teku.networking.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.DAS_CUSTODY_GROUP_COUNT_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.NEXT_FORK_DIGEST_ENR_FIELD;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.network.p2p.peer.SimplePeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

class DiscoveryNetworkTest {
  private final Spec spec =
      TestSpecFactory.createMinimalFulu(
          b ->
              b.altairForkEpoch(UInt64.valueOf(10_000))
                  .bellatrixForkEpoch(UInt64.valueOf(20_000))
                  .capellaForkEpoch(UInt64.valueOf(30_000))
                  .denebForkEpoch(UInt64.valueOf(40_000))
                  .electraForkEpoch(UInt64.valueOf(50_000))
                  .fuluForkEpoch(UInt64.valueOf(60_000))
                  .fuluBuilder(
                      fb ->
                          fb.blobSchedule(
                              List.of(new BlobScheduleEntry(UInt64.valueOf(65_000), 64)))));
  private final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<Fork> forks = spec.getForkSchedule().getForks();
  private final Bytes32 genesisValidatorsRoot = dataStructureUtil.randomBytes32();
  final ForkInfo currentForkInfo = new ForkInfo(forks.get(0), genesisValidatorsRoot);
  final Bytes4 currentForkDigest =
      spec.computeForkDigest(genesisValidatorsRoot, currentForkInfo.getFork().getEpoch());
  final Fork nextFork = forks.get(1);

  private final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0L);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> p2pNetwork = mock(P2PNetwork.class);

  private final DiscoveryService discoveryService = mock(DiscoveryService.class);
  private final ConnectionManager connectionManager = mock(ConnectionManager.class);

  private final DiscoveryNetwork<Peer> discoveryNetwork =
      new DiscoveryNetwork<>(
          p2pNetwork, discoveryService, connectionManager, spec, spec::getGenesisSchemaDefinitions);

  @Test
  public void shouldStartConnectionManagerAfterP2pAndDiscoveryStarted() {
    final SafeFuture<Void> p2pStart = new SafeFuture<>();
    final SafeFuture<Void> discoveryStart = new SafeFuture<>();
    final SafeFuture<Object> connectionManagerStart = new SafeFuture<>();
    doReturn(p2pStart).when(p2pNetwork).start();
    doReturn(discoveryStart).when(discoveryService).start();
    doReturn(connectionManagerStart).when(connectionManager).start();

    final SafeFuture<?> started = discoveryNetwork.start();

    verify(p2pNetwork).start();
    verify(discoveryService).start();
    verify(connectionManager, never()).start();

    p2pStart.complete(null);
    verify(connectionManager, never()).start();

    discoveryStart.complete(null);
    verify(connectionManager).start();
    assertThat(started).isNotDone();

    connectionManagerStart.complete(null);
    assertThat(started).isCompleted();
  }

  @Test
  public void shouldReturnEnrFromDiscoveryService() {
    when(discoveryService.getEnr()).thenReturn(Optional.of("enr:-"));
    assertThat(discoveryNetwork.getEnr()).contains("enr:-");
  }

  @Test
  @SuppressWarnings({"FutureReturnValueIgnored"})
  public void shouldStopConnectionManagerBeforeNetworkAndDiscovery() {
    final SafeFuture<Void> connectionStop = new SafeFuture<>();
    doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    doReturn(connectionStop).when(connectionManager).stop();

    Waiter.waitFor(discoveryNetwork::stop);

    verify(connectionManager).stop();
    verify(discoveryService).updateCustomENRField(any(), any());
    verify(discoveryService).getEnr();
    verifyNoMoreInteractions(discoveryService);
    verifyNoInteractions(p2pNetwork);

    connectionStop.complete(null);
    verify(p2pNetwork).stop();
    verify(discoveryService).stop();
  }

  @Test
  @SuppressWarnings({"FutureReturnValueIgnored"})
  public void shouldStopNetworkAndDiscoveryWhenConnectionManagerStopFails() {
    final SafeFuture<Void> connectionStop = new SafeFuture<>();
    doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    doReturn(connectionStop).when(connectionManager).stop();

    Waiter.waitFor(discoveryNetwork::stop);

    verify(connectionManager).stop();
    verify(discoveryService).updateCustomENRField(any(), any());
    verify(discoveryService).getEnr();
    verifyNoMoreInteractions(discoveryService);
    verifyNoInteractions(p2pNetwork);

    connectionStop.completeExceptionally(new RuntimeException("Nope"));
    verify(p2pNetwork).stop();
    verify(discoveryService).stop();
  }

  @Test
  public void shouldNotEnableDiscoveryWhenDiscoveryIsDisabled() {
    final DiscoveryConfig discoveryConfig =
        DiscoveryConfig.builder().isDiscoveryEnabled(false).build();
    final NetworkConfig networkConfig = NetworkConfig.builder().build();
    final PeerSelectionStrategy peerSelectionStrategy =
        new SimplePeerSelectionStrategy(new TargetPeerRange(20, 30, 0));
    final DiscoveryNetwork<?> network =
        DiscoveryNetworkBuilder.create()
            .metricsSystem(new NoOpMetricsSystem())
            .asyncRunner(DelayedExecutorAsyncRunner.create())
            .kvStore(new MemKeyValueStore<>())
            .p2pNetwork(p2pNetwork)
            .peerSelectionStrategy(peerSelectionStrategy)
            .discoveryConfig(discoveryConfig)
            .p2pConfig(networkConfig)
            .spec(spec)
            .timeProvider(timeProvider)
            .currentSchemaDefinitionsSupplier(spec::getGenesisSchemaDefinitions)
            .build();
    assertThat(network.getEnr()).isEmpty();
  }

  @Test
  public void setForkInfo_noFutureForkScheduled() {
    discoveryNetwork.setForkInfo(
        currentForkInfo, currentForkDigest, Optional.empty(), Optional.empty(), Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkDigest,
            currentForkInfo.getFork().getCurrentVersion(),
            SpecConfig.FAR_FUTURE_EPOCH);
    verify(discoveryService).updateCustomENRField("eth2", expectedEnrForkId.sszSerialize());
  }

  @Test
  public void setForkInfo_futureForkScheduled() {
    discoveryNetwork.setForkInfo(
        currentForkInfo,
        currentForkDigest,
        Optional.of(nextFork),
        Optional.empty(),
        Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(currentForkDigest, nextFork.getCurrentVersion(), nextFork.getEpoch());
    verify(discoveryService).updateCustomENRField("eth2", expectedEnrForkId.sszSerialize());
  }

  @Test
  public void setForkInfo_futureBpoForkScheduled() {
    final ForkInfo fuluForkInfo = new ForkInfo(forks.get(6), genesisValidatorsRoot);
    final Bytes4 fuluForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, fuluForkInfo.getFork().getEpoch());
    final BlobParameters nextBpoFork = new BlobParameters(UInt64.valueOf(65_000), 64);
    final Bytes4 nextForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, nextBpoFork.epoch());

    discoveryNetwork.setForkInfo(
        fuluForkInfo,
        fuluForkDigest,
        Optional.empty(),
        Optional.of(new BlobParameters(UInt64.valueOf(65_000), 64)),
        Optional.of(nextForkDigest));

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            fuluForkDigest, fuluForkInfo.getFork().getCurrentVersion(), nextBpoFork.epoch());
    verify(discoveryService).updateCustomENRField("eth2", expectedEnrForkId.sszSerialize());
    verify(discoveryService)
        .updateCustomENRField("nfd", SszBytes4.of(nextForkDigest).sszSerialize());
  }

  @Test
  public void setForkInfo_futureBpoForkScheduledBeforeHardFork() {
    final ForkInfo fuluForkInfo = new ForkInfo(forks.get(6), genesisValidatorsRoot);
    final Bytes4 fuluForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, fuluForkInfo.getFork().getEpoch());
    final BlobParameters nextBpoFork = new BlobParameters(UInt64.valueOf(65_000), 64);
    final Fork nextHardFork = nextHardFork(fuluForkInfo, UInt64.valueOf(70_000));
    final Bytes4 nextForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, nextBpoFork.epoch());

    discoveryNetwork.setForkInfo(
        fuluForkInfo,
        fuluForkDigest,
        Optional.of(nextHardFork),
        Optional.of(nextBpoFork),
        Optional.of(nextForkDigest));

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            fuluForkDigest, fuluForkInfo.getFork().getCurrentVersion(), nextBpoFork.epoch());
    verify(discoveryService).updateCustomENRField("eth2", expectedEnrForkId.sszSerialize());
    verify(discoveryService)
        .updateCustomENRField("nfd", SszBytes4.of(nextForkDigest).sszSerialize());
  }

  @Test
  public void setForkInfo_futureBpoForkScheduledAtHardFork() {
    final ForkInfo fuluForkInfo = new ForkInfo(forks.get(6), genesisValidatorsRoot);
    final Bytes4 fuluForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, fuluForkInfo.getFork().getEpoch());
    final BlobParameters nextBpoFork = new BlobParameters(UInt64.valueOf(65_000), 64);
    final Fork nextHardFork = nextHardFork(fuluForkInfo, nextBpoFork.epoch());
    final Bytes4 nextForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, nextHardFork.getEpoch());

    discoveryNetwork.setForkInfo(
        fuluForkInfo,
        fuluForkDigest,
        Optional.of(nextHardFork),
        Optional.of(nextBpoFork),
        Optional.of(nextForkDigest));

    final EnrForkId expectedEnrForkId =
        new EnrForkId(fuluForkDigest, nextHardFork.getCurrentVersion(), nextHardFork.getEpoch());
    verify(discoveryService).updateCustomENRField("eth2", expectedEnrForkId.sszSerialize());
    verify(discoveryService)
        .updateCustomENRField("nfd", SszBytes4.of(nextForkDigest).sszSerialize());
  }

  @Test
  public void setForkInfo_futureHardForkScheduledBeforeBpoFork() {
    final ForkInfo fuluForkInfo = new ForkInfo(forks.get(6), genesisValidatorsRoot);
    final Bytes4 fuluForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, fuluForkInfo.getFork().getEpoch());
    final BlobParameters nextBpoFork = new BlobParameters(UInt64.valueOf(65_000), 64);
    final Fork nextHardFork = nextHardFork(fuluForkInfo, UInt64.valueOf(62_000));
    final Bytes4 nextForkDigest =
        spec.computeForkDigest(genesisValidatorsRoot, nextHardFork.getEpoch());

    discoveryNetwork.setForkInfo(
        fuluForkInfo,
        fuluForkDigest,
        Optional.of(nextHardFork),
        Optional.of(nextBpoFork),
        Optional.of(nextForkDigest));

    final EnrForkId expectedEnrForkId =
        new EnrForkId(fuluForkDigest, nextHardFork.getCurrentVersion(), nextHardFork.getEpoch());
    verify(discoveryService).updateCustomENRField("eth2", expectedEnrForkId.sszSerialize());
    verify(discoveryService)
        .updateCustomENRField("nfd", SszBytes4.of(nextForkDigest).sszSerialize());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void setForkInfoShouldAddPredicateToConnectionManager() {
    discoveryNetwork.setForkInfo(
        currentForkInfo, currentForkDigest, Optional.empty(), Optional.empty(), Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkDigest,
            currentForkInfo.getFork().getCurrentVersion(),
            SpecConfig.FAR_FUTURE_EPOCH);
    Bytes encodedForkId = expectedEnrForkId.sszSerialize();
    verify(discoveryService).updateCustomENRField("eth2", encodedForkId);
    ArgumentCaptor<Predicate<DiscoveryPeer>> peerPredicateArgumentCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    verify(connectionManager).addPeerPredicate(peerPredicateArgumentCaptor.capture());

    DiscoveryPeer peer1 = createDiscoveryPeer(Optional.of(expectedEnrForkId));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer1)).isTrue();

    final EnrForkId newEnrForkId1 =
        new EnrForkId(currentForkDigest, Bytes4.fromHexString("0xdeadbeef"), UInt64.ZERO);
    DiscoveryPeer peer2 = createDiscoveryPeer(Optional.of(newEnrForkId1));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer2)).isTrue();

    final EnrForkId newEnrForkId2 =
        new EnrForkId(
            Bytes4.fromHexString("0xdeadbeef"), Bytes4.fromHexString("0xdeadbeef"), UInt64.ZERO);
    DiscoveryPeer peer3 = createDiscoveryPeer(Optional.of(newEnrForkId2));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer3)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotConnectToPeerWithNoEnrForkId() {
    discoveryNetwork.setForkInfo(
        currentForkInfo, currentForkDigest, Optional.empty(), Optional.empty(), Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkDigest,
            currentForkInfo.getFork().getCurrentVersion(),
            SpecConfig.FAR_FUTURE_EPOCH);
    Bytes encodedForkId = expectedEnrForkId.sszSerialize();
    verify(discoveryService).updateCustomENRField("eth2", encodedForkId);
    ArgumentCaptor<Predicate<DiscoveryPeer>> peerPredicateArgumentCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    verify(connectionManager).addPeerPredicate(peerPredicateArgumentCaptor.capture());

    DiscoveryPeer peer1 = createDiscoveryPeer(Optional.empty());
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer1)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotConnectToPeersWhenNodeHasNoEnrForkId() {
    final EnrForkId enrForkId =
        new EnrForkId(
            currentForkDigest,
            currentForkInfo.getFork().getCurrentVersion(),
            SpecConfig.FAR_FUTURE_EPOCH);
    ArgumentCaptor<Predicate<DiscoveryPeer>> peerPredicateArgumentCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    verify(connectionManager).addPeerPredicate(peerPredicateArgumentCaptor.capture());

    DiscoveryPeer peer1 = createDiscoveryPeer(Optional.of(enrForkId));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer1)).isFalse();
  }

  @Test
  public void setForkInfoAtInitialization() {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final Bytes4 genesisForkVersion = genesisSpec.getConfig().getGenesisForkVersion();
    final EnrForkId enrForkId =
        new EnrForkId(
            genesisSpec.miscHelpers().computeForkDigest(genesisForkVersion, Bytes32.ZERO),
            genesisForkVersion,
            SpecConfig.FAR_FUTURE_EPOCH);
    verify(discoveryService).updateCustomENRField("eth2", enrForkId.sszSerialize());
  }

  @ParameterizedTest
  @MethodSource("provideNodeIds")
  public void nodeIdMustBeWrappedInUint256(final String nodeIdValue) {
    final Optional<Bytes> nodeId =
        Optional.of(Bytes.wrap(new BigInteger(nodeIdValue).toByteArray()));
    when(discoveryService.getNodeId()).thenReturn(nodeId);
    assertThat(discoveryNetwork.getDiscoveryNodeId()).isPresent();
    assertThat(discoveryNetwork.getDiscoveryNodeId().get().toBigInteger().toString())
        .isEqualTo(nodeIdValue);
  }

  @ParameterizedTest
  @MethodSource("getCgcFixtures")
  public void cgcIsCorrectlyEncoded(final String hexString, final Integer cgc) {
    discoveryNetwork.setDASTotalCustodyGroupCount(cgc);
    verify(discoveryService)
        .updateCustomENRField(DAS_CUSTODY_GROUP_COUNT_ENR_FIELD, Bytes.fromHexString(hexString));
  }

  @Test
  public void nfdIsCorrectlyEncoded() {
    final Bytes4 nfd = Bytes4.fromHexString("abcdef12");
    discoveryNetwork.setNextForkDigest(nfd);
    verify(discoveryService)
        .updateCustomENRField(NEXT_FORK_DIGEST_ENR_FIELD, SszBytes4.of(nfd).sszSerialize());
  }

  public DiscoveryPeer createDiscoveryPeer(final Optional<EnrForkId> maybeForkId) {
    final SszBitvector syncCommitteeSubnets =
        schemaDefinitions.getSyncnetsENRFieldSchema().getDefault();
    return new DiscoveryPeer(
        BLSPublicKey.empty().toSSZBytes(),
        Bytes32.ZERO,
        Optional.of(InetSocketAddress.createUnresolved("yo", 9999)),
        Optional.empty(),
        maybeForkId,
        SszBitvectorSchema.create(spec.getNetworkingConfig().getAttestationSubnetCount())
            .getDefault(),
        syncCommitteeSubnets,
        Optional.empty(),
        Optional.empty());
  }

  private Fork nextHardFork(final ForkInfo currentForkInfo, final UInt64 epoch) {
    return new Fork(
        currentForkInfo.getFork().getCurrentVersion(), Bytes4.fromHexString("0x07000001"), epoch);
  }

  public static Stream<Arguments> provideNodeIds() {
    return Stream.of(
        Arguments.of("19"),
        Arguments.of("434726285098"),
        Arguments.of("28805562758054575154484845"),
        Arguments.of(
            "57467522110468688239177851250859789869070302005900722885377252304169193209346"));
  }

  private static Stream<Arguments> getCgcFixtures() {
    return Stream.of(
        Arguments.of("0x", 0),
        Arguments.of("0x80", 128),
        Arguments.of("0x8c", 140),
        Arguments.of("0x0190", 400));
  }
}
