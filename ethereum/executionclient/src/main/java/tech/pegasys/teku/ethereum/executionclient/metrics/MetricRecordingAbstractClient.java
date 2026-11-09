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

import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public abstract class MetricRecordingAbstractClient {
  private final TimeProvider timeProvider;
  private final MetricsCountersByIntervals clientRequestsCountersByIntervals;

  protected MetricRecordingAbstractClient(
      final TimeProvider timeProvider,
      final MetricsCountersByIntervals clientRequestsCountersByIntervals) {
    this.timeProvider = timeProvider;
    this.clientRequestsCountersByIntervals = clientRequestsCountersByIntervals;
  }

  protected <T> SafeFuture<Response<T>> countRequest(
      final RequestRunner<T> requestRunner, final String method) {
    final UInt64 startTime = timeProvider.getTimeInMillis();
    return requestRunner
        .run()
        .catchAndRethrow(__ -> recordRequestError(startTime, method))
        .thenPeek(
            response -> {
              if (response.isFailure()) {
                recordRequestError(startTime, method);
              } else {
                recordRequestSuccess(startTime, method);
              }
            });
  }

  private void recordRequestSuccess(final UInt64 startTime, final String method) {
    recordRequest(startTime, method, RequestOutcome.SUCCESS);
  }

  private void recordRequestError(final UInt64 startTime, final String method) {
    recordRequest(startTime, method, RequestOutcome.ERROR);
  }

  private void recordRequest(
      final UInt64 startTime, final String method, final RequestOutcome requestOutcome) {
    final UInt64 duration = timeProvider.getTimeInMillis().minusMinZero(startTime);
    clientRequestsCountersByIntervals.recordValue(duration, method, requestOutcome.toString());
  }

  @FunctionalInterface
  protected interface RequestRunner<T> {
    SafeFuture<Response<T>> run();
  }

  protected enum RequestOutcome {
    SUCCESS("success"),
    ERROR("error");

    private final String displayName;

    RequestOutcome(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
