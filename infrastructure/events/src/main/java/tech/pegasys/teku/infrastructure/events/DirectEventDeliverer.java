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

package tech.pegasys.teku.infrastructure.events;

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EVENTBUS;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class DirectEventDeliverer<T> extends EventDeliverer<T> {
  private final ChannelExceptionHandler exceptionHandler;
  private final LabelledMetric<Counter> consumedEventCounter;
  private final LabelledMetric<Counter> failedEventCounter;

  DirectEventDeliverer(
      final ChannelExceptionHandler exceptionHandler, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.exceptionHandler = exceptionHandler;
    consumedEventCounter =
        metricsSystem.createLabelledCounter(
            EVENTBUS,
            "event_consumed_count_total",
            "Total number of events consumed",
            "channel",
            "subscriber");
    failedEventCounter =
        metricsSystem.createLabelledCounter(
            EVENTBUS,
            "event_failed_count_total",
            "Number of events which failed to be processed",
            "channel",
            "subscriber");
  }

  @Override
  protected void deliverTo(final T subscriber, final Method method, final Object[] args) {
    // The response will be null as the method is void so we can just ignore the result.
    final SafeFuture<Object> result = executeMethod(subscriber, method, args);
    if (result != null) {
      result.finish(
          () -> {}, error -> exceptionHandler.handleException(error, subscriber, method, args));
    }
  }

  @Override
  protected <X> SafeFuture<X> deliverToWithResponse(
      final T subscriber,
      final Method method,
      final Object[] args,
      final AsyncRunner responseRunner) {
    return executeMethod(subscriber, method, args);
  }

  @SuppressWarnings("unchecked")
  private <X> SafeFuture<X> executeMethod(
      final T subscriber, final Method method, final Object[] args) {
    try {
      return (SafeFuture<X>) method.invoke(subscriber, args);
    } catch (IllegalAccessException e) {
      incrementCounter(failedEventCounter, subscriber, method);
      return SafeFuture.failedFuture(e);
    } catch (InvocationTargetException e) {
      incrementCounter(failedEventCounter, subscriber, method);
      return SafeFuture.failedFuture(e.getTargetException());
    } finally {
      incrementCounter(consumedEventCounter, subscriber, method);
    }
  }

  private void incrementCounter(
      final LabelledMetric<Counter> counter, final T subscriber, final Method method) {
    counter
        .labels(method.getDeclaringClass().getSimpleName(), subscriber.getClass().getSimpleName())
        .inc();
  }
}
