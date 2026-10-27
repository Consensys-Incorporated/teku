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

package tech.pegasys.teku.statetransition.lightclient;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class LightClientBootstrapProvider {

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;

  public LightClientBootstrapProvider(
      final Spec spec, final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public SafeFuture<Optional<LightClientBootstrap>> getLightClientBootstrap(
      final Bytes32 blockRoot) {
    return combinedChainDataClient
        .getBlockByBlockRoot(blockRoot)
        .thenCompose(
            maybeBlock ->
                maybeBlock
                    .map(block -> createBootstrapIfFinalizedEpochBoundary(blockRoot, block))
                    .orElse(SafeFuture.completedFuture(Optional.empty())));
  }

  private SafeFuture<Optional<LightClientBootstrap>> createBootstrapIfFinalizedEpochBoundary(
      final Bytes32 blockRoot, final SignedBeaconBlock block) {
    final UInt64 blockEpoch = spec.computeEpochAtSlot(block.getSlot());
    final UInt64 boundaryEpoch =
        spec.computeStartSlotAtEpoch(blockEpoch).equals(block.getSlot())
            ? blockEpoch
            : blockEpoch.plus(1);
    if (!isWithinServableRange(boundaryEpoch)
        || !combinedChainDataClient.isFinalizedEpoch(boundaryEpoch)) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return combinedChainDataClient
        .getBlockInEffectAtSlot(spec.computeStartSlotAtEpoch(boundaryEpoch))
        .thenCompose(
            maybeBoundaryBlock ->
                maybeBoundaryBlock
                        .map(SignedBeaconBlock::getRoot)
                        .filter(blockRoot::equals)
                        .isPresent()
                    ? createBootstrap(blockRoot, block)
                    : SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<LightClientBootstrap>> createBootstrap(
      final Bytes32 blockRoot, final SignedBeaconBlock block) {
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(
            maybeState ->
                maybeState.flatMap(
                    state ->
                        spec.getLightClientUtil(block.getSlot())
                            .map(util -> util.createLightClientBootstrap(state, block))));
  }

  private boolean isWithinServableRange(final UInt64 boundaryEpoch) {
    final UInt64 currentEpoch =
        combinedChainDataClient.getRecentChainData().getCurrentEpoch().orElse(UInt64.ZERO);
    final UInt64 minEpoch =
        currentEpoch.minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests());
    return boundaryEpoch.isGreaterThanOrEqualTo(minEpoch);
  }
}
