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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.ThrottlingSyncSource.RpcResponseListenerWithCount;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

@SuppressWarnings("unchecked")
class ThrottlingSyncSourceTest {

  private static final int MAX_BLOCKS_PER_MINUTE = 100;
  private static final int MAX_BLOBS_PER_BLOCK = 6;
  private static final int MAX_BLOB_SIDECARS_PER_MINUTE = 100;
  private static final int MAX_EXECUTION_PAYLOAD_ENVELOPES_PER_MINUTE = 100;

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final SyncSource delegate = mock(SyncSource.class);

  private final RpcResponseListener<SignedBeaconBlock> blocksListener =
      mock(RpcResponseListener.class);

  private final RpcResponseListener<BlobSidecar> blobSidecarsListener =
      mock(RpcResponseListener.class);

  private final RpcResponseListener<SignedExecutionPayloadEnvelope>
      executionPayloadEnvelopesListener = mock(RpcResponseListener.class);

  private final ThrottlingSyncSource source =
      new ThrottlingSyncSource(
          asyncRunner,
          timeProvider,
          delegate,
          MAX_BLOCKS_PER_MINUTE,
          Optional.of(MAX_BLOBS_PER_BLOCK),
          Optional.of(MAX_BLOB_SIDECARS_PER_MINUTE),
          Optional.empty(),
          Optional.of(MAX_EXECUTION_PAYLOAD_ENVELOPES_PER_MINUTE));

  @BeforeEach
  void setup() {
    // simulate complete RPC responses
    when(delegate.requestBlocksByRange(any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              final UInt64 count = invocationOnMock.getArgument(1);
              final RpcResponseListenerWithCount<SignedBeaconBlock> listener =
                  invocationOnMock.getArgument(2);
              UInt64.range(UInt64.ZERO, count)
                  .forEach(__ -> ignoreFuture(listener.onResponse(mock(SignedBeaconBlock.class))));
              return SafeFuture.COMPLETE;
            });
    when(delegate.requestBlobSidecarsByRange(any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              final UInt64 count = invocationOnMock.getArgument(1);
              final RpcResponseListenerWithCount<BlobSidecar> listener =
                  invocationOnMock.getArgument(2);
              UInt64.range(UInt64.ZERO, count.times(MAX_BLOBS_PER_BLOCK))
                  .forEach(__ -> ignoreFuture(listener.onResponse(mock(BlobSidecar.class))));
              return SafeFuture.COMPLETE;
            });
    when(delegate.requestExecutionPayloadEnvelopesByRange(any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              final UInt64 count = invocationOnMock.getArgument(1);
              final RpcResponseListenerWithCount<SignedExecutionPayloadEnvelope> listener =
                  invocationOnMock.getArgument(2);
              UInt64.range(UInt64.ZERO, count)
                  .forEach(
                      __ ->
                          ignoreFuture(
                              listener.onResponse(mock(SignedExecutionPayloadEnvelope.class))));
              return SafeFuture.COMPLETE;
            });
  }

  @Test
  void shouldDelegateDisconnectImmediately() {
    final SafeFuture<Void> result = new SafeFuture<>();
    when(delegate.disconnectCleanly(DisconnectReason.REMOTE_FAULT)).thenReturn(result);

    final SafeFuture<Void> actual = source.disconnectCleanly(DisconnectReason.REMOTE_FAULT);

    ignoreFuture(verify(delegate).disconnectCleanly(DisconnectReason.REMOTE_FAULT));

    assertThat(actual).isSameAs(result);
  }

  @Test
  void shouldRequestBlocksImmediatelyIfRateLimitNotExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_BLOCKS_PER_MINUTE - 1);
    ignoreFuture(source.requestBlocksByRange(UInt64.ZERO, count, blocksListener));
    ignoreFuture(source.requestBlocksByRange(count, count, blocksListener));

    // Both requests happen immediately
    ignoreFuture(
        verify(delegate)
            .requestBlocksByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    ignoreFuture(
        verify(delegate)
            .requestBlocksByRange(eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldRequestBlobSidecarsImmediatelyIfRateLimitNotExceeded() {
    // 100 / 6 = 16, 16 * 6 = 92 < 100
    final UInt64 count = UInt64.valueOf(16);
    ignoreFuture(source.requestBlobSidecarsByRange(UInt64.ZERO, count, blobSidecarsListener));
    ignoreFuture(source.requestBlobSidecarsByRange(count, count, blobSidecarsListener));

    // Both requests happen immediately
    ignoreFuture(
        verify(delegate)
            .requestBlobSidecarsByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    ignoreFuture(
        verify(delegate)
            .requestBlobSidecarsByRange(
                eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldRequestExecutionPayloadEnvelopesImmediatelyIfRateLimitNotExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_EXECUTION_PAYLOAD_ENVELOPES_PER_MINUTE - 1);
    ignoreFuture(
        source.requestExecutionPayloadEnvelopesByRange(
            UInt64.ZERO, count, executionPayloadEnvelopesListener));
    ignoreFuture(
        source.requestExecutionPayloadEnvelopesByRange(
            count, count, executionPayloadEnvelopesListener));

    // Both requests happen immediately
    ignoreFuture(
        verify(delegate)
            .requestExecutionPayloadEnvelopesByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    ignoreFuture(
        verify(delegate)
            .requestExecutionPayloadEnvelopesByRange(
                eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldDelayRequestIfBlockLimitAlreadyExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_BLOCKS_PER_MINUTE);
    ignoreFuture(source.requestBlocksByRange(UInt64.ZERO, count, blocksListener));
    ignoreFuture(source.requestBlocksByRange(count, count, blocksListener));

    // Second request should be delayed
    ignoreFuture(
        verify(delegate)
            .requestBlocksByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(61);
    asyncRunner.executeQueuedActions();

    ignoreFuture(
        verify(delegate)
            .requestBlocksByRange(eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldDelayRequestIfBlobSidecarsLimitAlreadyExceeded() {
    // 17 * 6 = 102 > 100
    final UInt64 count = UInt64.valueOf(17);
    ignoreFuture(source.requestBlobSidecarsByRange(UInt64.ZERO, count, blobSidecarsListener));
    ignoreFuture(source.requestBlobSidecarsByRange(count, count, blobSidecarsListener));

    // Second request should be delayed
    ignoreFuture(
        verify(delegate)
            .requestBlobSidecarsByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(61);
    asyncRunner.executeQueuedActions();

    ignoreFuture(
        verify(delegate)
            .requestBlobSidecarsByRange(
                eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldDelayRequestIfExecutionPayloadEnvelopeLimitAlreadyExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_EXECUTION_PAYLOAD_ENVELOPES_PER_MINUTE);
    ignoreFuture(
        source.requestExecutionPayloadEnvelopesByRange(
            UInt64.ZERO, count, executionPayloadEnvelopesListener));
    ignoreFuture(
        source.requestExecutionPayloadEnvelopesByRange(
            count, count, executionPayloadEnvelopesListener));

    // Second request should be delayed
    ignoreFuture(
        verify(delegate)
            .requestExecutionPayloadEnvelopesByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(61);
    asyncRunner.executeQueuedActions();

    ignoreFuture(
        verify(delegate)
            .requestExecutionPayloadEnvelopesByRange(
                eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldContinueDelayingBlocksRequestIfRequestStillExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_BLOCKS_PER_MINUTE);
    ignoreFuture(source.requestBlocksByRange(UInt64.ZERO, count, blocksListener));
    ignoreFuture(source.requestBlocksByRange(count, count, blocksListener));

    // Second request should be delayed
    ignoreFuture(
        verify(delegate)
            .requestBlocksByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(30);
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(31);
    asyncRunner.executeQueuedActions();
    ignoreFuture(
        verify(delegate)
            .requestBlocksByRange(eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldContinueDelayingBlobSidecarsRequestIfRequestStillExceeded() {
    // 17 * 6 = 102 > 100
    final UInt64 count = UInt64.valueOf(17);
    ignoreFuture(source.requestBlobSidecarsByRange(UInt64.ZERO, count, blobSidecarsListener));
    ignoreFuture(source.requestBlobSidecarsByRange(count, count, blobSidecarsListener));

    // Second request should be delayed
    ignoreFuture(
        verify(delegate)
            .requestBlobSidecarsByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(30);
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(31);
    asyncRunner.executeQueuedActions();
    ignoreFuture(
        verify(delegate)
            .requestBlobSidecarsByRange(
                eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }

  @Test
  void shouldContinueDelayingExecutionPayloadEnvelopesRequestIfRequestStillExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_EXECUTION_PAYLOAD_ENVELOPES_PER_MINUTE);
    ignoreFuture(
        source.requestExecutionPayloadEnvelopesByRange(
            UInt64.ZERO, count, executionPayloadEnvelopesListener));
    ignoreFuture(
        source.requestExecutionPayloadEnvelopesByRange(
            count, count, executionPayloadEnvelopesListener));

    // Second request should be delayed
    ignoreFuture(
        verify(delegate)
            .requestExecutionPayloadEnvelopesByRange(
                eq(UInt64.ZERO), eq(count), any(RpcResponseListenerWithCount.class)));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(30);
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(31);
    asyncRunner.executeQueuedActions();
    ignoreFuture(
        verify(delegate)
            .requestExecutionPayloadEnvelopesByRange(
                eq(count), eq(count), any(RpcResponseListenerWithCount.class)));
  }
}
