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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;

@SuppressWarnings("unchecked")
public class CompletionAwareResponseCallbackTest {

  private final ResponseCallback<String> delegate = mock(ResponseCallback.class);
  private final CompletionAwareResponseCallback<String> callback =
      new CompletionAwareResponseCallback<>(delegate);

  @BeforeEach
  void setUp() {
    when(delegate.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void completionActionRunsOnCompleteSuccessfully() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    callback.completeSuccessfully();

    verify(delegate).completeSuccessfully();
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsOnRespondAndCompleteSuccessfully() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    callback.respondAndCompleteSuccessfully("data");

    verify(delegate).respondAndCompleteSuccessfully("data");
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsOnCompleteWithErrorResponse() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    final RpcException error = new RpcException((byte) 1, "error");
    callback.completeWithErrorResponse(error);

    verify(delegate).completeWithErrorResponse(error);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsOnCompleteWithUnexpectedError() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    final RuntimeException error = new RuntimeException("unexpected");
    callback.completeWithUnexpectedError(error);

    verify(delegate).completeWithUnexpectedError(error);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void respondDoesNotTriggerCompletionAction() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    final SafeFuture<Void> result = callback.respond("data");

    assertThat(result).isCompleted();
    verify(delegate).respond("data");
    assertThat(counter.get()).isEqualTo(0);
  }

  @Test
  void completionActionExceptionIsSuppressed() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(
        () -> {
          throw new RuntimeException("boom");
        });
    callback.onCompletion(counter::incrementAndGet);

    callback.completeSuccessfully();

    verify(delegate).completeSuccessfully();
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsWhenCompleteSuccessfullyThrows() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    doThrow(new RuntimeException("delegate failed")).when(delegate).completeSuccessfully();

    assertThatThrownBy(callback::completeSuccessfully)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("delegate failed");
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsWhenRespondAndCompleteSuccessfullyThrows() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    doThrow(new RuntimeException("delegate failed"))
        .when(delegate)
        .respondAndCompleteSuccessfully("data");

    assertThatThrownBy(() -> callback.respondAndCompleteSuccessfully("data"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("delegate failed");
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsWhenCompleteWithErrorResponseThrows() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    final RpcException error = new RpcException((byte) 1, "error");
    doThrow(new RuntimeException("delegate failed"))
        .when(delegate)
        .completeWithErrorResponse(error);

    assertThatThrownBy(() -> callback.completeWithErrorResponse(error))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("delegate failed");
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionRunsWhenCompleteWithUnexpectedErrorThrows() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    final RuntimeException originalError = new RuntimeException("unexpected");
    doThrow(new RuntimeException("delegate failed"))
        .when(delegate)
        .completeWithUnexpectedError(originalError);

    assertThatThrownBy(() -> callback.completeWithUnexpectedError(originalError))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("delegate failed");
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void completionActionsRunOnlyOnceWhenDelegateThrowsAndErrorHandlerCompletesAgain() {
    final AtomicInteger counter = new AtomicInteger(0);
    callback.onCompletion(counter::incrementAndGet);

    doThrow(new RuntimeException("delegate failed")).when(delegate).completeSuccessfully();

    // Simulate the thenAccept().finish(errorHandler) pattern:
    // 1. completeSuccessfully() throws → finally runs completion actions
    // 2. Error handler calls completeWithUnexpectedError() on the same callback
    try {
      callback.completeSuccessfully();
    } catch (final RuntimeException e) {
      // error propagates to finish error handler which calls another complete method
      callback.completeWithUnexpectedError(e);
    }

    assertThat(counter.get()).isEqualTo(1);
  }
}
