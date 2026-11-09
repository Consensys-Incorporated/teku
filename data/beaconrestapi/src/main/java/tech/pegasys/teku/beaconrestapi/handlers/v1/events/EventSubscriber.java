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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.sse.SseClient;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.EventSubscriptionManager.EventSource;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class EventSubscriber {

  private static final Logger LOG = LogManager.getLogger();
  static final int EXCESSIVE_QUEUING_TOLERANCE_MS = 1000;
  static final int SANITY_LIMIT = 4;
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final List<EventType> eventTypes;
  private final SseClient sseClient;
  private final Queue<QueuedEvent> queuedEvents;
  private final TimeProvider timeProvider;
  private final int maxPendingEvents;
  private final AtomicBoolean processingQueue;
  private final AsyncRunner asyncRunner;
  private final AtomicLong excessiveQueueingDisconnectionTime = new AtomicLong(Long.MAX_VALUE);
  private final AtomicInteger successiveFailureCounter = new AtomicInteger(0);

  public EventSubscriber(
      final List<String> eventTypes,
      final SseClient sseClient,
      final Runnable closeCallback,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final int maxPendingEvents) {
    this.eventTypes = EventType.getTopics(eventTypes);
    this.sseClient = sseClient;
    this.timeProvider = timeProvider;
    this.maxPendingEvents = maxPendingEvents;
    this.queuedEvents = new ConcurrentLinkedQueue<>();
    this.processingQueue = new AtomicBoolean(false);
    this.asyncRunner = asyncRunner;
    this.sseClient.onClose(
        () -> {
          stopped.set(true);
          closeCallback.run();
        });

    keepAlive();
  }

  public void onEvent(final EventType eventType, final EventSource<?> message)
      throws JsonProcessingException {
    if (!eventTypes.contains(eventType)) {
      return;
    }
    final boolean queueSizeBelowLimit = queuedEvents.size() < maxPendingEvents;
    final long now = timeProvider.getTimeInMillis().longValue();
    final long queuingDisconnectTime = excessiveQueueingDisconnectionTime.get();
    if (queueSizeBelowLimit) {
      excessiveQueueingDisconnectionTime.set(Long.MAX_VALUE);
      addEventToQueue(eventType, message);
    } else if (queuingDisconnectTime <= now) {
      // Had excessive queuing for too long, disconnect.
      if (stopped.compareAndSet(false, true)) {
        LOG.debug("Closing event connection due to exceeding the pending message limit");
        terminateSseClient();
      }
    } else {
      if (now + EXCESSIVE_QUEUING_TOLERANCE_MS < queuingDisconnectTime) {
        excessiveQueueingDisconnectionTime.set(now + EXCESSIVE_QUEUING_TOLERANCE_MS);
      }
      addEventToQueue(eventType, message);
    }
  }

  private void terminateSseClient() {
    // Closing the SseClient completes Javalin's blocking future, allowing Javalin to complete the
    // async context itself.
    sseClient.close();
  }

  private void addEventToQueue(final EventType eventType, final EventSource<?> message)
      throws JsonProcessingException {
    queuedEvents.add(QueuedEvent.of(eventType, message.get()));
    processEventQueue();
  }

  public SseClient getSseClient() {
    return sseClient;
  }

  private void processEventQueue() {
    if (!stopped.get() && !processingQueue.compareAndSet(false, true)) {
      // any queue processing in progress will clear the queue, no need to run another instance
      return;
    }
    asyncRunner
        .runAsync(
            () -> {
              LOG.trace(
                  "Processing queue with {} elements for event client {}",
                  queuedEvents.size(),
                  sseClient.hashCode());
              QueuedEvent event = queuedEvents.poll();
              while (event != null && !stopped.get()) {
                sseClient.sendEvent(
                    event.getEventType().name(),
                    new ByteArrayInputStream(event.getMessageData().toArrayUnsafe()));
                event = queuedEvents.poll();
              }
              successiveFailureCounter.set(0);
            })
        .alwaysRun(
            () -> {
              processingQueue.set(false);
              if (!queuedEvents.isEmpty()) {
                processEventQueue();
              }
            })
        .finish(
            error -> {
              final int counter = successiveFailureCounter.incrementAndGet();
              if (error.getCause() instanceof IllegalStateException && counter > SANITY_LIMIT) {
                LOG.warn(
                    "Failed to process event queue for client {}, terminating connection with {} queued events after {} failed attempts to send events.",
                    sseClient::hashCode,
                    queuedEvents::size,
                    () -> counter);
                terminateSseClient();
              } else {
                if (counter > 1) {
                  LOG.error(
                      "Failed to process event queue for client {}", sseClient.hashCode(), error);
                } else {
                  LOG.trace(
                      "Failed to process event queue for client {}", sseClient.hashCode(), error);
                }
              }
            });
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void keepAlive() {
    final int retryInSeconds = 30;
    if (!stopped.get()) {
      asyncRunner
          .runAfterDelay(
              () -> {
                // Don't send a keep alive if we already have messages to send
                if (!stopped.get() && queuedEvents.isEmpty() && !processingQueue.get()) {
                  sseClient.sendComment("");
                }
              },
              Duration.ofSeconds(retryInSeconds))
          .alwaysRun(this::keepAlive)
          .exceptionally(
              throwable -> {
                final Throwable rootException = Throwables.getRootCause(throwable);
                if (rootException instanceof IllegalStateException) {
                  // this might fail due to https://github.com/eclipse/jetty.project/issues/4461 but
                  // ultimately isn't important because it has a retry mechanism
                  LOG.debug(
                      "Error sending keep-alive SSE comment. Retrying in {} seconds.",
                      retryInSeconds);
                  return null;
                }

                if (throwable instanceof RuntimeException) {
                  throw (RuntimeException) throwable;
                } else {
                  throw new RuntimeException(
                      "Unexpected error when sending keep-alive SSE comment.", throwable);
                }
              });
    }
  }

  /*
   Using this as a way of notifying clients that our EventSubscriber is ready and they should start receiving events
  */
  public void sendReadyComment() {
    if (!stopped.get()) {
      asyncRunner.runAsync(() -> sseClient.sendComment("ready")).finishStackTrace();
    }
  }
}
