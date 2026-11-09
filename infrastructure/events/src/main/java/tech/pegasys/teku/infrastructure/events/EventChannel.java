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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.joining;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class EventChannel<T> {

  private final Class<T> channelInterface;
  private final EventDeliverer<T> invoker;
  private final boolean allowMultipleSubscribers;
  private final AtomicBoolean hasSubscriber = new AtomicBoolean(false);

  private EventChannel(
      final Class<T> channelInterface,
      final EventDeliverer<T> invoker,
      final boolean allowMultipleSubscribers) {
    this.channelInterface = channelInterface;
    this.invoker = invoker;
    this.allowMultipleSubscribers = allowMultipleSubscribers;
  }

  static <T> EventChannel<T> create(
      final Class<T> channelInterface, final MetricsSystem metricsSystem) {
    return create(
        channelInterface, LoggingChannelExceptionHandler.LOGGING_EXCEPTION_HANDLER, metricsSystem);
  }

  static <T> EventChannel<T> create(
      final Class<T> channelInterface,
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem) {
    return create(channelInterface, new DirectEventDeliverer<>(exceptionHandler, metricsSystem));
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem,
      final boolean eventChannelVirtualThreadsEnabled) {
    final ExecutorService executor;
    if (eventChannelVirtualThreadsEnabled) {
      executor =
          Executors.newThreadPerTaskExecutor(
              Thread.ofVirtual().name(channelInterface.getSimpleName() + "-", 0).factory());
    } else {
      executor =
          Executors.newCachedThreadPool(
              new ThreadFactoryBuilder()
                  .setDaemon(true)
                  .setNameFormat(channelInterface.getSimpleName() + "-%d")
                  .build());
    }
    return createAsync(channelInterface, executor, exceptionHandler, metricsSystem);
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ExecutorService executor,
      final MetricsSystem metricsSystem) {
    return createAsync(
        channelInterface,
        executor,
        LoggingChannelExceptionHandler.LOGGING_EXCEPTION_HANDLER,
        metricsSystem);
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ExecutorService executor,
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem) {
    return create(
        channelInterface, new AsyncEventDeliverer<>(executor, exceptionHandler, metricsSystem));
  }

  private static <T> EventChannel<T> create(
      final Class<T> channelInterface, final EventDeliverer<T> eventDeliverer) {
    checkArgument(channelInterface.isInterface(), "Must provide an interface for the channel");
    final String illegalMethods =
        Stream.of(channelInterface.getMethods())
            .filter(method -> !isReturnTypeAllowed(method) || method.getExceptionTypes().length > 0)
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .map(Method::getName)
            .collect(joining(", "));
    checkArgument(
        illegalMethods.isEmpty(),
        "All methods must have a return type that is void or compatible with SafeFuture and no exceptions but %s did not",
        illegalMethods);
    final boolean hasReturnValues =
        Stream.of(channelInterface.getMethods())
            .anyMatch(method -> hasAllowedAsyncReturnValue(method.getReturnType()));
    if (hasReturnValues && VoidReturningChannelInterface.class.isAssignableFrom(channelInterface)) {
      throw new IllegalArgumentException(
          "Channel interface extends "
              + VoidReturningChannelInterface.class.getSimpleName()
              + " but has non-void return types");
    }

    return new EventChannel<>(channelInterface, eventDeliverer, !hasReturnValues);
  }

  private static boolean isReturnTypeAllowed(final Method method) {
    final Class<?> returnType = method.getReturnType();
    // Allow void
    return returnType.equals(Void.TYPE)
        // Allow Future, CompletableStage or CompletableFuture
        || hasAllowedAsyncReturnValue(returnType);
  }

  private static boolean hasAllowedAsyncReturnValue(final Class<?> returnType) {
    return Future.class.isAssignableFrom(returnType)
        && returnType.isAssignableFrom(SafeFuture.class);
  }

  T getPublisher(final Optional<AsyncRunner> responseRunner) {
    @SuppressWarnings("unchecked")
    final T publisher =
        (T)
            Proxy.newProxyInstance(
                channelInterface.getClassLoader(),
                new Class<?>[] {channelInterface},
                (proxy, method, args) -> invoker.invoke(proxy, method, args, responseRunner));
    return publisher;
  }

  void subscribe(final T listener) {
    subscribeMultithreaded(listener, 1);
  }

  /**
   * Adds a subscriber to this channel where events are handled by multiple threads concurrently.
   *
   * <p>Note that only async event channels can use multiple threads. Synchronous channels will
   * always use the publisher thread to process events.
   *
   * <p>Events are still placed into an ordered queue and started in order, but as multiple threads
   * pull from the queue, the execution order can no longer be guaranteed.
   *
   * @param listener the listener to notify of events
   * @param requestedParallelism the number of threads to use to process events
   */
  void subscribeMultithreaded(final T listener, final int requestedParallelism) {
    checkArgument(requestedParallelism > 0, "Number of threads must be at least 1");
    if (!hasSubscriber.compareAndSet(false, true) && !allowMultipleSubscribers) {
      throw new IllegalStateException("Only one subscriber is supported by this event channel");
    }
    invoker.subscribe(listener, requestedParallelism);
  }

  public SafeFuture<Void> stop() {
    return invoker.stop();
  }
}
