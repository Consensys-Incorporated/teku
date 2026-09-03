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

import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.StakedBuilderClientProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class BuilderBidFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final StakedBuilderClientProvider stakedBuilderClientProvider;

  public BuilderBidFetcher(
      final Spec spec, final StakedBuilderClientProvider stakedBuilderClientProvider) {
    this.spec = spec;
    this.stakedBuilderClientProvider = stakedBuilderClientProvider;
  }

  public SafeFuture<List<SignedExecutionPayloadBid>> getBuilderBids(
      final BeaconState state,
      final UInt64 slot,
      final BuilderConfig builderConfig,
      final Bytes32 parentHash,
      final Bytes32 parentRoot) {
    final int proposerIndex =
        spec.atSlot(slot).beaconStateAccessors().getBeaconProposerIndex(state, slot);
    final BLSPublicKey proposerPubkey =
        spec.getValidatorPubKey(state, UInt64.valueOf(proposerIndex)).orElseThrow();
    final Stream<SafeFuture<Optional<SignedExecutionPayloadBid>>> builderBids =
        builderConfig.getBuilders().stream()
            .map(
                builderEntry ->
                    stakedBuilderClientProvider
                        .getClient(builderEntry.getUrl())
                        .getExecutionPayloadBid(
                            state.getSlot(),
                            parentHash,
                            parentRoot,
                            proposerPubkey,
                            builderEntry.getAuth())
                        .whenComplete(
                            (maybeBid, exception) -> {
                              if (exception != null) {
                                LOG.error(
                                    "Error while retrieving an execution payload bid from {}",
                                    builderEntry.getUrl(),
                                    exception);
                                return;
                              }
                              maybeBid.ifPresentOrElse(
                                  bid ->
                                      LOG.info(
                                          "Retrieved bid from {} (builder index: {}, value: {} ETH) for block at slot {}",
                                          builderEntry.getUrl(),
                                          bid.getMessage().getBuilderIndex(),
                                          gweiToEth(bid.getMessage().getValue()),
                                          slot),
                                  () ->
                                      LOG.info(
                                          "No bid available from {} for block at slot {}",
                                          builderEntry.getUrl(),
                                          slot));
                            }));
    // Remove empty responses and return only the successfully retrieved bids
    return SafeFuture.collectAllSuccessful(builderBids)
        // TODO-GLOAS: validate the builder bids https://github.com/Consensys/teku/issues/11191
        .thenApply(bids -> bids.stream().flatMap(Optional::stream).toList());
  }
}
