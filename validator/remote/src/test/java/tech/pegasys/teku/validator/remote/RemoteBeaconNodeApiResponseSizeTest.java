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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.validator.api.ValidatorConfig;

class RemoteBeaconNodeApiResponseSizeTest {
  private static final long MAX_RESPONSE_BYTES = 64L * 1_000_000;
  private final MockWebServer server = new MockWebServer();

  @BeforeEach
  void startServer() throws IOException {
    server.start();
  }

  @AfterEach
  void stopServer() throws IOException {
    server.shutdown();
  }

  @Test
  void rejectsResponseWithAdvertisedSizeAboveLimit() {
    server.enqueue(
        new MockResponse().setBody("small").setHeader("Content-Length", MAX_RESPONSE_BYTES + 1));
    final OkHttpClient client =
        RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 64);

    assertThatThrownBy(() -> client.newCall(request()).execute())
        .isInstanceOf(IOException.class)
        .hasMessageContaining("64 MB");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, ValidatorConfig.DEFAULT_EVENT_STREAM_MAX_EVENT_SIZE_MEGABYTES})
  void streamClientDoesNotApplyRestResponseLimit(final int maxEventSizeMegabytes)
      throws IOException {
    server.enqueue(
        new MockResponse().setBody("small").setHeader("Content-Length", MAX_RESPONSE_BYTES + 1));
    final OkHttpClient client =
        RemoteBeaconNodeApi.createOkHttpClientForStreamFromClient(
            RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 64),
            maxEventSizeMegabytes);

    assertThat(client.interceptors()).noneMatch(RestResponseSizeLimitInterceptor.class::isInstance);
    assertThat(client.networkInterceptors())
        .noneMatch(RestResponseSizeLimitInterceptor.class::isInstance);
    try (final Response response = client.newCall(request()).execute()) {
      assertThat(response.body().source().readUtf8(5)).isEqualTo("small");
    }
  }

  @Test
  void configuredLimitAppliesToRestClient() {
    server.enqueue(new MockResponse().setBody("small").setHeader("Content-Length", 1_000_001));
    final OkHttpClient client = RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 1);

    assertThatThrownBy(() -> client.newCall(request()).execute())
        .isInstanceOf(IOException.class)
        .hasMessageContaining("1 MB");
  }

  @Test
  void zeroDisablesRestResponseLimit() throws IOException {
    server.enqueue(
        new MockResponse().setBody("small").setHeader("Content-Length", MAX_RESPONSE_BYTES + 1));
    final OkHttpClient client = RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 0);

    assertThat(client.interceptors()).noneMatch(RestResponseSizeLimitInterceptor.class::isInstance);
    assertThat(client.networkInterceptors())
        .noneMatch(RestResponseSizeLimitInterceptor.class::isInstance);
    try (final Response response = client.newCall(request()).execute()) {
      assertThat(response.body().source().readUtf8(5)).isEqualTo("small");
    }
  }

  @Test
  void acceptsAdvertisedSizeAtConfiguredLimit() throws IOException {
    server.enqueue(new MockResponse().setBody("small").setHeader("Content-Length", 1_000_000));

    try (final Response response =
        RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 1)
            .newCall(request())
            .execute()) {
      assertThat(response.code()).isEqualTo(200);
    }
  }

  @Test
  void acceptsResponseAtExactLimit() throws IOException {
    server.enqueue(new MockResponse().setChunkedBody("a".repeat(1_000_000), 64 * 1024));

    try (final Response response = limitedClient(1).newCall(request()).execute()) {
      assertThat(response.body().string()).hasSize(1_000_000);
    }
  }

  @Test
  void rejectsChunkedResponseBeyondLimit() throws IOException {
    server.enqueue(new MockResponse().setChunkedBody("a".repeat(1_000_001), 64 * 1024));

    try (final Response response = limitedClient(1).newCall(request()).execute()) {
      assertThatThrownBy(() -> response.body().bytes())
          .isInstanceOf(IOException.class)
          .hasMessageContaining("1 MB");
    }
  }

  @Test
  void rejectsGzipResponseBasedOnInflatedBytes() throws IOException {
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (final GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write("a".repeat(1_000_001).getBytes(StandardCharsets.UTF_8));
    }
    assertThat(compressed.size()).isLessThan(1_000_000);
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Encoding", "gzip")
            .setBody(new Buffer().write(compressed.toByteArray())));

    try (final Response response =
        RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 1)
            .newCall(request())
            .execute()) {
      assertThatThrownBy(() -> response.body().string())
          .isInstanceOf(IOException.class)
          .hasMessageContaining("1 MB");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0x08, 0x10})
  void rejectsGzipResponseWithOversizedMetadataBeforeDecompression(final int flag)
      throws IOException {
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (final GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write("ok".getBytes(StandardCharsets.UTF_8));
    }
    final byte[] gzipBytes = compressed.toByteArray();
    gzipBytes[3] |= (byte) flag;
    final ByteArrayOutputStream withFilename = new ByteArrayOutputStream();
    withFilename.write(gzipBytes, 0, 10);
    withFilename.write("a".repeat(1_000_001).getBytes(StandardCharsets.UTF_8));
    withFilename.write(0);
    withFilename.write(gzipBytes, 10, gzipBytes.length - 10);
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Encoding", "gzip")
            .setChunkedBody(new Buffer().write(withFilename.toByteArray()), 8192));

    assertThatThrownBy(
            () -> {
              try (final Response response =
                  RemoteBeaconNodeApi.createOkHttpClient(List.of(server.url("/")), 1)
                      .newCall(request())
                      .execute()) {
                response.body().string();
              }
            })
        .isInstanceOf(IOException.class)
        .hasMessageContaining("1 MB");
  }

  private OkHttpClient limitedClient(final int maxMegabytes) {
    return new OkHttpClient.Builder()
        .addInterceptor(new RestResponseSizeLimitInterceptor(maxMegabytes))
        .build();
  }

  private Request request() {
    return new Request.Builder().url(server.url("/")).build();
  }
}
