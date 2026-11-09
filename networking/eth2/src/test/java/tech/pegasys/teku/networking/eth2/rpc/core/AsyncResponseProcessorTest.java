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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.rpc.core.AsyncResponseProcessor.AsyncProcessingErrorHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.AdditionalDataReceivedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;

public class AsyncResponseProcessorTest {
  private static final Logger LOG = LogManager.getLogger();
  private final List<String> responses = new ArrayList<>();
  private final List<Throwable> errors = new ArrayList<>();
  private final Consumer<String> defaultProcessor = responses::add;
  private final AsyncProcessingErrorHandler errorConsumer = errors::add;

  private final AtomicReference<Consumer<String>> requestProcessor =
      new AtomicReference<>(defaultProcessor);
  final Eth2RpcResponseHandler<String, Void> responseHandler =
      Eth2RpcResponseHandler.expectMultipleResponses(
          RpcResponseListener.from(res -> requestProcessor.get().accept(res)));
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final AsyncResponseProcessor<String> asyncResponseProcessor =
      new AsyncResponseProcessor<>(asyncRunner, responseHandler, errorConsumer);

  @Test
  public void processMultipleResponsesSuccessfully() throws Exception {
    asyncResponseProcessor.processResponse("a");
    assertThat(asyncResponseProcessor.getResponseCount()).isEqualTo(1);
    asyncResponseProcessor.processResponse("b");
    assertThat(asyncResponseProcessor.getResponseCount()).isEqualTo(2);
    asyncResponseProcessor.processResponse("c");
    assertThat(asyncResponseProcessor.getResponseCount()).isEqualTo(3);

    assertThat(responses).isEmpty();

    asyncRunner.executeQueuedActions(1);
    assertThat(responses).containsExactly("a");

    asyncRunner.executeUntilDone();
    assertThat(responses).containsExactly("a", "b", "c");
    assertThat(asyncResponseProcessor.finishProcessing()).isDone();
    assertThat(asyncResponseProcessor.getResponseCount()).isEqualTo(3);
  }

  @Test
  public void dropsRemainingResponsesOnError() throws Exception {
    asyncResponseProcessor.processResponse("a");
    asyncResponseProcessor.processResponse("b");
    asyncResponseProcessor.processResponse("c");

    assertThat(responses).isEmpty();

    asyncRunner.executeQueuedActions(1);
    assertThat(responses).containsExactly("a");

    final RuntimeException error = new RuntimeException("whoops");
    final Consumer<String> failingProcessor =
        (s) -> {
          throw error;
        };
    requestProcessor.set(failingProcessor);

    asyncRunner.executeUntilDone();
    assertThat(responses).containsExactly("a");
    assertThat(errors).containsExactly(error);

    assertThat(asyncResponseProcessor.finishProcessing()).isDone();
    assertThat(asyncResponseProcessor.getResponseCount()).isEqualTo(3);
  }

  @Test
  public void finishProcessingWhileSomeResponsesStillQueue() throws Exception {
    asyncResponseProcessor.processResponse("a");
    asyncResponseProcessor.processResponse("b");
    asyncResponseProcessor.processResponse("c");

    assertThat(responses).isEmpty();

    asyncRunner.executeQueuedActions(1);
    assertThat(responses).containsExactly("a");

    final SafeFuture<Void> finishedFuture = asyncResponseProcessor.finishProcessing();
    assertThat(finishedFuture).isNotDone();

    asyncRunner.executeUntilDone();
    assertThat(responses).containsExactly("a", "b", "c");
    assertThat(finishedFuture).isDone();
  }

  @Test
  public void finishProcessingWhileSomeResponsesStillQueueWhenErrorIsThrown() throws Exception {
    asyncResponseProcessor.processResponse("a");
    asyncResponseProcessor.processResponse("b");
    asyncResponseProcessor.processResponse("c");

    assertThat(responses).isEmpty();

    asyncRunner.executeQueuedActions(1);
    assertThat(responses).containsExactly("a");

    final SafeFuture<Void> finishedFuture = asyncResponseProcessor.finishProcessing();
    assertThat(finishedFuture).isNotDone();

    final RuntimeException error = new RuntimeException("whoops");
    final Consumer<String> failingProcessor =
        (s) -> {
          throw error;
        };
    requestProcessor.set(failingProcessor);

    asyncRunner.executeUntilDone();
    assertThat(responses).containsExactly("a");
    assertThat(errors).containsExactly(error);

    assertThat(finishedFuture).isDone();
  }

  @Test
  public void shouldThrowIfResponsesSubmittedAfterFinishedProcessing() {
    asyncResponseProcessor.finishProcessing().finishDebug(LOG);
    assertThatThrownBy(() -> asyncResponseProcessor.processResponse("a"))
        .isInstanceOf(AdditionalDataReceivedException.class);
  }
}
