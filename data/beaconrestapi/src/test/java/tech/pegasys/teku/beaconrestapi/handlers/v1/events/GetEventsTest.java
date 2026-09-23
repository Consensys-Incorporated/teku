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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TOPICS;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class GetEventsTest {

  private final Spec spec = TestSpecFactory.createDefault();

  private final GetEvents handler =
      new GetEvents(
          spec,
          mock(NodeDataProvider.class),
          mock(ChainDataProvider.class),
          mock(SyncDataProvider.class),
          mock(ConfigProvider.class),
          mock(EventChannels.class),
          new StubAsyncRunner(),
          StubTimeProvider.withTimeInMillis(1000),
          10);

  @Test
  void shouldRejectAnUnknownTopic() throws Exception {
    final StubRestApiRequest request = requestWithTopics("not_a_real_topic");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(((HttpErrorResponse) request.getResponseBody()).getMessage())
        .isEqualTo("Invalid topic: not_a_real_topic");
  }

  @Test
  void shouldRejectAKnownTopicAlongsideAnUnknownOne() throws Exception {
    // the whole subscription is rejected, so a client cannot end up silently missing one topic
    final StubRestApiRequest request = requestWithTopics(EventType.head.name(), "not_a_real_topic");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(((HttpErrorResponse) request.getResponseBody()).getMessage())
        .isEqualTo("Invalid topic: not_a_real_topic");
  }

  @Test
  void shouldRejectARequestWithoutTopics() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder().metadata(handler.getMetadata()).build();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(((HttpErrorResponse) request.getResponseBody()).getMessage())
        .isEqualTo("No topics supplied");
  }

  @Test
  void shouldRejectAnEmptyTopicsParameter() throws Exception {
    final StubRestApiRequest request = requestWithTopics("");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(((HttpErrorResponse) request.getResponseBody()).getMessage())
        .isEqualTo("No topics supplied");
  }

  @Test
  void shouldStartTheEventStreamForKnownTopics() {
    final StubRestApiRequest request =
        requestWithTopics(EventType.head.name(), EventType.attester_slashing.name());

    // the stub cannot serve an event stream, so reaching it is what proves the topics were accepted
    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private StubRestApiRequest requestWithTopics(final String... topics) {
    return StubRestApiRequest.builder()
        .metadata(handler.getMetadata())
        .listQueryParameter(TOPICS, List.of(topics))
        .build();
  }
}
