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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarSignatureValidator {
  private final Spec spec;
  private final CombinedChainDataClient chainDataClient;
  private final LRUCache<Bytes32, SafeFuture<Boolean>>
      cachedSignatureValidationResultsBySignedHeaderRoot;

  public DataColumnSidecarSignatureValidator(
      final Spec spec, final CombinedChainDataClient chainDataClient) {
    this.spec = spec;
    this.chainDataClient = chainDataClient;

    // let's cache enough headers so that we can be effective even during syncing,
    // when we try to download columns for multiple blocks in parallel
    this.cachedSignatureValidationResultsBySignedHeaderRoot = LRUCache.create(100);
  }

  public SafeFuture<Boolean> validateSignature(final DataColumnSidecar sidecar) {
    final Optional<SignedBeaconBlockHeader> maybeSignedBlockHeader =
        sidecar.getMaybeSignedBlockHeader();
    if (maybeSignedBlockHeader.isEmpty()) {
      return SafeFuture.completedFuture(true);
    }

    final SignedBeaconBlockHeader signedBlockHeader = maybeSignedBlockHeader.get();

    return cachedSignatureValidationResultsBySignedHeaderRoot.get(
        signedBlockHeader.hashTreeRoot(),
        __ -> {
          final Optional<SafeFuture<BeaconState>> maybeState = chainDataClient.getBestState();
          if (maybeState.isEmpty()) {
            return SafeFuture.failedFuture(new RuntimeException("No beacon state available"));
          }

          final UInt64 epoch = spec.computeEpochAtSlot(sidecar.getSlot());
          final UInt64 proposerIndex = signedBlockHeader.getMessage().getProposerIndex();
          final Fork fork = spec.getForkSchedule().getFork(epoch);

          return maybeState
              .get()
              .thenApply(
                  state -> {
                    final Bytes32 domain =
                        spec.getDomain(
                            Domain.BEACON_PROPOSER, epoch, fork, state.getGenesisValidatorsRoot());
                    final Bytes signingRoot =
                        spec.computeSigningRoot(signedBlockHeader.getMessage(), domain);

                    return spec.getValidatorPubKey(state, proposerIndex)
                        .map(
                            pubKey ->
                                spec.getSpecConfig(epoch)
                                    .getBLSSignatureVerifier()
                                    .verify(pubKey, signingRoot, signedBlockHeader.getSignature()))
                        .orElse(false);
                  });
        });
  }
}
