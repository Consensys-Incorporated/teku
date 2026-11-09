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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;

public class GetPayloadV4Response {
  public final ExecutionPayloadV3 executionPayload;

  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  public final UInt256 blockValue;

  public final BlobsBundleV1 blobsBundle;

  public final boolean shouldOverrideBuilder;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> executionRequests;

  public GetPayloadV4Response(
      final @JsonProperty("executionPayload") ExecutionPayloadV3 executionPayload,
      final @JsonProperty("blockValue") UInt256 blockValue,
      final @JsonProperty("blobsBundle") BlobsBundleV1 blobsBundle,
      final @JsonProperty("shouldOverrideBuilder") boolean shouldOverrideBuilder,
      final @JsonProperty("executionRequests") List<Bytes> executionRequests) {
    checkNotNull(executionPayload, "executionPayload");
    checkNotNull(blockValue, "blockValue");
    checkNotNull(blobsBundle, "blobsBundle");
    checkNotNull(executionRequests, "executionRequests");
    this.executionPayload = executionPayload;
    this.blockValue = blockValue;
    this.blobsBundle = blobsBundle;
    this.shouldOverrideBuilder = shouldOverrideBuilder;
    this.executionRequests = executionRequests;
  }

  public GetPayloadResponse asInternalGetPayloadResponse(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final BlobSchema blobSchema,
      final ExecutionRequestsDataCodec executionRequestsDataCodec) {
    return new GetPayloadResponse(
        executionPayload.asInternalExecutionPayload(executionPayloadSchema),
        blockValue,
        blobsBundle.asInternalBlobsBundle(blobSchema),
        shouldOverrideBuilder,
        executionRequestsDataCodec.decode(executionRequests));
  }
}
