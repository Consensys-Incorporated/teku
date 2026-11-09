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

import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.ReadyState;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EventStreamListener {
  BackgroundEventSource eventSource;
  final Eth2EventHandler handler = new Eth2EventHandler();

  public EventStreamListener(final String url) {
    eventSource =
        new BackgroundEventSource.Builder(
                handler,
                new EventSource.Builder(
                    ConnectStrategy.http(URI.create(url)).readTimeout(5, TimeUnit.MINUTES)))
            .build();
    eventSource.start();
  }

  public List<Eth2EventHandler.PackedMessage> getMessages() {
    return handler.getMessages();
  }

  public boolean isReady() {
    return eventSource.getEventSource().getState() == ReadyState.OPEN
        && handler.hasReceivedComment();
  }

  public void close() {
    eventSource.close();
  }
}
