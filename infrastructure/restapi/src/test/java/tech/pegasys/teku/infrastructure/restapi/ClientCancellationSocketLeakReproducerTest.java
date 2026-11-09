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

package tech.pegasys.teku.infrastructure.restapi;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse.respondOk;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.get;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.management.UnixOperatingSystemMXBean;
import io.javalin.Javalin;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

@Timeout(60)
class ClientCancellationSocketLeakReproducerTest {
  private static final String PATH = "/eth/v1/validator/attestation_data";
  private static final String REQUEST_PATH = PATH + "?slot=1&committee_index=0";
  private static final int REQUEST_COUNT = 8;
  private static final int ASYNC_TIMEOUT_MILLIS = 1_000;

  private final DelayedEndpoint endpoint = new DelayedEndpoint();
  private RestApi restApi;
  private ServerConnector connector;
  private long initialFileDescriptorCount;

  @AfterEach
  void stopRestApi() throws Exception {
    if (restApi != null) {
      waitFor(restApi.stop());
    }
  }

  @Test
  void cancelledRequestsAreReleasedWithoutCancellingEndpointFutures() throws Exception {
    startRestApi();

    final List<SafeFuture<AsyncApiResponse>> pendingResponses = cancelRequests();

    try {
      waitFor(() -> assertThat(openConnectionCount()).isZero(), 10, SECONDS);
      assertThat(pendingResponses).allMatch(response -> !response.isDone());
      assertThat(openFileDescriptorCount()).isLessThan(initialFileDescriptorCount + REQUEST_COUNT);
    } finally {
      pendingResponses.forEach(response -> response.complete(respondOk("attestation data")));
    }
  }

  @Test
  void cancelledRequestsAreReleasedWhenEndpointFuturesComplete() throws Exception {
    startRestApi();

    final List<SafeFuture<AsyncApiResponse>> pendingResponses = cancelRequests();

    pendingResponses.forEach(response -> response.complete(respondOk("attestation data")));
    waitFor(() -> assertThat(openConnectionCount()).isZero(), 10, SECONDS);
  }

  private void startRestApi() throws Exception {
    restApi =
        new RestApiBuilder()
            .port(0)
            .asyncTimeoutMillis(ASYNC_TIMEOUT_MILLIS)
            .endpoint(endpoint)
            .build();
    waitFor(restApi.start());
    connector = (ServerConnector) getJavalin(restApi).jettyServer().server().getConnectors()[0];
    initialFileDescriptorCount = openFileDescriptorCount();
  }

  private List<SafeFuture<AsyncApiResponse>> cancelRequests() throws Exception {
    final List<SafeFuture<AsyncApiResponse>> pendingResponses = new ArrayList<>();
    for (int i = 0; i < REQUEST_COUNT; i++) {
      pendingResponses.add(sendRequestAndCancel());
    }
    return pendingResponses;
  }

  private SafeFuture<AsyncApiResponse> sendRequestAndCancel() throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress(InetAddress.getLoopbackAddress(), restApi.getListenPort()));
      socket
          .getOutputStream()
          .write(
              ("GET "
                      + REQUEST_PATH
                      + " HTTP/1.1\r\n"
                      + "Host: localhost\r\n"
                      + "Accept: application/json\r\n"
                      + "Accept-Encoding: gzip\r\n"
                      + "\r\n")
                  .getBytes(US_ASCII));
      socket.getOutputStream().flush();
      final SafeFuture<AsyncApiResponse> response = endpoint.awaitPendingResponse();
      // Force the server to observe the cancellation instead of a graceful client disconnect.
      socket.setSoLinger(true, 0);
      return response;
    }
  }

  private int openConnectionCount() {
    return connector.getConnectedEndPoints().size();
  }

  private long openFileDescriptorCount() {
    return ((UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
        .getOpenFileDescriptorCount();
  }

  private static Javalin getJavalin(final RestApi restApi)
      throws NoSuchFieldException, IllegalAccessException {
    final Field appField = RestApi.class.getDeclaredField("app");
    appField.setAccessible(true);
    return (Javalin) appField.get(restApi);
  }

  private static class DelayedEndpoint extends RestApiEndpoint {
    private static final EndpointMetadata METADATA =
        get(PATH)
            .operationId("socketLeakReproducer")
            .summary("Socket leak reproducer")
            .description("Returns a response after the client has disconnected")
            .response(SC_OK, "Success", STRING_TYPE)
            .build();

    private final BlockingQueue<SafeFuture<AsyncApiResponse>> pendingResponses =
        new LinkedBlockingQueue<>();

    private DelayedEndpoint() {
      super(METADATA);
    }

    @Override
    public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
      final SafeFuture<AsyncApiResponse> response = new SafeFuture<>();
      pendingResponses.add(response);
      request.respondAsync(response);
    }

    private SafeFuture<AsyncApiResponse> awaitPendingResponse() throws InterruptedException {
      final SafeFuture<AsyncApiResponse> response = pendingResponses.poll(10, SECONDS);
      assertThat(response).as("server should receive the request").isNotNull();
      return response;
    }
  }
}
