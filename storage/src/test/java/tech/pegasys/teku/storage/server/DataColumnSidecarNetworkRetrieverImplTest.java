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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;

public class DataColumnSidecarNetworkRetrieverImplTest {

  @SuppressWarnings("unchecked")
  final Function<DataColumnSlotAndIdentifier, SafeFuture<DataColumnSidecar>> retriever =
      mock(Function.class);

  final Runnable retrieverFlusher = mock(Runnable.class);

  @SuppressWarnings("unchecked")
  final Function<DataColumnSidecar, SafeFuture<Void>> dbWriter = mock(Function.class);

  final Duration retrievalTimeout = Duration.ofSeconds(10);

  final DataColumnSidecarNetworkRetriever networkRetriever =
      new DataColumnSidecarNetworkRetrieverImpl(
          retriever, retrieverFlusher, dbWriter, retrievalTimeout);

  final DataColumnSlotAndIdentifier id1 = mock(DataColumnSlotAndIdentifier.class);
  final DataColumnSlotAndIdentifier id2 = mock(DataColumnSlotAndIdentifier.class);
  final DataColumnSidecar sidecar1 = mock(DataColumnSidecar.class);
  final DataColumnSidecar sidecar2 = mock(DataColumnSidecar.class);

  @Test
  void shouldReturnSidecarsWhenSuccessful() {
    var pendingSidecar2 = new SafeFuture<DataColumnSidecar>();
    when(retriever.apply(id1)).thenReturn(SafeFuture.completedFuture(sidecar1));
    when(retriever.apply(id2)).thenReturn(pendingSidecar2);

    final SafeFuture<List<DataColumnSidecar>> result =
        networkRetriever.retrieveDataColumnSidecars(List.of(id1, id2));

    verify(retrieverFlusher).run();

    verify(dbWriter).apply(sidecar1);

    assertThat(result).isNotCompleted();

    pendingSidecar2.complete(sidecar2);
    verify(dbWriter).apply(sidecar2);

    assertThat(result).isCompleted();
    assertThat(result.join()).containsExactly(sidecar1, sidecar2);
  }

  @Test
  void shouldNotFailIfDbWriteFails() {
    when(retriever.apply(id1)).thenReturn(SafeFuture.completedFuture(sidecar1));
    when(dbWriter.apply(sidecar1)).thenReturn(SafeFuture.failedFuture(new RuntimeException()));

    final SafeFuture<List<DataColumnSidecar>> result =
        networkRetriever.retrieveDataColumnSidecars(List.of(id1));

    verify(dbWriter).apply(sidecar1);

    assertThat(result).isCompleted();
    assertThat(result.join()).containsExactly(sidecar1);
  }

  @Test
  void shouldNotRetrieveOrFlushWhenRequestListIsEmpty() {
    final SafeFuture<List<DataColumnSidecar>> result =
        networkRetriever.retrieveDataColumnSidecars(List.of());

    assertThat(result).isCompleted();
    assertThat(result.join()).isEmpty();

    verify(retriever, never()).apply(any());
    verify(retrieverFlusher, never()).run();
  }

  @Test
  void shouldReturnEmptyListWhenOneRequestFails() {
    // One succeeds, one fails
    when(retriever.apply(id1)).thenReturn(SafeFuture.completedFuture(sidecar1));
    when(retriever.apply(id2))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Network Error")));

    final SafeFuture<List<DataColumnSidecar>> result =
        networkRetriever.retrieveDataColumnSidecars(List.of(id1, id2));

    // The logic captures the exception, logs it, and returns an empty list
    assertThat(result).isCompleted();
    assertThat(result.join()).isEmpty();

    // sidecar1 has been written anyway
    verify(dbWriter).apply(sidecar1);
  }

  @Test
  void shouldCancelPendingRequestsAndReturnEmptyListOnTimeout() {
    // We create a specific instance with a short timeout for this test
    final Duration shortTimeout = Duration.ofMillis(10);
    final DataColumnSidecarNetworkRetriever timeoutRetriever =
        new DataColumnSidecarNetworkRetrieverImpl(
            retriever, retrieverFlusher, dbWriter, shortTimeout);

    final SafeFuture<DataColumnSidecar> pendingFuture = new SafeFuture<>();
    when(retriever.apply(id1)).thenReturn(pendingFuture);

    final SafeFuture<List<DataColumnSidecar>> result =
        timeoutRetriever.retrieveDataColumnSidecars(List.of(id1));

    assertThat(result).succeedsWithin(1, TimeUnit.SECONDS);
    assertThatSafeFuture(result).isCompletedWithValueMatching(List::isEmpty);

    // verify the logic canceled the pending upstream request
    assertThat(pendingFuture.isCancelled()).isTrue();
  }

  @Test
  void isEnabled() {
    assertThat(networkRetriever.isEnabled()).isTrue();
  }

  @Test
  void disabledIsDisabled() {
    assertThat(DataColumnSidecarNetworkRetriever.DISABLED.isEnabled()).isFalse();
  }
}
