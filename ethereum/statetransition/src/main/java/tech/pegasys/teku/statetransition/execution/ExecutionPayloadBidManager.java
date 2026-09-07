/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.BidForBlock;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ExecutionPayloadBidManager {

  enum RemoteBidOrigin {
    P2P,
    BUILDER
  }

  ExecutionPayloadBidManager NOOP =
      new ExecutionPayloadBidManager() {

        @Override
        public SafeFuture<InternalValidationResult> validateAndAddBid(
            final SignedExecutionPayloadBid signedBid, final RemoteBidOrigin remoteBidOrigin) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public void subscribeOperationAdded(
            final OperationAddedSubscriber<SignedExecutionPayloadBid> subscriber) {}

        @Override
        public SafeFuture<BidForBlock> getBidForBlock(
            final Bytes32 parentRoot,
            final Bytes32 parentBlockHash,
            final BeaconState state,
            final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
            final BuilderConfig builderConfig,
            final BlockProductionPerformance blockProductionPerformance) {
          return SafeFuture.completedFuture(null);
        }
      };

  SafeFuture<InternalValidationResult> validateAndAddBid(
      SignedExecutionPayloadBid signedBid, RemoteBidOrigin remoteBidOrigin);

  void subscribeOperationAdded(OperationAddedSubscriber<SignedExecutionPayloadBid> subscriber);

  SafeFuture<BidForBlock> getBidForBlock(
      Bytes32 parentRoot,
      Bytes32 parentBlockHash,
      BeaconState state,
      SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      BuilderConfig builderConfig,
      BlockProductionPerformance blockProductionPerformance);

  record LocalBid(
      SignedExecutionPayloadBid bid, UInt256 valueInWei, boolean shouldOverrideBuilder) {}

  // can represent both P2P bids and Builder API bids
  record RemoteBid(SignedExecutionPayloadBid bid, UInt64 valueInGwei, Optional<String> builderUrl) {

    public UInt64 builderIndex() {
      return bid.getMessage().getBuilderIndex();
    }
  }

  // The best bid determined for the block proposal after evaluating local bids, P2P bids, and
  // Builder API bids
  record BidForBlock(
      SignedExecutionPayloadBid bid, UInt256 valueInWei, Optional<String> builderUrl) {}
}
