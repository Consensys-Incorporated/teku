/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.subscribers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.ExitConstants;
import tech.pegasys.teku.infrastructure.exceptions.FatalErrorHandler;

public class SubscribersTest {

  private final Runnable subscriber1 = mock(Runnable.class);
  private final Runnable subscriber2 = mock(Runnable.class);
  private final Subscribers<Runnable> subscribers = Subscribers.create(false);
  private final List<Integer> exitCodes = new ArrayList<>();

  @BeforeEach
  void setUpFatalErrorHandler() {
    FatalErrorHandler.overrideProcessTerminator(
        (exitCode, gracefulTimeout) -> exitCodes.add(exitCode));
  }

  @AfterEach
  void restoreFatalErrorHandler() {
    FatalErrorHandler.restoreDefaultProcessTerminator();
  }

  @Test
  public void shouldAddSubscriber() {
    subscribers.subscribe(subscriber1);

    assertThat(subscribers.getSubscriberCount()).isEqualTo(1);

    subscribers.forEach(Runnable::run);
    verify(subscriber1).run();
  }

  @Test
  public void shouldRemoveSubscriber() {
    final long id = subscribers.subscribe(subscriber1);
    subscribers.subscribe(subscriber2);
    assertThat(subscribers.unsubscribe(id)).isTrue();

    assertThat(subscribers.getSubscriberCount()).isEqualTo(1);
    subscribers.forEach(Runnable::run);
    verifyNoInteractions(subscriber1);
    verify(subscriber2).run();
  }

  @Test
  public void shouldTrackMultipleSubscribers() {
    final Runnable subscriber3 = mock(Runnable.class);
    subscribers.subscribe(subscriber1);
    subscribers.subscribe(subscriber2);
    subscribers.subscribe(subscriber3);

    assertThat(subscribers.getSubscriberCount()).isEqualTo(3);
    subscribers.forEach(Runnable::run);
    verify(subscriber1).run();
    verify(subscriber2).run();
    verify(subscriber3).run();
  }

  @Test
  public void suppressCallbackExceptions_false() {
    final Subscribers<Runnable> subscribers = Subscribers.create(false);

    doThrow(new IllegalStateException("whoops")).when(subscriber1).run();
    subscribers.subscribe(subscriber1);

    assertThatThrownBy(() -> subscribers.forEach(Runnable::run))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("whoops");
  }

  @Test
  public void suppressCallbackExceptions_true() {
    final Subscribers<Runnable> subscribers = Subscribers.create(true);

    doThrow(new IllegalStateException("whoops")).when(subscriber1).run();
    subscribers.subscribe(subscriber1);

    // No Exception should be thrown
    subscribers.forEach(Runnable::run);

    assertThat(exitCodes).isEmpty();
  }

  // Suppressing exceptions must not hide the node running out of memory
  @Test
  public void suppressCallbackExceptions_shouldStillShutdownOnFatalError() {
    final Subscribers<Runnable> subscribers = Subscribers.create(true);

    doThrow(new IllegalStateException("whoops", new OutOfMemoryError())).when(subscriber1).run();
    subscribers.subscribe(subscriber1);

    subscribers.forEach(Runnable::run);

    assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldDeliverEventToEachSubscriber() {
    final Subscribers<Consumer<String>> subscribers = Subscribers.create(false);
    final Consumer<String> subscriber1 = mock(Consumer.class);
    final Consumer<String> subscriber2 = mock(Consumer.class);
    subscribers.subscribe(subscriber1);
    subscribers.subscribe(subscriber2);

    final String event = "Hello";
    subscribers.deliver(Consumer::accept, event);
    verify(subscriber1).accept(event);
    verify(subscriber2).accept(event);
  }
}
