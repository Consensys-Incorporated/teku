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

package tech.pegasys.teku.builder.rest;

import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.handlers.GetExecutionPayloadBidRequest;
import tech.pegasys.teku.builder.rest.handlers.SubmitBuilderPreferencesRequest;
import tech.pegasys.teku.builder.rest.handlers.SubmitSignedBeaconBlockRequest;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesRequest;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;

class OkHttpStakedBuilderClient implements StakedBuilderClient {

  private final AsyncRunner asyncRunner;
  private final GetExecutionPayloadBidRequest getExecutionPayloadBidRequest;
  private final SubmitBuilderPreferencesRequest submitBuilderPreferencesRequest;
  private final SubmitSignedBeaconBlockRequest submitSignedBeaconBlockRequest;

  OkHttpStakedBuilderClient(
      final AsyncRunner asyncRunner,
      final Spec spec,
      final HttpUrl baseEndpoint,
      final OkHttpClient httpClient) {
    this.asyncRunner = asyncRunner;
    this.getExecutionPayloadBidRequest =
        new GetExecutionPayloadBidRequest(spec, baseEndpoint, httpClient);
    this.submitBuilderPreferencesRequest =
        new SubmitBuilderPreferencesRequest(spec, baseEndpoint, httpClient);
    this.submitSignedBeaconBlockRequest =
        new SubmitSignedBeaconBlockRequest(spec, baseEndpoint, httpClient);
  }

  @Override
  public SafeFuture<Optional<SignedExecutionPayloadBid>> getExecutionPayloadBid(
      final UInt64 slot,
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final BLSPublicKey proposerPubkey,
      final SignedBuilderRequestAuth auth) {
    return asyncRunner.runAsync(
        () ->
            getExecutionPayloadBidRequest.submit(
                slot, parentHash, parentRoot, proposerPubkey, auth));
  }

  @Override
  public SafeFuture<Void> submitBuilderPreferences(
      final BLSPublicKey validatorPubkey,
      final BuilderPreferencesRequest builderPreferencesRequest) {
    return asyncRunner.runAsync(
        () -> submitBuilderPreferencesRequest.submit(validatorPubkey, builderPreferencesRequest));
  }

  @Override
  public SafeFuture<Void> submitSignedBeaconBlock(final SignedBeaconBlock signedBeaconBlock) {
    return asyncRunner.runAsync(() -> submitSignedBeaconBlockRequest.submit(signedBeaconBlock));
  }
}
