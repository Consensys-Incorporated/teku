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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcResponseHandler.InvalidRpcResponseException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;

public class Eth2ResponseHandlerTest {
  private final RuntimeException error = new RuntimeException("oops");

  @Test
  @SuppressWarnings("deprecation")
  public void expectMultipleResponses_successful() {
    final IntList responsesReceived = new IntArrayList();
    final Eth2RpcResponseHandler<Integer, Void> handler =
        Eth2RpcResponseHandler.expectMultipleResponses(
            RpcResponseListener.from(responsesReceived::add));
    assertNotDone(handler);

    assertThat(handler.onResponse(1)).isCompleted();
    assertThat(handler.onResponse(2)).isCompleted();
    assertNotDone(handler);
    handler.onCompleted();

    assertThatIntCollection(responsesReceived).contains(1, 2);
    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompleted();
  }

  @Test
  public void expectMultipleResponses_partiallySuccessful() {
    final IntList responsesReceived = new IntArrayList();
    final Eth2RpcResponseHandler<Integer, Void> handler =
        Eth2RpcResponseHandler.expectMultipleResponses(
            RpcResponseListener.from(i -> responsesReceived.add((int) i)));

    assertThat(handler.onResponse(1)).isCompleted();
    assertThat(handler.onResponse(2)).isCompleted();
    assertNotDone(handler);

    // Complete
    handler.onCompleted(error);

    assertThatIntCollection(responsesReceived).containsExactly(1, 2);
    assertFailedWithDefaultError(handler);
  }

  @Test
  public void expectSingleResponse_successful() throws Exception {
    Eth2RpcResponseHandler<Integer, Integer> handler =
        Eth2RpcResponseHandler.expectSingleResponse();
    assertNotDone(handler);

    assertThat(handler.onResponse(1)).isCompleted();
    handler.onCompleted();

    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompleted();
    assertThat(handler.getResult().get()).isEqualTo(1);
  }

  @Test
  public void expectSingleResponse_failed() {
    Eth2RpcResponseHandler<Integer, Integer> handler =
        Eth2RpcResponseHandler.expectSingleResponse();
    assertNotDone(handler);

    handler.onCompleted(error);

    assertFailedWithDefaultError(handler);
  }

  @Test
  public void expectSingleResponse_missingResponse() {
    Eth2RpcResponseHandler<Integer, Integer> handler =
        Eth2RpcResponseHandler.expectSingleResponse();
    assertNotDone(handler);

    handler.onCompleted();

    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompletedExceptionally();
    assertThatThrownBy(() -> handler.getResult().get())
        .hasCauseInstanceOf(InvalidRpcResponseException.class)
        .hasMessageContaining("No response received when single response expected");
  }

  @Test
  public void expectSingleResponse_tooManyResponses() throws Exception {
    Eth2RpcResponseHandler<Integer, Integer> handler =
        Eth2RpcResponseHandler.expectSingleResponse();
    assertNotDone(handler);

    assertThat(handler.onResponse(1)).isCompleted();
    final SafeFuture<?> secondResult = handler.onResponse(2);
    assertThat(secondResult).isCompletedExceptionally();
    assertThatThrownBy(secondResult::get)
        .hasCauseInstanceOf(InvalidRpcResponseException.class)
        .hasMessageContaining("Received multiple responses when single response expected");
    assertNotDone(handler);

    handler.onCompleted();

    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompletedExceptionally();
    assertThatThrownBy(() -> handler.getResult().get())
        .hasCauseInstanceOf(InvalidRpcResponseException.class)
        .hasMessageContaining("Received multiple responses when single response expected");
  }

  @Test
  public void expectOptionalResponse_successfulWithResponse() throws Exception {
    Eth2RpcResponseHandler<Integer, Optional<Integer>> handler =
        Eth2RpcResponseHandler.expectOptionalResponse();
    assertNotDone(handler);

    assertThat(handler.onResponse(1)).isCompleted();
    assertNotDone(handler);
    handler.onCompleted();

    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompleted();
    assertThat(handler.getResult().get()).contains(1);
  }

  @Test
  public void expectOptionalResponse_appliesListenerWrapper() {
    final RuntimeException validationError = new RuntimeException("invalid");
    Eth2RpcResponseHandler<Integer, Optional<Integer>> handler =
        Eth2RpcResponseHandler.expectOptionalResponse(
            listener ->
                response -> {
                  if (response == 2) {
                    throw validationError;
                  }
                  return listener.onResponse(response);
                });
    assertNotDone(handler);

    final SafeFuture<?> responseResult = handler.onResponse(2);
    assertThat(responseResult).isCompletedExceptionally();
    assertThatThrownBy(responseResult::get).hasCause(validationError);
  }

  @Test
  public void expectOptionalResponse_successfulWithNoResponse() throws Exception {
    Eth2RpcResponseHandler<Integer, Optional<Integer>> handler =
        Eth2RpcResponseHandler.expectOptionalResponse();
    assertNotDone(handler);

    handler.onCompleted();

    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompleted();
    assertThat(handler.getResult().get()).isEmpty();
  }

  @Test
  public void expectOptionalResponse_failed() {
    Eth2RpcResponseHandler<Integer, Optional<Integer>> handler =
        Eth2RpcResponseHandler.expectOptionalResponse();
    assertNotDone(handler);

    assertThat(handler.onResponse(1)).isCompleted();
    assertNotDone(handler);
    handler.onCompleted(error);

    assertFailedWithDefaultError(handler);
  }

  @Test
  public void expectOptionalResponse_tooManyResponses() throws Exception {
    Eth2RpcResponseHandler<Integer, Optional<Integer>> handler =
        Eth2RpcResponseHandler.expectOptionalResponse();
    assertNotDone(handler);

    assertThat(handler.onResponse(1)).isCompleted();
    final SafeFuture<?> secondResult = handler.onResponse(2);
    assertThat(secondResult).isCompletedExceptionally();
    assertThatThrownBy(secondResult::get)
        .hasCauseInstanceOf(InvalidRpcResponseException.class)
        .hasMessageContaining("Received multiple responses when single response expected");
    assertNotDone(handler);

    handler.onCompleted();

    assertThat(handler.getCompletedFuture()).isCompleted();
    assertThat(handler.getResult()).isCompletedExceptionally();
    assertThatThrownBy(() -> handler.getResult().get())
        .hasCauseInstanceOf(InvalidRpcResponseException.class)
        .hasMessageContaining("Received multiple responses when single response expected");
  }

  private void assertFailedWithDefaultError(final Eth2RpcResponseHandler<?, ?> handler) {
    AssertionsForClassTypes.assertThat(handler.getCompletedFuture()).isCompletedExceptionally();
    AssertionsForClassTypes.assertThat(handler.getResult()).isCompletedExceptionally();
    assertThatThrownBy(() -> handler.getCompletedFuture().get()).hasCause(error);
    assertThatThrownBy(() -> handler.getResult().get()).hasCause(error);
  }

  private void assertNotDone(final Eth2RpcResponseHandler<?, ?> handler) {
    assertThat(handler.getCompletedFuture()).isNotDone();
    assertThat(handler.getResult()).isNotDone();
  }
}
