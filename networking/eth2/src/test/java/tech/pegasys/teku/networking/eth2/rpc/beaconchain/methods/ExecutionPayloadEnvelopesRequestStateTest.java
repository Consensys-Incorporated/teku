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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionPayloadEnvelopesRequestStateTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SignedExecutionPayloadEnvelope> callback =
      mock(ResponseCallback.class);

  private final ExecutionPayloadEnvelopesRequestState requestState =
      new ExecutionPayloadEnvelopesRequestState(callback, 5) {};

  @Test
  void shouldAccumulateCharges() {
    assertThat(requestState.getRequestedCount()).isEqualTo(5);
    assertThat(requestState.getChargedCount()).isZero();

    requestState.charge(2);
    requestState.charge(3);

    assertThat(requestState.getChargedCount()).isEqualTo(5);
  }

  @Test
  void shouldReportStreamClosureFromCallback() {
    assertThat(requestState.isStreamClosed()).isFalse();

    when(callback.isClosed()).thenReturn(true);

    assertThat(requestState.isStreamClosed()).isTrue();
  }

  @Test
  void shouldSendEnvelopesOneAfterTheOtherInOrder() {
    final SignedExecutionPayloadEnvelope first =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final SignedExecutionPayloadEnvelope second =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(2);
    final SafeFuture<Void> firstWrite = new SafeFuture<>();
    when(callback.respond(first)).thenReturn(firstWrite);
    when(callback.respond(second)).thenReturn(SafeFuture.COMPLETE);

    final SafeFuture<Void> sent =
        requestState.sendExecutionPayloadEnvelopes(List.of(first, second));

    // The second envelope is only written once the first write has completed
    verify(callback).respond(first);
    verify(callback, never()).respond(second);
    assertThat(sent).isNotDone();

    firstWrite.complete(null);

    final InOrder inOrder = inOrder(callback);
    inOrder.verify(callback).respond(first);
    inOrder.verify(callback).respond(second);
    assertThat(sent).isCompleted();
  }

  @Test
  void shouldCompleteImmediatelyWhenThereIsNothingToSend() {
    assertThat(requestState.sendExecutionPayloadEnvelopes(List.of())).isCompleted();
    verify(callback, never()).respond(any());
  }
}
