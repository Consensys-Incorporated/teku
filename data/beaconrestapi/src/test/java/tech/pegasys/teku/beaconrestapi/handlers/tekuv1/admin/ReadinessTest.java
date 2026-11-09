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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.REQUIRE_PREPARED_PROPOSERS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.REQUIRE_VALIDATOR_REGISTRATIONS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TARGET_PEER_COUNT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

public class ReadinessTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setHandler(
        new Readiness(
            syncDataProvider,
            chainDataProvider,
            network,
            nodeDataProvider,
            executionClientDataProvider));
    // ready by default
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(executionClientDataProvider.isExecutionClientAvailable()).thenReturn(true);
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReadyAndTargetPeerCountReached() throws Exception {
    request.setOptionalQueryParameter(TARGET_PEER_COUNT, "1");
    final Eth2Peer peer1 = mock(Eth2Peer.class);
    when(eth2P2PNetwork.streamPeers())
        .thenReturn(Stream.of(peer1, peer1))
        .thenReturn(Stream.of(peer1, peer1));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnUnavailableWhenStartingUp() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.START_UP);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnUnavailableWhenSyncing() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnBadRequestWhenWrongTargetPeerCountParam() {
    request.setOptionalQueryParameter(TARGET_PEER_COUNT, "a");

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReturnUnavailableWhenTargetPeerCountNotReached() throws Exception {
    request.setOptionalQueryParameter(TARGET_PEER_COUNT, "1234");
    final Eth2Peer peer1 = mock(Eth2Peer.class);
    when(eth2P2PNetwork.streamPeers()).thenReturn(Stream.of(peer1)).thenReturn(Stream.of(peer1));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnUnavailableWhenExecutionClientIsNotAvailable() throws Exception {
    when(executionClientDataProvider.isExecutionClientAvailable()).thenReturn(false);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnUnavailableWhenThereAreNoPreparedProposers() throws Exception {
    request.setOptionalQueryParameter(REQUIRE_PREPARED_PROPOSERS, "true");

    when(nodeDataProvider.getPreparedProposerInfo()).thenReturn(Map.of());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  public void shouldReturnUnavailableWhenThereAreNoValidatorRegistrations() throws Exception {
    request.setOptionalQueryParameter(REQUIRE_VALIDATOR_REGISTRATIONS, "true");

    when(nodeDataProvider.getValidatorRegistrationInfo()).thenReturn(Map.of());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataEmptyResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
