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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity.IdentityData;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class GetIdentityTest extends AbstractMigratedBeaconHandlerTest {
  private final MetadataMessage defaultMetadata =
      spec.getGenesisSchemaDefinitions().getMetadataMessageSchema().createDefault();
  private final IdentityData identityData =
      new IdentityData(
          "aeiou", Optional.empty(), List.of("address"), Collections.emptyList(), defaultMetadata);

  @BeforeEach
  void setUp() {
    setHandler(new GetIdentity(network, spec.getNetworkingConfig()));
  }

  @Test
  public void shouldReturnExpectedObjectType() throws Exception {
    NodeId nodeid = mock(NodeId.class);

    when(eth2P2PNetwork.getMetadata()).thenReturn(defaultMetadata);
    when(eth2P2PNetwork.getNodeId()).thenReturn(nodeid);
    when(nodeid.toBase58()).thenReturn("aeiou");
    when(eth2P2PNetwork.getNodeAddresses()).thenReturn(List.of("address"));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(identityData);
    assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
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
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, identityData);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"peer_id\":\"aeiou\",\"p2p_addresses\":[\"address\"],"
                + "\"discovery_addresses\":[],\"metadata\":{\"seq_number\":\"0\",\"attnets\":\"0x0000000000000000\"}}}");
  }
}
