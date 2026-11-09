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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public interface ExecutionClientHandler {

  SafeFuture<Optional<PowBlock>> eth1GetPowBlock(Bytes32 blockHash);

  SafeFuture<PowBlock> eth1GetPowChainHead();

  SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      ForkChoiceState forkChoiceState,
      Optional<PayloadBuildingAttributes> payloadBuildingAttributes);

  SafeFuture<GetPayloadResponse> engineGetPayload(
      ExecutionPayloadContext executionPayloadContext, UInt64 slot);

  SafeFuture<PayloadStatus> engineNewPayload(NewPayloadRequest newPayloadRequest, UInt64 slot);

  SafeFuture<List<ClientVersion>> engineGetClientVersion(ClientVersion clientVersion);

  SafeFuture<List<BlobAndProof>> engineGetBlobsV1(
      List<VersionedHash> blobVersionedHashes, UInt64 slot);

  SafeFuture<List<BlobAndCellProofs>> engineGetBlobsV2(
      List<VersionedHash> blobVersionedHashes, UInt64 slot);

  SafeFuture<List<ExecutionPayloadBody>> engineGetPayloadBodiesByHash(List<Bytes32> blockHashes);
}
