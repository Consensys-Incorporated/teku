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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.google.common.base.Splitter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

public class ExternalSignerBasicAuthIntegrationTest
    extends AbstractSecureExternalSignerIntegrationTest {

  private final String username = "foo";
  private final String password = "bar";

  @Override
  protected URL getUrl() throws MalformedURLException {
    return URI.create(
            String.format("https://%s:%s@127.0.0.1:%s", username, password, client.getLocalPort()))
        .toURL();
  }

  @Test
  public void addsBasicAuthorizationToSigningRequest() {

    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));

    // This will trigger the HttpClient to include the "Authorization" header in the next request
    // and is the normal behaviour of servers
    client
        .when(request().withSecure(true), Times.exactly(1))
        .respond(response().withStatusCode(401).withHeader("WWW-Authenticate", "Basic"));
    client
        .when(request().withSecure(true))
        .respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final HttpRequest[] recordedRequests = client.retrieveRecordedRequests(request());
    assertThat(recordedRequests).hasSize(2);
    // second request will be the authenticated one
    final HttpRequest recordedRequest = recordedRequests[1];

    // verify Authorization header
    final String recordedAuthHeader = recordedRequest.getFirstHeader("Authorization");
    final String decodedAuthHeader =
        new String(
            Base64.getDecoder().decode(Splitter.on(' ').splitToList(recordedAuthHeader).get(1)),
            StandardCharsets.UTF_8);
    assertThat(decodedAuthHeader).isEqualTo(username + ":" + password);
  }
}
