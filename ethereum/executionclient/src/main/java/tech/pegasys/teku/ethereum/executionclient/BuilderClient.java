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

package tech.pegasys.teku.ethereum.executionclient;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;

public interface BuilderClient {

  SafeFuture<Response<Void>> status();

  SafeFuture<Response<Void>> registerValidators(
      UInt64 slot, SszList<SignedValidatorRegistration> signedValidatorRegistrations);

  SafeFuture<Response<Optional<SignedBuilderBid>>> getHeader(
      UInt64 slot, BLSPublicKey pubKey, Bytes32 parentHash);

  SafeFuture<Response<BuilderPayload>> getPayload(SignedBeaconBlock signedBlindedBeaconBlock);

  SafeFuture<Response<Void>> getPayloadV2(SignedBeaconBlock signedBlindedBeaconBlock);
}
