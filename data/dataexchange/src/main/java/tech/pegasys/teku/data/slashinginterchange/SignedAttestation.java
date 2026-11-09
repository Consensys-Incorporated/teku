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

package tech.pegasys.teku.data.slashinginterchange;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record SignedAttestation(
    UInt64 sourceEpoch, UInt64 targetEpoch, Optional<Bytes32> signingRoot) {

  public static DeserializableTypeDefinition<SignedAttestation> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(
            SignedAttestation.class, SignedAttestationBuilder.class)
        .initializer(SignedAttestationBuilder::new)
        .finisher(SignedAttestationBuilder::build)
        .withField(
            "source_epoch",
            UINT64_TYPE,
            SignedAttestation::sourceEpoch,
            SignedAttestationBuilder::sourceEpoch)
        .withField(
            "target_epoch",
            UINT64_TYPE,
            SignedAttestation::targetEpoch,
            SignedAttestationBuilder::targetEpoch)
        .withOptionalField(
            "signing_root",
            BYTES32_TYPE,
            SignedAttestation::signingRoot,
            SignedAttestationBuilder::signingRoot)
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("sourceEpoch", sourceEpoch)
        .add("targetEpoch", targetEpoch)
        .add("signingRoot", signingRoot)
        .toString();
  }

  static class SignedAttestationBuilder {
    UInt64 sourceEpoch;
    UInt64 targetEpoch;
    Optional<Bytes32> signingRoot = Optional.empty();

    SignedAttestationBuilder sourceEpoch(final UInt64 sourceEpoch) {
      this.sourceEpoch = sourceEpoch;
      return this;
    }

    SignedAttestationBuilder targetEpoch(final UInt64 targetEpoch) {
      this.targetEpoch = targetEpoch;
      return this;
    }

    SignedAttestationBuilder signingRoot(final Optional<Bytes32> signingRoot) {
      this.signingRoot = signingRoot;
      return this;
    }

    SignedAttestation build() {
      return new SignedAttestation(sourceEpoch, targetEpoch, signingRoot);
    }
  }
}
