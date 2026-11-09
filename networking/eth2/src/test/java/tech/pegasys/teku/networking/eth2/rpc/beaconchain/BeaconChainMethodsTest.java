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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcRequestDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconChainMethodsTest {

  private static final Bytes SSZ_RECORDED_STATUS_REQUEST_BYTES =
      Bytes.fromHexString(
          "0x54ff060000734e61507059002d00007e8c1ea2540000aa01007c30a903798306695d21d1faa76363a0070677130835e503760b0e84479b7819e6114b");
  private static final StatusMessage RECORDED_STATUS_MESSAGE_DATA =
      new StatusMessagePhase0(
          new Bytes4(Bytes.of(0, 0, 0, 0)),
          Bytes32.ZERO,
          UInt64.ZERO,
          Bytes32.fromHexString(
              "0x30A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E6"),
          UInt64.ZERO);

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final PeerLookup peerLookup = mock(PeerLookup.class);
  final AsyncRunner asyncRunner = new StubAsyncRunner();
  final CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
  final RecentChainData recentChainData = mock(RecentChainData.class);
  final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  final StatusMessageFactory statusMessageFactory =
      new StatusMessageFactory(spec, combinedChainDataClient, metricsSystem);
  final MetadataMessagesFactory metadataMessagesFactory = new MetadataMessagesFactory();

  @BeforeEach
  public void setUp() {
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
  }

  @Test
  void testStatusRoundtripSerialization() throws Exception {
    final BeaconChainMethods methods = getMethods();
    final StatusMessage expected =
        new StatusMessagePhase0(
            Bytes4.rightPad(Bytes.of(4)),
            Bytes32.random(),
            UInt64.ZERO,
            Bytes32.random(),
            UInt64.ZERO);

    final Bytes encoded = methods.status().encodeRequest(expected);
    final RpcRequestDecoder<StatusMessage> decoder = methods.status().createRequestDecoder();
    Optional<StatusMessage> decodedRequest = decoder.decodeRequest(Utils.toByteBuf(encoded));

    assertThat(decodedRequest).contains(expected);
  }

  @Test
  public void shouldDecodeStatusMessageRequest() throws Exception {
    final BeaconChainMethods methods = getMethods();
    final RpcRequestDecoder<StatusMessage> decoder = methods.status().createRequestDecoder();
    final Optional<StatusMessage> decodedRequest =
        decoder.decodeRequest(Utils.toByteBuf(SSZ_RECORDED_STATUS_REQUEST_BYTES));
    assertThat(decodedRequest).contains(RECORDED_STATUS_MESSAGE_DATA);
  }

  @Test
  public void shouldCreateVersionedBlocksByRangeMethodWithAltairEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalAltair());

    assertThat(methods.beaconBlocksByRange().getIds())
        .containsExactly("/eth2/beacon_chain/req/beacon_blocks_by_range/2/ssz_snappy");
  }

  @Test
  public void shouldCreateUnversionedBlocksByRangeMethodWithAltairDisabled() {
    final BeaconChainMethods methods = getMethods();

    assertThat(methods.beaconBlocksByRange().getIds())
        .containsExactly("/eth2/beacon_chain/req/beacon_blocks_by_range/2/ssz_snappy");
  }

  @Test
  public void shouldCreateVersionedBlocksByRootMethodWithAltairEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalAltair());

    assertThat(methods.beaconBlocksByRoot().getIds())
        .containsExactly("/eth2/beacon_chain/req/beacon_blocks_by_root/2/ssz_snappy");
  }

  @Test
  public void shouldNotCreateBlobSidecarsByRootWithDenebDisabled() {
    final BeaconChainMethods methods = getMethods();

    assertThat(methods.blobSidecarsByRoot()).isEmpty();
  }

  @Test
  public void shouldCreateBlobSidecarsByRootWithDenebEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalDeneb());

    assertThat(methods.blobSidecarsByRoot())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly("/eth2/beacon_chain/req/blob_sidecars_by_root/1/ssz_snappy"));
  }

  @Test
  public void shouldCreateBlobSidecarsByRangeWithDenebEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalDeneb());

    assertThat(methods.blobSidecarsByRange())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly("/eth2/beacon_chain/req/blob_sidecars_by_range/1/ssz_snappy"));
  }

  @Test
  public void shouldCreateStatusVersionWithSupportToVersion1BeforeFuluEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalElectra());

    assertThat(methods.status())
        .satisfies(
            method ->
                assertThat(method.getIds())
                    .containsExactly("/eth2/beacon_chain/req/status/1/ssz_snappy"));
  }

  @Test
  public void shouldCreateStatusVersionWithSupportToVersion1And2FromFuluEnableOnwards() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalFulu());

    assertThat(methods.status())
        .satisfies(
            method ->
                assertThat(method.getIds())
                    .contains(
                        "/eth2/beacon_chain/req/status/1/ssz_snappy",
                        "/eth2/beacon_chain/req/status/2/ssz_snappy"));
  }

  @Test
  public void shouldCreateExecutionPayloadEnvelopesByRootWithGloasEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalGloas());

    assertThat(methods.executionPayloadEnvelopesByRoot())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/execution_payload_envelopes_by_root/1/ssz_snappy"));
  }

  @Test
  public void shouldCreateExecutionPayloadEnvelopesByRangeWithGloasEnabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalGloas());

    assertThat(methods.executionPayloadEnvelopesByRange())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/execution_payload_envelopes_by_range/1/ssz_snappy"));
  }

  @Test
  void shouldNotRegisterLightClientMethodsWhenServerDisabled() {
    final BeaconChainMethods methods = getMethods(TestSpecFactory.createMinimalAltair());

    assertThat(methods.lightClientFinalityUpdate()).isEmpty();
    assertThat(methods.lightClientOptimisticUpdate()).isEmpty();
    assertThat(methods.lightClientUpdatesByRange()).isEmpty();
    assertThat(methods.lightClientBootstrap()).isEmpty();
  }

  @Test
  void shouldNotRegisterLightClientMethodsPreAltair() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final BeaconChainMethods methods =
        getMethods(spec, Optional.of(new LightClientUpdateStore(spec)));

    assertThat(methods.lightClientFinalityUpdate()).isEmpty();
    assertThat(methods.lightClientOptimisticUpdate()).isEmpty();
    assertThat(methods.lightClientUpdatesByRange()).isEmpty();
    assertThat(methods.lightClientBootstrap()).isEmpty();
  }

  @Test
  void shouldRegisterLightClientLatestUpdateMethods() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final BeaconChainMethods methods =
        getMethods(spec, Optional.of(new LightClientUpdateStore(spec)));

    assertThat(methods.lightClientFinalityUpdate())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/light_client_finality_update/1/ssz_snappy"));
    assertThat(methods.lightClientOptimisticUpdate())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/light_client_optimistic_update/1/ssz_snappy"));
  }

  @Test
  void shouldRegisterLightClientUpdatesByRange() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final BeaconChainMethods methods =
        getMethods(spec, Optional.of(new LightClientUpdateStore(spec)));

    assertThat(methods.lightClientUpdatesByRange())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly(
                        "/eth2/beacon_chain/req/light_client_updates_by_range/1/ssz_snappy"));
  }

  @Test
  void shouldRegisterLightClientBootstrap() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final BeaconChainMethods methods =
        getMethods(spec, Optional.of(new LightClientUpdateStore(spec)));

    assertThat(methods.lightClientBootstrap())
        .hasValueSatisfying(
            method ->
                assertThat(method.getIds())
                    .containsExactly("/eth2/beacon_chain/req/light_client_bootstrap/1/ssz_snappy"));
  }

  private BeaconChainMethods getMethods() {
    return getMethods(TestSpecFactory.createMinimalPhase0());
  }

  private BeaconChainMethods getMethods(final Spec spec) {
    return getMethods(spec, Optional.empty());
  }

  private BeaconChainMethods getMethods(
      final Spec spec, final Optional<LightClientUpdateStore> lightClientUpdateStore) {
    return BeaconChainMethods.create(
        spec,
        asyncRunner,
        peerLookup,
        combinedChainDataClient,
        () -> CustodyGroupCountManager.NOOP,
        recentChainData,
        metricsSystem,
        statusMessageFactory,
        metadataMessagesFactory,
        RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize()),
        DataColumnSidecarArchiveReconstructor.NOOP,
        DasReqRespLogger.NOOP,
        lightClientUpdateStore);
  }
}
