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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class EventChannels {

  private final ConcurrentMap<Class<?>, EventChannel<?>> channels = new ConcurrentHashMap<>();
  private final Function<Class<?>, EventChannel<?>> eventChannelFactory;

  public EventChannels(
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem,
      final boolean eventChannelVirtualThreadsEnabled) {
    this(
        channelInterface ->
            EventChannel.createAsync(
                channelInterface,
                exceptionHandler,
                metricsSystem,
                eventChannelVirtualThreadsEnabled));
  }

  public static EventChannels createSyncChannels(
      final ChannelExceptionHandler exceptionHandler, final MetricsSystem metricsSystem) {
    return new EventChannels(
        channelInterface -> EventChannel.create(channelInterface, exceptionHandler, metricsSystem));
  }

  EventChannels(final Function<Class<?>, EventChannel<?>> eventChannelFactory) {
    this.eventChannelFactory = eventChannelFactory;
  }

  /**
   * Creates a publisher to send events to an event channel. Unless this instance was created with
   * {@link #createSyncChannels(ChannelExceptionHandler, MetricsSystem)} calls will return
   * immediately and the event will be processed by subscribers on their own threads.
   *
   * <p>As the supplied {@code channelInterface} must be a {@link VoidReturningChannelInterface} all
   * its methods must return void.
   *
   * @param channelInterface the interface defining the channel
   * @param <T> the interface type
   * @return A publisher for the channel which implements {@code channelInterface}
   */
  public <T extends VoidReturningChannelInterface> T getPublisher(final Class<T> channelInterface) {
    return getChannel(channelInterface).getPublisher(Optional.empty());
  }

  /**
   * Creates a publisher to send events to an event channel. Unless this instance was created with
   * {@link #createSyncChannels(ChannelExceptionHandler, MetricsSystem)} calls will return
   * immediately and the event will be processed by subscribers on their own threads.
   *
   * <p>Any methods which return a future, will complete that future via {@code responseRunner}. As
   * a result, any handlers chained to the returned future via methods like {@link
   * tech.pegasys.teku.infrastructure.async.SafeFuture#thenApply(Function)} will be executed on one
   * of {@code responseRunner}'s threads.
   *
   * @param channelInterface the interface defining the channel
   * @param responseRunner the {@link AsyncRunner} to use when completing any returned futures
   * @param <T> the interface type
   * @return A publisher for the channel which implements {@code channelInterface}
   */
  public <T extends ChannelInterface> T getPublisher(
      final Class<T> channelInterface, final AsyncRunner responseRunner) {
    return getChannel(channelInterface).getPublisher(Optional.of(responseRunner));
  }

  public <T extends ChannelInterface> EventChannels subscribe(
      final Class<T> channelInterface, final T subscriber) {
    return subscribeMultithreaded(channelInterface, subscriber, 1);
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
   * @param channelInterface the channel to subscribe to
   * @param subscriber the subscriber to notify of events
   * @param requestedParallelism the number of threads to use to process events
   */
  public <T extends ChannelInterface> EventChannels subscribeMultithreaded(
      final Class<T> channelInterface, final T subscriber, final int requestedParallelism) {
    getChannel(channelInterface).subscribeMultithreaded(subscriber, requestedParallelism);
    return this;
  }

  @SuppressWarnings("unchecked")
  private <T extends ChannelInterface> EventChannel<T> getChannel(final Class<T> channelInterface) {
    return (EventChannel<T>) channels.computeIfAbsent(channelInterface, eventChannelFactory);
  }

  public SafeFuture<Void> stop() {
    return SafeFuture.allOf(channels.values().stream().map(EventChannel::stop));
  }
}
