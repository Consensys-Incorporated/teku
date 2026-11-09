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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class FuluExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalFulu();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void engineGetPayload_shouldCallGetPayloadV5() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayloadContext context = randomContext();
    final GetPayloadV5Response responseData =
        new GetPayloadV5Response(
            ExecutionPayloadV3.fromInternalExecutionPayload(
                dataStructureUtil.randomExecutionPayload()),
            UInt256.MAX_VALUE,
            BlobsBundleV2.fromInternalBlobsBundle(dataStructureUtil.randomBlobsBundle()),
            true,
            dataStructureUtil.randomEncodedExecutionRequests());
    final SafeFuture<Response<GetPayloadV5Response>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.getPayloadV5(context.getPayloadId())).thenReturn(dummyResponse);

    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final SafeFuture<GetPayloadResponse> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV5(context.getPayloadId());
    final SchemaDefinitionsFulu schemaDefinitionFulu =
        SchemaDefinitionsFulu.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadSchema<?> executionPayloadSchema =
        schemaDefinitionFulu.getExecutionPayloadSchema();
    final BlobSchema blobSchema = schemaDefinitionFulu.getBlobSchema();
    final GetPayloadResponse expectedGetPayloadResponse =
        responseData.asInternalGetPayloadResponse(
            executionPayloadSchema, blobSchema, spec.getExecutionRequestsDataCodec(slot));
    assertThat(future).isCompletedWithValue(expectedGetPayloadResponse);
  }

  @Test
  void engineNewPayload_shouldCallNewPayloadV4() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final List<VersionedHash> versionedHashes = dataStructureUtil.randomVersionedHashes(3);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final List<Bytes> encodedExecutionRequests = dataStructureUtil.randomEncodedExecutionRequests();
    final NewPayloadRequest newPayloadRequest =
        new NewPayloadRequest(
            payload, versionedHashes, parentBeaconBlockRoot, encodedExecutionRequests);
    final ExecutionPayloadV3 payloadV3 = ExecutionPayloadV3.fromInternalExecutionPayload(payload);
    final PayloadStatusV1 responseData =
        new PayloadStatusV1(
            ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.newPayloadV4(
            eq(payloadV3),
            eq(versionedHashes),
            eq(parentBeaconBlockRoot),
            eq(encodedExecutionRequests)))
        .thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future =
        handler.engineNewPayload(newPayloadRequest, UInt64.ZERO);
    verify(executionEngineClient)
        .newPayloadV4(
            eq(payloadV3),
            eq(versionedHashes),
            eq(parentBeaconBlockRoot),
            eq(encodedExecutionRequests));
    assertThat(future).isCompletedWithValue(responseData.asInternalExecutionPayload());
  }

  @Test
  void engineForkChoiceUpdated_shouldCallEngineForkChoiceUpdatedV3() {
    final ExecutionClientHandler handler = getHandler();
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
    final ForkChoiceStateV1 forkChoiceStateV1 =
        ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState);
    final PayloadBuildingAttributes attributes =
        new PayloadBuildingAttributes(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            Optional.empty(),
            Optional.of(List.of()),
            ForkChoiceNode.createBase(dataStructureUtil.randomBytes32()));
    final Optional<PayloadAttributesV3> payloadAttributes =
        PayloadAttributesV3.fromInternalPayloadBuildingAttributesV3(Optional.of(attributes));
    final ForkChoiceUpdatedResult responseData =
        new ForkChoiceUpdatedResult(
            new PayloadStatusV1(
                ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), ""),
            dataStructureUtil.randomBytes8());
    final SafeFuture<Response<ForkChoiceUpdatedResult>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.forkChoiceUpdatedV3(forkChoiceStateV1, payloadAttributes))
        .thenReturn(dummyResponse);
    final SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult> future =
        handler.engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
    verify(executionEngineClient).forkChoiceUpdatedV3(forkChoiceStateV1, payloadAttributes);
    assertThat(future).isCompletedWithValue(responseData.asInternalExecutionPayload());
  }

  @Test
  void engineGetBlobs_shouldCallGetBlobsV2() {
    final ExecutionClientHandler handler = getHandler();
    final int maxBlobsPerBlock =
        SpecConfigDeneb.required(spec.getGenesisSpecConfig()).getMaxBlobsPerBlock();
    final List<VersionedHash> versionedHashes =
        dataStructureUtil.randomVersionedHashes(maxBlobsPerBlock);
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle(maxBlobsPerBlock);
    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final List<BlobAndProofV2> responseData =
        IntStream.range(0, blobsBundle.getBlobs().size())
            .mapToObj(
                i ->
                    new BlobAndProofV2(
                        blobsBundle.getBlobs().get(i).getBytes(),
                        blobsBundle
                            .getProofs()
                            .subList(i * CELLS_PER_EXT_BLOB, (i + 1) * CELLS_PER_EXT_BLOB)
                            .stream()
                            .map(KZGProof::getBytesCompressed)
                            .toList()))
            .toList();

    final SafeFuture<Response<List<BlobAndProofV2>>> dummyResponse =
        SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData));
    when(executionEngineClient.getBlobsV2(versionedHashes)).thenReturn(dummyResponse);
    final SafeFuture<List<BlobAndCellProofs>> future =
        handler.engineGetBlobsV2(versionedHashes, slot);
    verify(executionEngineClient).getBlobsV2(versionedHashes);
    final BlobSchema blobSchema =
        SchemaDefinitionsFulu.required(spec.atSlot(slot).getSchemaDefinitions()).getBlobSchema();
    assertThat(future)
        .isCompletedWithValue(
            responseData.stream()
                .map(blobAndProofV2 -> blobAndProofV2.asInternalBlobAndProofs(blobSchema))
                .toList());
  }

  private ExecutionPayloadContext randomContext() {
    return new ExecutionPayloadContext(
        dataStructureUtil.randomBytes8(),
        dataStructureUtil.randomForkChoiceState(false),
        dataStructureUtil.randomPayloadBuildingAttributes(false));
  }
}
