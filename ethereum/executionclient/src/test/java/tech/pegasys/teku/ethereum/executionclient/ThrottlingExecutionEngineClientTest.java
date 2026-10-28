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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadBodyV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV4;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.QueueIsFullException;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

class ThrottlingExecutionEngineClientTest {

  private static final int MAX_CONCURRENT_REQUESTS = 2;
  private static final int MAX_CONCURRENT_PAYLOAD_BODIES_REQUESTS = 1;
  private static final int MAX_QUEUED_PAYLOAD_BODIES_REQUESTS = 2;

  private static final ForkChoiceStateV1 FORK_CHOICE_STATE =
      new ForkChoiceStateV1(Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO);

  private final ExecutionEngineClient delegate = mock(ExecutionEngineClient.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final List<SafeFuture<Response<List<ExecutionPayloadBodyV2>>>>
      pendingPayloadBodiesResponses = new ArrayList<>();
  private final List<SafeFuture<Response<PayloadStatusV1>>> pendingNewPayloadResponses =
      new ArrayList<>();
  private final List<SafeFuture<Response<ForkChoiceUpdatedResult>>>
      pendingForkChoiceUpdatedResponses = new ArrayList<>();

  private final ThrottlingExecutionEngineClient client =
      new ThrottlingExecutionEngineClient(
          delegate,
          MAX_CONCURRENT_REQUESTS,
          MAX_CONCURRENT_PAYLOAD_BODIES_REQUESTS,
          MAX_QUEUED_PAYLOAD_BODIES_REQUESTS,
          metricsSystem);

  @BeforeEach
  void setUp() {
    // every delegate call returns a future that stays pending until the test completes it, so the
    // tests control exactly how many requests are in flight
    when(delegate.getPayloadBodiesByHashV2(any()))
        .thenAnswer(
            __ -> {
              final SafeFuture<Response<List<ExecutionPayloadBodyV2>>> response =
                  new SafeFuture<>();
              pendingPayloadBodiesResponses.add(response);
              return response;
            });
    when(delegate.newPayloadV5(any(), any(), any(), any()))
        .thenAnswer(
            __ -> {
              final SafeFuture<Response<PayloadStatusV1>> response = new SafeFuture<>();
              pendingNewPayloadResponses.add(response);
              return response;
            });
    when(delegate.forkChoiceUpdatedV4(any(), any()))
        .thenAnswer(
            __ -> {
              final SafeFuture<Response<ForkChoiceUpdatedResult>> response = new SafeFuture<>();
              pendingForkChoiceUpdatedResponses.add(response);
              return response;
            });
  }

  @Test
  void shouldNotDelayCriticalRequestsWhilePayloadBodiesRequestsArePending() {
    saturatePayloadBodiesQueue();

    final SafeFuture<Response<PayloadStatusV1>> newPayloadResult = newPayload();
    final SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedResult =
        forkChoiceUpdated();

    // both calls must reach the execution engine straight away, even though the payload bodies
    // lookups ahead of them have not completed
    verify(delegate).newPayloadV5(any(), any(), any(), any());
    verify(delegate).forkChoiceUpdatedV4(any(), any());

    final Response<PayloadStatusV1> newPayloadResponse = Response.fromNullPayload();
    pendingNewPayloadResponses.getFirst().complete(newPayloadResponse);
    assertThat(newPayloadResult).isCompletedWithValue(newPayloadResponse);

    final Response<ForkChoiceUpdatedResult> forkChoiceUpdatedResponse = Response.fromNullPayload();
    pendingForkChoiceUpdatedResponses.getFirst().complete(forkChoiceUpdatedResponse);
    assertThat(forkChoiceUpdatedResult).isCompletedWithValue(forkChoiceUpdatedResponse);
  }

  @Test
  void shouldNotDelayPayloadBodiesRequestsWhileCriticalRequestsArePending() {
    // fill every in-flight slot of the critical queue and leave one request waiting behind them
    IntStream.range(0, MAX_CONCURRENT_REQUESTS + 1).forEach(__ -> ignoreFuture(newPayload()));
    verify(delegate, times(MAX_CONCURRENT_REQUESTS)).newPayloadV5(any(), any(), any(), any());

    final Bytes32 blockHash = blockHash(0);
    ignoreFuture(client.getPayloadBodiesByHashV2(List.of(blockHash)));

    verify(delegate).getPayloadBodiesByHashV2(List.of(blockHash));
  }

  @Test
  void shouldLimitConcurrentPayloadBodiesRequests() {
    final Bytes32 firstBlockHash = blockHash(0);
    final Bytes32 secondBlockHash = blockHash(1);

    final SafeFuture<Response<List<ExecutionPayloadBodyV2>>> firstResult =
        client.getPayloadBodiesByHashV2(List.of(firstBlockHash));
    final SafeFuture<Response<List<ExecutionPayloadBodyV2>>> secondResult =
        client.getPayloadBodiesByHashV2(List.of(secondBlockHash));

    verify(delegate).getPayloadBodiesByHashV2(List.of(firstBlockHash));
    verify(delegate, never()).getPayloadBodiesByHashV2(List.of(secondBlockHash));
    assertThat(firstResult).isNotDone();
    assertThat(secondResult).isNotDone();

    final Response<List<ExecutionPayloadBodyV2>> firstResponse = Response.fromNullPayload();
    pendingPayloadBodiesResponses.getFirst().complete(firstResponse);

    assertThat(firstResult).isCompletedWithValue(firstResponse);
    verify(delegate).getPayloadBodiesByHashV2(List.of(secondBlockHash));
    assertThat(secondResult).isNotDone();
  }

  @Test
  void shouldRejectPayloadBodiesRequestsWhenTheirQueueIsFull() {
    saturatePayloadBodiesQueue();
    assertThat(getPayloadBodiesQueueSize()).isEqualTo(MAX_QUEUED_PAYLOAD_BODIES_REQUESTS);

    final Bytes32 rejectedBlockHash = blockHash(99);
    final SafeFuture<Response<List<ExecutionPayloadBodyV2>>> rejectedResult =
        client.getPayloadBodiesByHashV2(List.of(rejectedBlockHash));

    assertThatSafeFuture(rejectedResult).isCompletedExceptionallyWith(QueueIsFullException.class);
    verify(delegate, never()).getPayloadBodiesByHashV2(List.of(rejectedBlockHash));
    assertThat(getPayloadBodiesQueueSize()).isEqualTo(MAX_QUEUED_PAYLOAD_BODIES_REQUESTS);
  }

  @Test
  void shouldLimitConcurrentCriticalRequests() {
    final List<SafeFuture<Response<ForkChoiceUpdatedResult>>> results =
        IntStream.range(0, MAX_CONCURRENT_REQUESTS + 1)
            .mapToObj(__ -> forkChoiceUpdated())
            .toList();

    verify(delegate, times(MAX_CONCURRENT_REQUESTS)).forkChoiceUpdatedV4(any(), any());
    assertThat(results).noneMatch(SafeFuture::isDone);

    final Response<ForkChoiceUpdatedResult> response = Response.fromNullPayload();
    pendingForkChoiceUpdatedResponses.getFirst().complete(response);

    assertThat(results.getFirst()).isCompletedWithValue(response);
    verify(delegate, times(MAX_CONCURRENT_REQUESTS + 1)).forkChoiceUpdatedV4(any(), any());
  }

  /** Occupies every in-flight slot of the payload bodies queue and fills its backlog. */
  private void saturatePayloadBodiesQueue() {
    IntStream.range(0, MAX_CONCURRENT_PAYLOAD_BODIES_REQUESTS + MAX_QUEUED_PAYLOAD_BODIES_REQUESTS)
        .forEach(i -> ignoreFuture(client.getPayloadBodiesByHashV2(List.of(blockHash(i)))));
    verify(delegate, times(MAX_CONCURRENT_PAYLOAD_BODIES_REQUESTS)).getPayloadBodiesByHashV2(any());
  }

  private SafeFuture<Response<PayloadStatusV1>> newPayload() {
    return client.newPayloadV5(mock(ExecutionPayloadV4.class), List.of(), Bytes32.ZERO, List.of());
  }

  private SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated() {
    return client.forkChoiceUpdatedV4(FORK_CHOICE_STATE, Optional.empty());
  }

  private double getPayloadBodiesQueueSize() {
    return metricsSystem
        .getGauge(TekuMetricCategory.BEACON, "ee_payload_bodies_request_queue_size")
        .getValue();
  }

  private static Bytes32 blockHash(final int index) {
    return Bytes32.leftPad(Bytes.of(index));
  }
}
