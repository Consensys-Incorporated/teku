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

package tech.pegasys.teku.ethereum.executionclient.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.metrics.MetricRecordingAbstractClient.RequestOutcome;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MetricRecordingAbstractClientTest {

  private static final UInt64 RESPONSE_DELAY = UInt64.valueOf(1300L);

  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  private final MetricsCountersByIntervals metricsCountersByIntervals =
      mock(MetricsCountersByIntervals.class);
  private final TestClient delegatedTestClient = mock(TestClient.class);
  private final TestClient clientTest =
      new TestClient(delegatedTestClient, stubTimeProvider, metricsCountersByIntervals);

  @Test
  public void shouldCountSuccessfulRequest() {
    final Response<String> response = Response.fromPayloadReceivedAsJson("value");
    setupResponse(SafeFuture.completedFuture(response));
    final SafeFuture<Response<String>> result = clientTest.testMethod("test");

    assertThat(result).isCompletedWithValue(response);

    verify(metricsCountersByIntervals)
        .recordValue(RESPONSE_DELAY, "testMethod", RequestOutcome.SUCCESS.toString());
    verifyNoMoreInteractions(metricsCountersByIntervals);
  }

  @Test
  public void shouldCountRequestWithFailedFutureResponse() {
    final RuntimeException exception = new RuntimeException("Nope");
    setupResponse(SafeFuture.failedFuture(exception));
    final SafeFuture<Response<String>> result = clientTest.testMethod("test");

    assertThat(result).isCompletedExceptionally();

    verify(metricsCountersByIntervals)
        .recordValue(RESPONSE_DELAY, "testMethod", RequestOutcome.ERROR.toString());
    verifyNoMoreInteractions(metricsCountersByIntervals);
  }

  @Test
  public void shouldCountRequestWithResponseFailure() {
    final Response<String> response = Response.fromErrorMessage("error");
    setupResponse(SafeFuture.completedFuture(response));
    final SafeFuture<Response<String>> result = clientTest.testMethod("test");

    assertThat(result).isCompletedWithValue(response);

    verify(metricsCountersByIntervals)
        .recordValue(RESPONSE_DELAY, "testMethod", RequestOutcome.ERROR.toString());
    verifyNoMoreInteractions(metricsCountersByIntervals);
  }

  private static class TestClient extends MetricRecordingAbstractClient {
    private final TestClient delegate;

    protected TestClient(
        final TestClient delegate,
        final TimeProvider timeProvider,
        final MetricsCountersByIntervals clientRequestsCountersByIntervals) {
      super(timeProvider, clientRequestsCountersByIntervals);
      this.delegate = delegate;
    }

    protected SafeFuture<Response<String>> testMethod(final String param) {
      return countRequest(() -> delegate.testMethod(param), "testMethod");
    }
  }

  private void setupResponse(final SafeFuture<Object> response) {
    when(delegatedTestClient.testMethod(any()))
        .thenAnswer(
            __ -> {
              stubTimeProvider.advanceTimeByMillis(RESPONSE_DELAY);
              return response;
            });
  }
}
