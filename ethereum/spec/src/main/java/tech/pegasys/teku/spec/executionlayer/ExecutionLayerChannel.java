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

package tech.pegasys.teku.spec.executionlayer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public interface ExecutionLayerChannel extends ChannelInterface {
  String PREVIOUS_STUB_ENDPOINT_PREFIX = "stub";
  String STUB_ENDPOINT_PREFIX = "unsafe-test-stub";
  ExecutionLayerChannel NOOP =
      new ExecutionLayerChannel() {
        @Override
        public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<PowBlock> eth1GetPowChainHead() {
          throw new UnsupportedOperationException();
        }

        @Override
        public SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
            final ForkChoiceState forkChoiceState,
            final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
          return SafeFuture.completedFuture(
              new ForkChoiceUpdatedResult(PayloadStatus.SYNCING, Optional.empty()));
        }

        @Override
        public SafeFuture<GetPayloadResponse> engineGetPayload(
            final ExecutionPayloadContext executionPayloadContext, final BeaconState state) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<PayloadStatus> engineNewPayload(
            final NewPayloadRequest newPayloadRequest, final UInt64 slot) {
          return SafeFuture.completedFuture(PayloadStatus.SYNCING);
        }

        @Override
        public SafeFuture<List<ClientVersion>> engineGetClientVersion(
            final ClientVersion clientVersion) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<List<Optional<BlobAndProof>>> engineGetBlobAndProofs(
            final List<VersionedHash> blobVersionedHashes, final UInt64 slot) {
          return SafeFuture.completedFuture(
              blobVersionedHashes.stream().map(e -> Optional.<BlobAndProof>empty()).toList());
        }

        @Override
        public SafeFuture<List<BlobAndCellProofs>> engineGetBlobAndCellProofsList(
            final List<VersionedHash> blobVersionedHashes, final UInt64 slot) {
          return SafeFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public SafeFuture<List<ExecutionPayloadBody>> engineGetPayloadBodiesByHash(
            final List<Bytes32> blockHashes) {
          return SafeFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public SafeFuture<Void> builderRegisterValidators(
            final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
            final UInt64 slot) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<BuilderPayloadOrFallbackData> builderGetPayload(
            final SignedBeaconBlock signedBeaconBlock,
            final Function<UInt64, Optional<ExecutionPayloadResult>>
                getCachedPayloadResultFunction) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<BuilderBidOrFallbackData> builderGetHeader(
            final ExecutionPayloadContext executionPayloadContext,
            final BeaconState state,
            final Optional<UInt64> requestedBuilderBoostFactor,
            final BlockProductionPerformance blockProductionPerformance) {
          return SafeFuture.completedFuture(null);
        }
      };

  // eth namespace
  SafeFuture<Optional<PowBlock>> eth1GetPowBlock(Bytes32 blockHash);

  SafeFuture<PowBlock> eth1GetPowChainHead();

  // engine namespace
  SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      ForkChoiceState forkChoiceState,
      Optional<PayloadBuildingAttributes> payloadBuildingAttributes);

  SafeFuture<PayloadStatus> engineNewPayload(NewPayloadRequest newPayloadRequest, UInt64 slot);

  SafeFuture<List<ClientVersion>> engineGetClientVersion(ClientVersion clientVersion);

  SafeFuture<List<Optional<BlobAndProof>>> engineGetBlobAndProofs(
      List<VersionedHash> blobVersionedHashes, UInt64 slot);

  SafeFuture<List<BlobAndCellProofs>> engineGetBlobAndCellProofsList(
      List<VersionedHash> blobVersionedHashes, UInt64 slot);

  SafeFuture<List<ExecutionPayloadBody>> engineGetPayloadBodiesByHash(List<Bytes32> blockHashes);

  /**
   * This is low level method, use {@link
   * ExecutionLayerBlockProductionManager#initiateBlockProduction(ExecutionPayloadContext,
   * BeaconState, boolean, Optional, BlockProductionPerformance)} instead
   */
  SafeFuture<GetPayloadResponse> engineGetPayload(
      ExecutionPayloadContext executionPayloadContext, BeaconState state);

  // builder namespace
  SafeFuture<Void> builderRegisterValidators(
      SszList<SignedValidatorRegistration> signedValidatorRegistrations, UInt64 slot);

  /**
   * This is low level method, use {@link
   * ExecutionLayerBlockProductionManager#getUnblindedPayload(SignedBeaconBlock,
   * BlockPublishingPerformance)} instead
   */
  SafeFuture<BuilderPayloadOrFallbackData> builderGetPayload(
      SignedBeaconBlock signedBeaconBlock,
      Function<UInt64, Optional<ExecutionPayloadResult>> getCachedPayloadResultFunction);

  /**
   * This is low level method, use {@link
   * ExecutionLayerBlockProductionManager#initiateBlockProduction(ExecutionPayloadContext,
   * BeaconState, boolean, Optional, BlockProductionPerformance)} instead
   *
   * @param executionPayloadContext The execution payload context
   * @param state The beacon state
   * @param requestedBuilderBoostFactor The requested builder boost factor
   * @param blockProductionPerformance The performance tracker
   */
  SafeFuture<BuilderBidOrFallbackData> builderGetHeader(
      ExecutionPayloadContext executionPayloadContext,
      BeaconState state,
      Optional<UInt64> requestedBuilderBoostFactor,
      BlockProductionPerformance blockProductionPerformance);
}
