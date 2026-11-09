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

package tech.pegasys.teku.statetransition.datacolumns.log.gossip;

import com.google.common.base.Throwables;
import io.libp2p.pubsub.MessageAlreadySeenException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class DasGossipBatchLogger implements DasGossipLogger {
  private static final Logger LOG = LogManager.getLogger(DasGossipLogger.class);
  private final TimeProvider timeProvider;

  private List<Event> events = new ArrayList<>();

  public DasGossipBatchLogger(final AsyncRunner asyncRunner, final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    asyncRunner.runWithFixedDelay(
        this::logBatchedEvents,
        Duration.ofSeconds(1),
        err -> LOG.debug("DasGossipBatchLogger error: {}", err.toString()));
  }

  interface Event {

    long time();
  }

  interface ColumnEvent extends Event {
    DataColumnSidecar sidecar();
  }

  record ReceiveEvent(
      long time, DataColumnSidecar sidecar, InternalValidationResult validationResult)
      implements ColumnEvent {}

  record PublishEvent(long time, DataColumnSidecar sidecar, Optional<Throwable> result)
      implements ColumnEvent {}

  record SubscribeEvent(long time, int subnetId) implements Event {}

  record UnsubscribeEvent(long time, int subnetId) implements Event {}

  private void logBatchedEvents() {
    final List<Event> eventsLoc;
    synchronized (this) {
      if (events.isEmpty()) {
        return;
      }
      eventsLoc = events;
      events = new ArrayList<>();
    }

    groupByBlock(ReceiveEvent.class, eventsLoc).forEach(this::logReceiveEvents);
    groupByBlock(PublishEvent.class, eventsLoc).forEach(this::logPublishEvents);
    logSubscriptionEvents(eventsLoc);
  }

  private void logReceiveEvents(final SlotAndBlockRoot blockId, final List<ReceiveEvent> events) {
    final Map<ValidationResultCode, List<ReceiveEvent>> eventsByValidateCode =
        events.stream().collect(Collectors.groupingBy(e -> e.validationResult().code()));
    eventsByValidateCode.forEach(
        (validationCode, codeEvents) -> {
          LOG.debug(
              "Received {} data columns (validation result: {}) by gossip {} for block {}: {}",
              codeEvents.size(),
              validationCode,
              msAgoString(codeEvents),
              blockIdString(blockId),
              columnIndicesString(codeEvents));
        });
  }

  private void logPublishEvents(final SlotAndBlockRoot blockId, final List<PublishEvent> events) {
    final Map<Optional<Class<?>>, List<PublishEvent>> eventsByError =
        events.stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.result().map(thr -> ExceptionUtils.getRootCause(thr).getClass())));
    eventsByError.forEach(
        (maybeErrorClass, errEvents) -> {
          Optional<Throwable> someError = errEvents.getFirst().result();
          someError.ifPresentOrElse(
              error -> logErrorByType(error, events, blockId),
              () -> {
                LOG.debug(
                    "Published {} data columns by gossip {} for block {}: {}",
                    errEvents.size(),
                    msAgoString(errEvents),
                    blockIdString(blockId),
                    columnIndicesString(errEvents));
              });
        });
  }

  private void logErrorByType(
      final Throwable error, final List<PublishEvent> errEvents, final SlotAndBlockRoot blockId) {
    final Throwable rootCause = Throwables.getRootCause(error);
    switch (rootCause) {
      case MessageAlreadySeenException ignored ->
          LOG.debug(
              "Error publishing {} data columns ({}) by gossip {} for block {}: has already been seen",
              errEvents.size(),
              columnIndicesString(errEvents),
              msAgoString(errEvents),
              blockIdString(blockId));
      default ->
          LOG.debug(
              "Error publishing {} data columns ({}) by gossip {} for block {}: {}",
              errEvents.size(),
              columnIndicesString(errEvents),
              msAgoString(errEvents),
              blockIdString(blockId),
              error);
    }
  }

  private void logSubscriptionEvents(final List<Event> events) {
    final List<Integer> subscribedSubnets = new ArrayList<>();
    final List<Integer> unsubscribedSubnets = new ArrayList<>();
    events.forEach(
        e -> {
          switch (e) {
            case SubscribeEvent event -> subscribedSubnets.add(event.subnetId());
            case UnsubscribeEvent event -> unsubscribedSubnets.add(event.subnetId());
            default -> {}
          }
        });

    if (!(subscribedSubnets.isEmpty() && unsubscribedSubnets.isEmpty())) {
      String subscribeString =
          subscribedSubnets.isEmpty()
              ? ""
              : "subscribed: " + StringifyUtil.toIntRangeStringWithSize(subscribedSubnets);
      String unsubscribeString =
          unsubscribedSubnets.isEmpty()
              ? ""
              : "unsubscribed: " + StringifyUtil.toIntRangeStringWithSize(unsubscribedSubnets);
      String maybeDelim = subscribedSubnets.isEmpty() || unsubscribedSubnets.isEmpty() ? "" : ", ";
      LOG.debug(
          "Data column gossip subnets subscriptions changed: "
              + subscribeString
              + maybeDelim
              + unsubscribeString);
    }
  }

  private String columnIndicesString(final List<? extends ColumnEvent> events) {
    final List<Integer> columnIndices =
        events.stream().map(e -> e.sidecar().getIndex().intValue()).toList();
    return StringifyUtil.toIntRangeString(columnIndices);
  }

  private static String blockIdString(final SlotAndBlockRoot blockId) {
    return "#"
        + blockId.getSlot()
        + " (0x"
        + LogFormatter.formatAbbreviatedHashRoot(blockId.getBlockRoot())
        + ")";
  }

  private String msAgoString(final List<? extends Event> events) {
    long curTime = timeProvider.getTimeInMillis().longValue();
    long firstMillisAgo = curTime - events.getFirst().time();
    long lastMillisAgo = curTime - events.getLast().time();
    return (lastMillisAgo == firstMillisAgo
            ? lastMillisAgo + "ms"
            : lastMillisAgo + "ms-" + firstMillisAgo + "ms")
        + " ago";
  }

  private boolean needToLogEvent() {
    return LOG.isDebugEnabled();
  }

  @Override
  public synchronized void onReceive(
      final DataColumnSidecar sidecar, final InternalValidationResult validationResult) {
    LOG.trace(
        "DataColumnSidecar received: {}, proofs: {}, reason: {}",
        sidecar::toLogString,
        sidecar::getKzgProofs,
        () -> validationResult.getDescription().orElse("<no reason>"));
    if (needToLogEvent()) {
      events.add(
          new ReceiveEvent(timeProvider.getTimeInMillis().longValue(), sidecar, validationResult));
    }
  }

  @Override
  public synchronized void onPublish(
      final DataColumnSidecar sidecar, final Optional<Throwable> result) {
    if (needToLogEvent()) {
      events.add(new PublishEvent(timeProvider.getTimeInMillis().longValue(), sidecar, result));
    }
  }

  @Override
  public void onDataColumnSubnetSubscribe(final int subnetId) {
    if (needToLogEvent()) {
      events.add(new SubscribeEvent(timeProvider.getTimeInMillis().longValue(), subnetId));
    }
  }

  @Override
  public void onDataColumnSubnetUnsubscribe(final int subnetId) {
    if (needToLogEvent()) {
      events.add(new UnsubscribeEvent(timeProvider.getTimeInMillis().longValue(), subnetId));
    }
  }

  private static <TEvent extends ColumnEvent>
      SortedMap<SlotAndBlockRoot, List<TEvent>> groupByBlock(
          final Class<TEvent> eventClass, final List<Event> allEvents) {
    final SortedMap<SlotAndBlockRoot, List<TEvent>> eventsByBlock = new TreeMap<>();
    for (final Event event : allEvents) {
      if (eventClass.isAssignableFrom(event.getClass())) {
        @SuppressWarnings("unchecked")
        final TEvent e = (TEvent) event;
        final DataColumnSidecar sidecar = e.sidecar();
        final SlotAndBlockRoot blockId =
            new SlotAndBlockRoot(sidecar.getSlot(), sidecar.getBeaconBlockRoot());
        eventsByBlock.computeIfAbsent(blockId, __ -> new ArrayList<>()).add(e);
      }
    }
    return eventsByBlock;
  }
}
