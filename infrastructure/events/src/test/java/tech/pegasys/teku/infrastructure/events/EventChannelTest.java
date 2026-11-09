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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.AsyncEventDeliverer.QueueReader;

class EventChannelTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final ChannelExceptionHandler exceptionHandler = mock(ChannelExceptionHandler.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private ExecutorService executor;

  @AfterEach
  public void tearDown() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  public void shouldRejectClassesThatAreNotInterfaces() {
    assertThatThrownBy(() -> EventChannel.create(String.class, metricsSystem))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectInterfacesWithNonVoidMethods() {
    assertThatThrownBy(() -> EventChannel.create(Supplier.class, metricsSystem))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectInterfacesWithDeclaredExceptions() {
    assertThatThrownBy(() -> EventChannel.create(WithException.class, metricsSystem))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldDeliverCallsToSubscribers() {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class, metricsSystem);
    final Runnable subscriber = mock(Runnable.class);
    channel.subscribe(subscriber);

    channel.getPublisher(Optional.empty()).run();

    verify(subscriber).run();
  }

  @Test
  public void shouldDeliverCallsToMultipleSubscribers() {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class, metricsSystem);
    final Runnable subscriber1 = mock(Runnable.class);
    final Runnable subscriber2 = mock(Runnable.class);
    channel.subscribe(subscriber1);
    channel.subscribe(subscriber2);

    channel.getPublisher(Optional.empty()).run();

    verify(subscriber1).run();
    verify(subscriber2).run();
  }

  @Test
  public void shouldDeliverCallsToCorrectMethod() {
    final EventChannel<MultipleMethods> channel =
        EventChannel.create(MultipleMethods.class, metricsSystem);
    final MultipleMethods subscriber = mock(MultipleMethods.class);
    channel.subscribe(subscriber);

    channel.getPublisher(Optional.empty()).method2();
    verify(subscriber).method2();
    verifyNoMoreInteractions(subscriber);

    channel.getPublisher(Optional.empty()).method1();
    verify(subscriber).method1();
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  public void shouldReturnFutureResults() {
    final EventChannel<WithFuture> channel = EventChannel.create(WithFuture.class, metricsSystem);
    final SafeFuture<String> expected = new SafeFuture<>();
    final WithFuture subscriber = () -> expected;
    channel.subscribe(subscriber);

    final SafeFuture<String> result =
        channel.getPublisher(Optional.of(asyncRunner)).getFutureString();
    assertThat(result).isNotDone();

    expected.complete("Yay");
    assertThat(result).isCompletedWithValue("Yay");
  }

  @Test
  public void shouldDisallowMultipleSubscribersWhenMethodsHaveReturnValues() {
    final EventChannel<WithFuture> channel = EventChannel.create(WithFuture.class, metricsSystem);
    channel.subscribe(SafeFuture::new);
    assertThatThrownBy(() -> channel.subscribe(SafeFuture::new))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReportExceptionsToExceptionHandler() throws Exception {
    final EventChannel<Runnable> channel =
        EventChannel.create(Runnable.class, exceptionHandler, metricsSystem);
    final Runnable subscriber = mock(Runnable.class);
    final RuntimeException exception = new RuntimeException("Nope");
    doThrow(exception).when(subscriber).run();

    channel.subscribe(subscriber);
    channel.getPublisher(Optional.empty()).run();

    verify(exceptionHandler)
        .handleException(exception, subscriber, Runnable.class.getMethod("run"), null);
  }

  @Test
  public void shouldNotProxyToString() {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class, metricsSystem);
    final Runnable publisher = channel.getPublisher(Optional.empty());
    final String toString = publisher.toString();
    assertThat(toString).contains(DirectEventDeliverer.class.getName());
  }

  @SuppressWarnings("EqualsWithItself")
  @Test
  public void publisherShouldNotBeEqualToAnything() {
    // Mostly we just want it to not throw exceptions when equals is called.
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class, metricsSystem);
    final Runnable publisher = channel.getPublisher(Optional.empty());
    assertThat(publisher).isNotEqualTo("Foo");
    assertThat(publisher).isNotEqualTo(null);
    assertThat(publisher)
        .isNotEqualTo(
            EventChannel.create(Runnable.class, metricsSystem).getPublisher(Optional.empty()));
    // Specifically call equals as .isEqualTo first checks reference equality
    assertThat(publisher.equals(publisher)).isFalse();
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void shouldDeliverEventsAsync() throws Exception {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<EventWithArgument> channel =
        EventChannel.createAsync(EventWithArgument.class, executor, metricsSystem);
    final EventWithArgument subscriber = mock(EventWithArgument.class);
    channel.subscribe(subscriber);

    // Publish a sequence of events
    channel.getPublisher(Optional.empty()).method1("Event1");
    channel.getPublisher(Optional.empty()).method2("Event2");
    channel.getPublisher(Optional.empty()).method1("Event3");

    verifyNoInteractions(subscriber);

    // Now actually run the consuming thread
    final ArgumentCaptor<QueueReader> consumerCaptor = ArgumentCaptor.forClass(QueueReader.class);
    verify(executor).execute(consumerCaptor.capture());
    consumerCaptor.getValue().deliverNextEvent();
    consumerCaptor.getValue().deliverNextEvent();
    consumerCaptor.getValue().deliverNextEvent();

    // Verify that the events were delivered in order
    final InOrder inOrder = inOrder(subscriber);
    inOrder.verify(subscriber).method1("Event1");
    inOrder.verify(subscriber).method2("Event2");
    inOrder.verify(subscriber).method1("Event3");
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldDeliverAsyncEventsOnMultipleThreads() throws Exception {
    executor =
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("shouldDeliverAsyncEventsOnMultipleThreads-%d")
                .build());
    final EventChannel<WaitOnLatch> channel =
        EventChannel.createAsync(WaitOnLatch.class, executor, metricsSystem);
    final WaitOnLatch subscriber =
        (started, await, completed) -> {
          started.countDown();
          try {
            await.await();
            completed.countDown();
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    channel.subscribeMultithreaded(subscriber, 2); // Two subscribing threads

    final CountDownLatch started1 = new CountDownLatch(1);
    final CountDownLatch await1 = new CountDownLatch(1);
    final CountDownLatch completed1 = new CountDownLatch(1);
    final CountDownLatch started2 = new CountDownLatch(1);
    final CountDownLatch await2 = new CountDownLatch(1);
    final CountDownLatch completed2 = new CountDownLatch(1);

    // Publish two events
    channel.getPublisher(Optional.empty()).waitFor(started1, await1, completed1);
    channel.getPublisher(Optional.empty()).waitFor(started2, await2, completed2);

    // Both events should start being processed
    waitForCountDownLatchComplete(started1);
    waitForCountDownLatchComplete(started2);

    // Then allow the second event to process
    await2.countDown();
    // And it should complete
    waitForCountDownLatchComplete(completed2);

    // And finally allow the first event to process
    await1.countDown();
    // And it should also complete
    waitForCountDownLatchComplete(completed1);
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void shouldReturnFutureResultsAsync() throws Exception {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<WithFuture> channel =
        EventChannel.createAsync(WithFuture.class, executor, metricsSystem);
    final SafeFuture<String> expected = SafeFuture.completedFuture("Yay");
    final WithFuture subscriber = () -> expected;
    channel.subscribe(subscriber);

    final SafeFuture<String> result =
        channel.getPublisher(Optional.of(asyncRunner)).getFutureString();
    assertThat(result).isNotDone(); // Hasn't been delivered to the subscriber yet

    // Now actually run the consuming thread
    final ArgumentCaptor<QueueReader> consumerCaptor = ArgumentCaptor.forClass(QueueReader.class);
    verify(executor).execute(consumerCaptor.capture());
    consumerCaptor.getValue().deliverNextEvent();
    // Should complete the future via the responseExecutor, not immediately
    assertThat(result).isNotDone();

    // Run the response thread
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue("Yay");
  }

  @Test
  @SuppressWarnings("rawtypes")
  void shouldProcessAlreadyPublishedEventsBeforeStopping() {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<EventWithArgument> channel =
        EventChannel.createAsync(EventWithArgument.class, executor, metricsSystem);
    final EventWithArgument subscriber = mock(EventWithArgument.class);
    channel.subscribe(subscriber);

    // Publish a sequence of events
    channel.getPublisher(Optional.empty()).method1("Event1");
    channel.getPublisher(Optional.empty()).method2("Event2");
    channel.getPublisher(Optional.empty()).method1("Event3");

    verifyNoInteractions(subscriber);

    // Stop the channel, but it should still complete pending events
    final SafeFuture<Void> stopFuture = channel.stop();
    assertThat(stopFuture).isNotDone();

    // Now actually run the consuming thread
    final ArgumentCaptor<QueueReader> consumerCaptor = ArgumentCaptor.forClass(QueueReader.class);
    verify(executor).execute(consumerCaptor.capture());
    consumerCaptor.getValue().run(); // Should deliver pending events then stop

    // Verify that the events were delivered in order
    final InOrder inOrder = inOrder(subscriber);
    inOrder.verify(subscriber).method1("Event1");
    inOrder.verify(subscriber).method2("Event2");
    inOrder.verify(subscriber).method1("Event3");
    inOrder.verifyNoMoreInteractions();
    assertThat(stopFuture).isCompleted();
  }

  @Test
  @SuppressWarnings("rawtypes")
  void shouldNotAcceptAdditionalEventsAfterStopped() {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<EventWithArgument> channel =
        EventChannel.createAsync(EventWithArgument.class, executor, metricsSystem);
    final EventWithArgument subscriber = mock(EventWithArgument.class);
    channel.subscribe(subscriber);

    // Publish a sequence of events
    channel.getPublisher(Optional.empty()).method1("Event1");

    // Stop the channel, should only execute events added prior to this point.
    final SafeFuture<Void> stopFuture = channel.stop();
    assertThat(stopFuture).isNotDone();

    channel.getPublisher(Optional.empty()).method2("Event2");
    channel.getPublisher(Optional.empty()).method1("Event3");

    verifyNoInteractions(subscriber);

    // Now actually run the consuming thread
    final ArgumentCaptor<QueueReader> consumerCaptor = ArgumentCaptor.forClass(QueueReader.class);
    verify(executor).execute(consumerCaptor.capture());
    consumerCaptor.getValue().run(); // Should deliver pending events then stop

    // Verify that the events were delivered in order
    final InOrder inOrder = inOrder(subscriber);
    inOrder.verify(subscriber).method1("Event1");
    // events 2 and 3 not executed because they were added after stop
    inOrder.verifyNoMoreInteractions();
    assertThat(stopFuture).isCompleted();
  }

  private void waitForCountDownLatchComplete(final CountDownLatch started1)
      throws InterruptedException {
    assertThat(started1.await(5, TimeUnit.SECONDS)).isTrue();
  }

  private interface WithException {
    @SuppressWarnings("unused")
    void someMethod() throws Exception;
  }

  private interface MultipleMethods {
    void method1();

    void method2();

    @SuppressWarnings("unused")
    void method3();
  }

  private interface EventWithArgument {
    void method1(String value);

    void method2(String value);
  }

  private interface WithFuture {
    SafeFuture<String> getFutureString();
  }

  private interface WaitOnLatch {
    void waitFor(CountDownLatch started, CountDownLatch latch, CountDownLatch completed);
  }
}
