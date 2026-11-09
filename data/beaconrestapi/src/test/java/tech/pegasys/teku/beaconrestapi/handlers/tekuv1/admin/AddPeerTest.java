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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;

class AddPeerTest extends AbstractMigratedBeaconHandlerTest {

  private final DiscoveryNetwork<?> discoveryNetwork = mock(DiscoveryNetwork.class);

  @BeforeEach
  public void setup() {
    setHandler(new AddPeer(network));
  }

  @Test
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
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
  public void shouldReturnOkWhenAValidListIsProvided() throws Exception {
    final String peerAddress = "/ip4/someIP/udp/9001/p2p/somePeerId";
    request.setRequestBody(peerAddress);
    when(network.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldReturnOkWhenRequestBodyIsEmpty() throws Exception {
    final String peerAddress = "";
    request.setRequestBody(peerAddress);
    when(network.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldReturnBadRequestIfInvalidPeerAddress() throws Exception {
    final String peerAddress = "invalid-peer-address";
    request.setRequestBody(peerAddress);
    when(network.getDiscoveryNetwork()).thenReturn(Optional.of(discoveryNetwork));
    doThrow(new IllegalArgumentException("Invalid peer address"))
        .when(discoveryNetwork)
        .addStaticPeer(peerAddress);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnInternalErrorWhenDiscoveryNetworkNotAvailable() throws Exception {
    final String peerAddress = "/ip4/someIP/udp/9001/p2p/somePeerId";
    request.setRequestBody(peerAddress);
    when(network.getDiscoveryNetwork()).thenReturn(Optional.empty());
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }
}
