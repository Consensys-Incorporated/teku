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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;

abstract class EventDeliverer<T> {
  private final Subscribers<T> subscribers = Subscribers.create(true);
  private final LabelledMetric<Counter> publishedEventCounter;

  protected EventDeliverer(final MetricsSystem metricsSystem) {
    publishedEventCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.EVENTBUS,
            "event_published_count_total",
            "Total number of events published",
            "channel");
  }

  void subscribe(final T subscriber, final int numberOfThreads) {
    subscribers.subscribe(subscriber);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public Object invoke(
      final Object proxy,
      final Method method,
      final Object[] args,
      final Optional<AsyncRunner> responseRunner) {
    if (method.getDeclaringClass().equals(Object.class)) {
      try {
        return method.invoke(this, args);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
    publishedEventCounter.labels(method.getDeclaringClass().getSimpleName()).inc();
    if (method.getReturnType().equals(Void.TYPE)) {
      subscribers.forEach(subscriber -> deliverTo(subscriber, method, args));
      return null;
    } else {
      final SafeFuture<T> result = new SafeFuture<>();
      subscribers.forEach(
          subscriber -> {
            final SafeFuture<T> response =
                deliverToWithResponse(subscriber, method, args, responseRunner.orElseThrow());
            response.propagateTo(result);
          });
      return result;
    }
  }

  protected abstract void deliverTo(T subscriber, Method method, Object[] args);

  protected abstract <X> SafeFuture<X> deliverToWithResponse(
      T subscriber, Method method, Object[] args, AsyncRunner responseRunner);

  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }
}
