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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;

class EventChannelsTest {
  private final EventChannels channels =
      new EventChannels(channel -> EventChannel.create(channel, new NoOpMetricsSystem()));

  @Test
  public void shouldBeAbleToGetPublisherBeforeSubscriber() {
    final SimpleChannel publisher = channels.getPublisher(SimpleChannel.class);

    final SimpleChannel subscriber = mock(SimpleChannel.class);
    channels.subscribe(SimpleChannel.class, subscriber);

    publisher.run();

    verify(subscriber).run();
  }

  @Test
  public void shouldBeAbleToSubscribeBeforePublisherIsCreated() {
    final SimpleChannel subscriber = mock(SimpleChannel.class);
    channels.subscribe(SimpleChannel.class, subscriber);

    final SimpleChannel publisher = channels.getPublisher(SimpleChannel.class);

    publisher.run();

    verify(subscriber).run();
  }

  @Test
  public void shouldBeAbleToAddMultipleSubscribers() {
    final SimpleChannel subscriber1 = mock(SimpleChannel.class);
    final SimpleChannel subscriber2 = mock(SimpleChannel.class);

    channels.subscribe(SimpleChannel.class, subscriber1);
    final SimpleChannel publisher = channels.getPublisher(SimpleChannel.class);
    channels.subscribe(SimpleChannel.class, subscriber2);

    publisher.run();

    verify(subscriber1).run();
    verify(subscriber2).run();
  }

  @Test
  public void shouldKeepDifferentChannelsSeparate() {
    final SimpleChannel runnableSubscriber = mock(SimpleChannel.class);
    final SimpleConsumer consumerSubscriber = mock(SimpleConsumer.class);
    channels.subscribe(SimpleChannel.class, runnableSubscriber);
    channels.subscribe(SimpleConsumer.class, consumerSubscriber);

    final SimpleChannel runnablePublisher = channels.getPublisher(SimpleChannel.class);
    final SimpleConsumer consumerPublisher = channels.getPublisher(SimpleConsumer.class);

    runnablePublisher.run();
    verify(runnableSubscriber).run();
    verifyNoInteractions(consumerSubscriber);

    consumerPublisher.accept(1);
    verify(consumerSubscriber).accept(1);
    verifyNoMoreInteractions(runnableSubscriber);
  }

  private interface SimpleConsumer extends VoidReturningChannelInterface {
    void accept(int value);
  }

  private interface SimpleChannel extends VoidReturningChannelInterface {
    void run();
  }
}
