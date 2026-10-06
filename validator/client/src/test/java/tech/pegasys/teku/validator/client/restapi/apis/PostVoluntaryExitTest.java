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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PUBKEY;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.VoluntaryExitDataProvider;

public class PostVoluntaryExitTest {
  private final VoluntaryExitDataProvider provider = mock(VoluntaryExitDataProvider.class);
  private final KeyManager keyManager = mock(KeyManager.class);
  private final Validator validator = mock(Validator.class);
  private final PostVoluntaryExit handler = new PostVoluntaryExit(provider, keyManager);
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubRestApiRequest request = new StubRestApiRequest(handler.getMetadata());

  final VoluntaryExit message = new VoluntaryExit(UInt64.valueOf(123), UInt64.ZERO);
  final SignedVoluntaryExit signedVoluntaryExit =
      new SignedVoluntaryExit(message, dataStructureUtil.randomSignature());

  @BeforeEach
  void setUp() {
    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.of(validator));
  }

  @Test
  void shouldReturnNotFoundWhenValidatorIsNotOwned() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    when(keyManager.getValidatorByPublicKey(publicKey)).thenReturn(Optional.empty());
    request.setPathParameter(PUBKEY, publicKey.toString());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldCallProviderWhenEpochNotProvided() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    request.setPathParameter("pubkey", publicKey.toString());

    when(provider.getSignedVoluntaryExit(eq(publicKey), eq(Optional.empty())))
        .thenReturn(SafeFuture.completedFuture(signedVoluntaryExit));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(signedVoluntaryExit);
  }

  @Test
  void shouldCallProviderWhenEpochProvided() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    request.setPathParameter(PUBKEY, publicKey.toString());
    request.setOptionalQueryParameter(EPOCH, "1234");

    when(provider.getSignedVoluntaryExit(eq(publicKey), eq(Optional.of(UInt64.valueOf(1234)))))
        .thenReturn(SafeFuture.completedFuture(signedVoluntaryExit));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(signedVoluntaryExit);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle401() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_UNAUTHORIZED);
  }

  @Test
  void metadata_shouldHandle403() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_FORBIDDEN);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    final String data = getResponseStringFromMetadata(handler, SC_OK, exit);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"message\":"
                + "{\"epoch\":\""
                + exit.getMessage().getEpoch()
                + "\","
                + "\"validator_index\":\""
                + exit.getMessage().getValidatorIndex()
                + "\"},"
                + "\"signature\":\""
                + exit.getSignature()
                + "\"}}");
  }
}
