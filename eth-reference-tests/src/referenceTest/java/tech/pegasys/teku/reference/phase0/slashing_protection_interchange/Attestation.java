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

package tech.pegasys.teku.reference.phase0.slashing_protection_interchange;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

record Attestation(
    BLSPublicKey pubkey,
    UInt64 sourceEpoch,
    UInt64 targetEpoch,
    Bytes32 signingRoot,
    boolean shouldSucceed) {

  static DeserializableTypeDefinition<Attestation> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(Attestation.class, AttestationBuilder.class)
        .initializer(AttestationBuilder::new)
        .finisher(AttestationBuilder::build)
        .withField(
            "pubkey", SigningHistory.PUBKEY_TYPE, Attestation::pubkey, AttestationBuilder::pubkey)
        .withField(
            "source_epoch", UINT64_TYPE, Attestation::sourceEpoch, AttestationBuilder::sourceEpoch)
        .withField(
            "target_epoch", UINT64_TYPE, Attestation::targetEpoch, AttestationBuilder::targetEpoch)
        .withField(
            "signing_root", BYTES32_TYPE, Attestation::signingRoot, AttestationBuilder::signingRoot)
        .withField(
            "should_succeed",
            BOOLEAN_TYPE,
            Attestation::shouldSucceed,
            AttestationBuilder::shouldSucceed)
        .build();
  }

  private static class AttestationBuilder {
    BLSPublicKey pubkey;
    UInt64 sourceEpoch;
    UInt64 targetEpoch;
    Bytes32 signingRoot;
    boolean shouldSucceed;

    AttestationBuilder pubkey(final BLSPublicKey pubkey) {
      this.pubkey = pubkey;
      return this;
    }

    AttestationBuilder sourceEpoch(final UInt64 sourceEpoch) {
      this.sourceEpoch = sourceEpoch;
      return this;
    }

    AttestationBuilder targetEpoch(final UInt64 targetEpoch) {
      this.targetEpoch = targetEpoch;
      return this;
    }

    AttestationBuilder signingRoot(final Bytes32 signingRoot) {
      this.signingRoot = signingRoot;
      return this;
    }

    AttestationBuilder shouldSucceed(final boolean shouldSucceed) {
      this.shouldSucceed = shouldSucceed;
      return this;
    }

    Attestation build() {
      return new Attestation(pubkey, sourceEpoch, targetEpoch, signingRoot, shouldSucceed);
    }
  }
}
