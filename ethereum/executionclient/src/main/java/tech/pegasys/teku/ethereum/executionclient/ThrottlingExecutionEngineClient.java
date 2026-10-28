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

import static tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.DEFAULT_MAXIMUM_QUEUE_SIZE;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ClientVersionV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadBodyV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV4;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV4Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV6Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV4;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class ThrottlingExecutionEngineClient implements ExecutionEngineClient {
  private final ExecutionEngineClient delegate;
  private final ThrottlingTaskQueue taskQueue;
  private final ThrottlingTaskQueue payloadBodiesTaskQueue;

  public ThrottlingExecutionEngineClient(
      final ExecutionEngineClient delegate,
      final int maximumConcurrentRequests,
      final int maximumConcurrentPayloadBodiesRequests,
      final int maximumQueuedPayloadBodiesRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        ThrottlingTaskQueue.create(
            maximumConcurrentRequests,
            DEFAULT_MAXIMUM_QUEUE_SIZE,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "ee_request_queue_size",
            "ee_request_queue_rejected");
    payloadBodiesTaskQueue =
        ThrottlingTaskQueue.create(
            maximumConcurrentPayloadBodiesRequests,
            maximumQueuedPayloadBodiesRequests,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "ee_payload_bodies_request_queue_size",
            "ee_payload_bodies_request_queue_rejected");
  }

  @Override
  public SafeFuture<PowBlock> getPowBlock(final Bytes32 blockHash) {
    return taskQueue.queueTask(() -> delegate.getPowBlock(blockHash));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return taskQueue.queueTask(delegate::getPowChainHead);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV1(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV2(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV3(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV4Response>> getPayloadV4(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV4(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV5Response>> getPayloadV5(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV5(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV6Response>> getPayloadV6(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV6(payloadId));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(
      final ExecutionPayloadV1 executionPayload) {
    return taskQueue.queueTask(() -> delegate.newPayloadV1(executionPayload));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(
      final ExecutionPayloadV2 executionPayload) {
    return taskQueue.queueTask(() -> delegate.newPayloadV2(executionPayload));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    return taskQueue.queueTask(
        () -> delegate.newPayloadV3(executionPayload, blobVersionedHashes, parentBeaconBlockRoot));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV4(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    return taskQueue.queueTask(
        () ->
            delegate.newPayloadV4(
                executionPayload, blobVersionedHashes, parentBeaconBlockRoot, executionRequests));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV5(
      final ExecutionPayloadV4 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    return taskQueue.queueTask(
        () ->
            delegate.newPayloadV5(
                executionPayload, blobVersionedHashes, parentBeaconBlockRoot, executionRequests));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV1(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV2> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV2(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV3(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV3(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV4(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV4> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV4(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
    return taskQueue.queueTask(() -> delegate.exchangeCapabilities(capabilities));
  }

  @Override
  public SafeFuture<Response<List<ClientVersionV1>>> getClientVersionV1(
      final ClientVersionV1 clientVersion) {
    return taskQueue.queueTask(() -> delegate.getClientVersionV1(clientVersion));
  }

  @Override
  public SafeFuture<Response<List<BlobAndProofV1>>> getBlobsV1(
      final List<VersionedHash> blobVersionedHashes) {
    return taskQueue.queueTask(() -> delegate.getBlobsV1(blobVersionedHashes));
  }

  @Override
  public SafeFuture<Response<List<BlobAndProofV2>>> getBlobsV2(
      final List<VersionedHash> blobVersionedHashes) {
    return taskQueue.queueTask(() -> delegate.getBlobsV2(blobVersionedHashes));
  }

  /** Peer-driven, so served from a separate bounded queue to never delay critical engine calls. */
  @Override
  public SafeFuture<Response<List<ExecutionPayloadBodyV2>>> getPayloadBodiesByHashV2(
      final List<Bytes32> blockHashes) {
    return payloadBodiesTaskQueue.queueTask(() -> delegate.getPayloadBodiesByHashV2(blockHashes));
  }
}
