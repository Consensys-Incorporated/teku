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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.client.loader.PublicKeyLoader.EXTERNAL_SIGNER_SOURCE_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader.ExternalSignerException;

public class PublicKeyLoaderTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final String firstKeyStr =
      dataStructureUtil.randomPublicKey().toBytesCompressed().toHexString();
  private final String secondKeyStr =
      dataStructureUtil.randomPublicKey().toBytesCompressed().toHexString();
  private final String urlSource = "http://my.host";
  private final BLSPublicKey firstKey =
      BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(firstKeyStr));
  private final BLSPublicKey secondKey =
      BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(secondKeyStr));

  private final URL externalSignerUrl = URI.create("http://external.sigener").toURL();
  private final ObjectMapper mapper = mock(ObjectMapper.class);
  private final HttpClient httpClient = mock(HttpClient.class);

  @SuppressWarnings("unchecked")
  private final HttpResponse<String> externalSignerHttpResponse = mock(HttpResponse.class);

  private final Supplier<HttpClient> externalSignerHttpClientSupplier = () -> httpClient;

  private final PublicKeyLoader loader =
      new PublicKeyLoader(mapper, externalSignerHttpClientSupplier, externalSignerUrl);

  public PublicKeyLoaderTest() throws MalformedURLException {}

  @BeforeEach
  void setUp() throws IOException, InterruptedException {
    // URL response
    final String[] values = {firstKeyStr, secondKeyStr};
    when(mapper.readValue(URI.create(urlSource).toURL(), String[].class)).thenReturn(values);

    // external signer response
    final String jsonValues = String.format("[%s, %s]", firstKeyStr, secondKeyStr);
    when(externalSignerHttpResponse.body()).thenReturn(jsonValues);
    when(externalSignerHttpResponse.statusCode()).thenReturn(200);
    when(mapper.readValue(jsonValues, String[].class)).thenReturn(values);
    when(httpClient.send(any(), ArgumentMatchers.<BodyHandler<String>>any()))
        .thenReturn(externalSignerHttpResponse);
  }

  @Test
  void shouldGetListOfLocallySpecifiedPubKeys() {
    assertThat(loader.getPublicKeys(List.of(firstKeyStr, secondKeyStr)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldRemoveDuplicateKeysFromLocalList() {
    assertThat(loader.getPublicKeys(List.of(firstKeyStr, secondKeyStr, firstKeyStr)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldReadFromUrl() {
    assertThat(loader.getPublicKeys(List.of(urlSource))).containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldReadFromExternalSigner() {
    assertThat(loader.getPublicKeys(List.of(EXTERNAL_SIGNER_SOURCE_ID)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldHandleDuplicatesAcrossSources() {
    assertThat(
            loader.getPublicKeys(
                List.of(firstKeyStr, urlSource, secondKeyStr, EXTERNAL_SIGNER_SOURCE_ID)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldHandleEmptyResponseFromUrl() throws IOException {
    final String[] values = {};
    when(mapper.readValue(URI.create(urlSource).toURL(), String[].class)).thenReturn(values);
    assertThat(loader.getPublicKeys(List.of(urlSource, secondKeyStr))).containsExactly(secondKey);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenUrlFailsToLoad() throws Exception {
    final UnknownHostException exception = new UnknownHostException("Unknown host");
    when(mapper.readValue(URI.create(urlSource).toURL(), String[].class)).thenThrow(exception);
    assertThatThrownBy(() -> loader.getPublicKeys(List.of(urlSource)))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasRootCause(exception);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenExternalSignerReturnsNon200() throws Exception {
    when(externalSignerHttpResponse.statusCode()).thenReturn(400);
    assertThatThrownBy(() -> loader.getPublicKeys(List.of(EXTERNAL_SIGNER_SOURCE_ID)))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasRootCauseInstanceOf(ExternalSignerException.class);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenExternalSignerHttpClientThrows()
      throws Exception {
    when(httpClient.send(any(), ArgumentMatchers.<BodyHandler<String>>any()))
        .thenThrow(new IOException("error"));
    assertThatThrownBy(() -> loader.getPublicKeys(List.of(EXTERNAL_SIGNER_SOURCE_ID)))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasRootCauseInstanceOf(IOException.class);
  }
}
