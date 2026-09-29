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

package tech.pegasys.teku.beaconrestapi.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.GetEvents;

/**
 * The event stream handler commits the {@code 200} response as soon as the stream starts, so the
 * topics have to be rejected before that. These tests run against the real server to make sure a
 * client is told about a bad subscription instead of being handed a stream which never delivers
 * anything.
 */
public class GetEventsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

  private final OkHttpClient streamClient =
      new OkHttpClient.Builder()
          .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
          .build();

  @Test
  public void shouldRejectAnUnknownTopic() throws IOException {
    startRestAPIAtGenesis();

    try (final Response response = subscribe("not_a_real_topic")) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      assertThat(response.body().string()).contains("Invalid topic: not_a_real_topic");
    }
  }

  @Test
  public void shouldRejectAKnownTopicAlongsideAnUnknownOne() throws IOException {
    startRestAPIAtGenesis();

    try (final Response response = subscribe("head,not_a_real_topic")) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      assertThat(response.body().string()).contains("Invalid topic: not_a_real_topic");
    }
  }

  @Test
  public void shouldRejectARequestWithoutTopics() throws IOException {
    startRestAPIAtGenesis();

    final Request request =
        new Request.Builder()
            .url(getUrl(GetEvents.ROUTE))
            .header("Accept", "text/event-stream")
            .build();
    try (final Response response = streamClient.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      assertThat(response.body().string()).contains("No topics supplied");
    }
  }

  @Test
  public void shouldAcceptKnownTopics() throws IOException {
    startRestAPIAtGenesis();

    try (final Response response = subscribe("head,attester_slashing")) {
      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.header("Content-Type")).contains("text/event-stream");
    }
  }

  private Response subscribe(final String topics) throws IOException {
    final Request request =
        new Request.Builder()
            .url(getUrl(GetEvents.ROUTE) + "?topics=" + topics)
            .header("Accept", "text/event-stream")
            .build();
    return streamClient.newCall(request).execute();
  }
}
