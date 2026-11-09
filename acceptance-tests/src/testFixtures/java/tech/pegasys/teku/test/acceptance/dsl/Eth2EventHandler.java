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

package tech.pegasys.teku.test.acceptance.dsl;

import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Eth2EventHandler implements BackgroundEventHandler {
  private static final Logger LOG = LogManager.getLogger();
  private final List<PackedMessage> eventList = new ArrayList<>();

  // Using this as a way to assert that our EventSubscriber is ready
  private boolean hasReceivedReadyComment = false;

  @Override
  public void onOpen() {}

  @Override
  public void onClosed() {
    LOG.warn("Event stream closed");
  }

  @Override
  public synchronized void onMessage(final String event, final MessageEvent messageEvent) {
    eventList.add(new PackedMessage(event, messageEvent));
  }

  public synchronized List<PackedMessage> getMessages() {
    return List.copyOf(eventList);
  }

  @Override
  public synchronized void onComment(final String comment) {
    if (!hasReceivedReadyComment) {
      hasReceivedReadyComment = comment.equals("ready");
    }
  }

  public synchronized boolean hasReceivedComment() {
    return hasReceivedReadyComment;
  }

  @Override
  public void onError(final Throwable t) {
    LOG.error("Event stream encountered an error!", t);
  }

  public static class PackedMessage {
    private final String event;
    private final MessageEvent messageEvent;

    public PackedMessage(final String event, final MessageEvent messageEvent) {
      this.event = event;
      this.messageEvent = messageEvent;
    }

    public String getEvent() {
      return event;
    }

    public MessageEvent getMessageEvent() {
      return messageEvent;
    }
  }
}
