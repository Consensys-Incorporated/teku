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

package tech.pegasys.teku.networking.eth2.peers;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public class StubSyncSource implements SyncSource {

  private final List<Request> blocksRequests = new ArrayList<>();
  private final List<Request> blobSidecarsRequests = new ArrayList<>();
  private final List<Request> dataColumnSidecarsRequests = new ArrayList<>();
  private final List<Request> executionPayloadEnvelopesByRangeRequests = new ArrayList<>();
  private final List<Bytes32> executionPayloadEnvelopeByRootRequests = new ArrayList<>();

  private Optional<SafeFuture<Void>> currentBlockRequest = Optional.empty();
  private Optional<RpcResponseListener<SignedBeaconBlock>> currentBlockListener = Optional.empty();

  private Optional<SafeFuture<Void>> currentBlobSidecarRequest = Optional.empty();
  private Optional<RpcResponseListener<BlobSidecar>> currentBlobSidecarListener = Optional.empty();

  private Optional<SafeFuture<Void>> currentDataColumnSidecarRequest = Optional.empty();
  private Optional<RpcResponseListener<DataColumnSidecar>> currentDataColumnSidecarListener =
      Optional.empty();

  private Optional<SafeFuture<Void>> currentExecutionPayloadEnvelopesRequest = Optional.empty();
  private Optional<RpcResponseListener<SignedExecutionPayloadEnvelope>>
      currentExecutionPayloadEnvelopesListener = Optional.empty();

  public void receiveBlocks(final SignedBeaconBlock... blocks) {
    final RpcResponseListener<SignedBeaconBlock> listener = currentBlockListener.orElseThrow();
    Stream.of(blocks).forEach(response -> assertThat(listener.onResponse(response)).isCompleted());
    currentBlockRequest.orElseThrow().complete(null);
  }

  public void receiveBlobSidecars(final BlobSidecar... blobSidecars) {
    final RpcResponseListener<BlobSidecar> listener = currentBlobSidecarListener.orElseThrow();
    Stream.of(blobSidecars)
        .forEach(response -> assertThat(listener.onResponse(response)).isCompleted());
    currentBlobSidecarRequest.orElseThrow().complete(null);
  }

  public void receiveDataColumnSidecars(final DataColumnSidecar... dataColumnSidecars) {
    final RpcResponseListener<DataColumnSidecar> listener =
        currentDataColumnSidecarListener.orElseThrow();
    Stream.of(dataColumnSidecars)
        .forEach(response -> assertThat(listener.onResponse(response)).isCompleted());
    currentDataColumnSidecarRequest.orElseThrow().complete(null);
  }

  public void receiveExecutionPayloadEnvelopes(
      final SignedExecutionPayloadEnvelope... executionPayloadEnvelopes) {
    final RpcResponseListener<SignedExecutionPayloadEnvelope> listener =
        currentExecutionPayloadEnvelopesListener.orElseThrow();
    Stream.of(executionPayloadEnvelopes)
        .forEach(response -> assertThat(listener.onResponse(response)).isCompleted());
    currentExecutionPayloadEnvelopesRequest.orElseThrow().complete(null);
  }

  public void failRequest(final Throwable error) {
    currentBlockRequest.orElseThrow().completeExceptionally(error);
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedBeaconBlock> listener) {
    checkArgument(count.isGreaterThan(UInt64.ZERO), "Count must be greater than zero");
    blocksRequests.add(new Request(startSlot, count));
    final SafeFuture<Void> request = new SafeFuture<>();
    currentBlockRequest = Optional.of(request);
    currentBlockListener = Optional.of(listener);
    return request;
  }

  @Override
  public SafeFuture<Void> requestBlobSidecarsByRange(
      final UInt64 startSlot, final UInt64 count, final RpcResponseListener<BlobSidecar> listener) {
    checkArgument(count.isGreaterThan(UInt64.ZERO), "Count must be greater than zero");
    blobSidecarsRequests.add(new Request(startSlot, count));
    final SafeFuture<Void> request = new SafeFuture<>();
    currentBlobSidecarRequest = Optional.of(request);
    currentBlobSidecarListener = Optional.of(listener);
    return request;
  }

  @Override
  public SafeFuture<Void> requestDataColumnSidecarsByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final List<UInt64> columns,
      final RpcResponseListener<DataColumnSidecar> listener) {
    checkArgument(count.isGreaterThan(UInt64.ZERO), "Count must be greater than zero");
    dataColumnSidecarsRequests.add(new Request(startSlot, count, columns));
    final SafeFuture<Void> request = new SafeFuture<>();
    currentDataColumnSidecarRequest = Optional.of(request);
    currentDataColumnSidecarListener = Optional.of(listener);
    return request;
  }

  @Override
  public SafeFuture<Void> requestExecutionPayloadEnvelopesByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> listener) {
    checkArgument(count.isGreaterThan(UInt64.ZERO), "Count must be greater than zero");
    executionPayloadEnvelopesByRangeRequests.add(new Request(startSlot, count));
    final SafeFuture<Void> request = new SafeFuture<>();
    currentExecutionPayloadEnvelopesRequest = Optional.of(request);
    currentExecutionPayloadEnvelopesListener = Optional.of(listener);
    return request;
  }

  @Override
  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>> requestExecutionPayloadEnvelopeByRoot(
      final Bytes32 beaconBlockRoot) {
    executionPayloadEnvelopeByRootRequests.add(beaconBlockRoot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    return SafeFuture.COMPLETE;
  }

  public void assertRequestedBlocks(final long startSlot, final long count) {
    assertThat(blocksRequests)
        .contains(new Request(UInt64.valueOf(startSlot), UInt64.valueOf(count)));
  }

  public void assertRequestedBlobSidecars(final long startSlot, final long count) {
    assertThat(blobSidecarsRequests)
        .contains(new Request(UInt64.valueOf(startSlot), UInt64.valueOf(count)));
  }

  public void assertRequestedDataColumnSidecars(
      final long startSlot, final long count, final List<UInt64> columns) {
    assertThat(dataColumnSidecarsRequests)
        .contains(new Request(UInt64.valueOf(startSlot), UInt64.valueOf(count), columns));
  }

  public void assertRequestedExecutionPayloadEnvelopes(final long startSlot, final long count) {
    assertThat(executionPayloadEnvelopesByRangeRequests)
        .contains(new Request(UInt64.valueOf(startSlot), UInt64.valueOf(count)));
  }

  public void assertRequestedExecutionPayloadEnvelopeByRoot(final Bytes32 beaconBlockRoot) {
    assertThat(executionPayloadEnvelopeByRootRequests).contains(beaconBlockRoot);
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {}

  private static final class Request {
    private final UInt64 start;
    private final UInt64 count;
    private final Optional<List<UInt64>> columns;

    private Request(final UInt64 start, final UInt64 count) {
      this.start = start;
      this.count = count;
      columns = Optional.empty();
    }

    private Request(final UInt64 start, final UInt64 count, final List<UInt64> columns) {
      this.start = start;
      this.count = count;
      this.columns = Optional.of(columns);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Request request = (Request) o;
      return Objects.equals(start, request.start)
          && Objects.equals(count, request.count)
          && Objects.equals(columns, request.columns);
    }

    @Override
    public int hashCode() {
      return Objects.hash(start, count, columns);
    }
  }
}
