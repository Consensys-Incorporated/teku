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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesRequest;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;

/**
 * REST client for the Staked Builder API.
 *
 * @see <a href="https://github.com/ethereum/builder-specs/pull/138">builder-specs#138</a>
 */
public interface StakedBuilderClient {

  SafeFuture<Optional<SignedExecutionPayloadBid>> getExecutionPayloadBid(
      UInt64 slot,
      Bytes32 parentHash,
      Bytes32 parentRoot,
      BLSPublicKey proposerPubkey,
      SignedBuilderRequestAuth auth)
      throws BuilderClientException;

  SafeFuture<Void> submitBuilderPreferences(
      BLSPublicKey validatorPubkey, BuilderPreferencesRequest builderPreferencesRequest)
      throws BuilderClientException;

  SafeFuture<Void> submitSignedBeaconBlock(SignedBeaconBlock signedBeaconBlock)
      throws BuilderClientException;
}
