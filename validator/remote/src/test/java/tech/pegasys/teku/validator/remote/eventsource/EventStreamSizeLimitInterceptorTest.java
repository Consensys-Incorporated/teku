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

package tech.pegasys.teku.validator.remote.eventsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

class EventStreamSizeLimitInterceptorTest {

  private static final int MAX_EVENT_SIZE_MEGABYTES = 1;
  private static final int MAX_EVENT_SIZE_BYTES =
      MAX_EVENT_SIZE_MEGABYTES * EventStreamSizeLimitInterceptor.BYTES_PER_MEGABYTE;

  @Test
  void rejectsAnUnterminatedDataLineBeforeDispatch() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setChunkedBody("event: head\ndata: " + "x".repeat(MAX_EVENT_SIZE_BYTES), 137));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThatThrownBy(eventSource::readMessage)
            .hasRootCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("Beacon node event stream event exceeded 1 MB");
      }
    }
  }

  @Test
  void acceptsManyEventsWhoseCombinedSizeExceedsTheLimit() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final String data = "x".repeat(600_000);
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setChunkedBody(
                  "event: head\r\ndata: "
                      + data
                      + "\r\n\r\n"
                      + "event: head\r\ndata: "
                      + data
                      + "\r\n\r\n",
                  173));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
      }
    }
  }

  @Test
  void acceptsAnEventExactlyAtTheLimit() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final String prefix = "event: head\ndata: ";
      final String data = "x".repeat(MAX_EVENT_SIZE_BYTES - prefix.length() - 2);
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setBody(prefix + data + "\n\n"));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
      }
    }
  }

  @Test
  void acceptsConsecutiveCrLfEventsExactlyAtTheLimit() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final String prefix = "event: head\r\ndata: ";
      final String suffix = "\r\n\r\n";
      final String data = "x".repeat(MAX_EVENT_SIZE_BYTES - prefix.length() - suffix.length());
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setBody((prefix + data + suffix).repeat(2)));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
      }
    }
  }

  @Test
  void acceptsEventsWhenCrLfIsSplitAcrossHttpChunks() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final int chunkSize = 8192;
      final String prefix = "event: head\r\ndata: ";
      final String data = "x".repeat(chunkSize * 74 - prefix.length() - 1);
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setChunkedBody((prefix + data + "\r\n\r\n").repeat(2), chunkSize));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
      }
    }
  }

  @Test
  void rejectsAnOversizedCommentWithoutAnyData() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setBody(":" + "x".repeat(MAX_EVENT_SIZE_BYTES)));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThatThrownBy(eventSource::readMessage)
            .hasRootCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("Beacon node event stream event exceeded 1 MB");
      }
    }
  }

  @Test
  void limitsDecompressedEventBytes() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (final GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
        gzip.write(
            ("event: head\ndata: " + "x".repeat(MAX_EVENT_SIZE_BYTES))
                .getBytes(StandardCharsets.UTF_8));
      }
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setHeader("Content-Encoding", "gzip")
              .setBody(new Buffer().write(bytes.toByteArray())));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThatThrownBy(eventSource::readMessage)
            .hasRootCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("Beacon node event stream event exceeded 1 MB");
      }
    }
  }

  @Test
  void resetsAfterCarriageReturnSeparatedEvents() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final String data = "x".repeat(600_000);
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setChunkedBody(
                  "event: head\rdata: " + data + "\r\r" + "event: head\rdata: " + data + "\r\r",
                  137));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
      }
    }
  }

  @Test
  void countsMultipleDataLinesAsOneEvent() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final String data = "x".repeat(600_000);
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setBody("event: head\ndata: " + data + "\ndata: " + data + "\n\n"));
      server.start();

      try (final EventSource eventSource = eventSource(server)) {
        assertThatThrownBy(eventSource::readMessage)
            .hasRootCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("Beacon node event stream event exceeded 1 MB");
      }
    }
  }

  @Test
  void disablingLimitAcceptsOversizedEvent() throws Exception {
    try (final MockWebServer server = new MockWebServer()) {
      final String data = "x".repeat(MAX_EVENT_SIZE_BYTES + 1);
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setBody("event: head\ndata: " + data + "\n\n"));
      server.start();

      try (final EventSource eventSource = eventSource(server, 0)) {
        assertThat(eventSource.readMessage().getData()).isEqualTo(data);
      }
    }
  }

  private EventSource eventSource(final MockWebServer server) {
    return eventSource(server, MAX_EVENT_SIZE_MEGABYTES);
  }

  private EventSource eventSource(final MockWebServer server, final int maxEventSizeMegabytes) {
    final OkHttpClient client =
        RemoteBeaconNodeApi.createOkHttpClientForStreamFromClient(
            new OkHttpClient(), maxEventSizeMegabytes);
    return new EventSource.Builder(
            ConnectStrategy.http(server.url("/eth/v1/events")).httpClient(client))
        .build();
  }
}
