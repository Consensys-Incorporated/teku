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

public record SignedBlock(UInt64 slot, Optional<Bytes32> signingRoot) {

  public static DeserializableTypeDefinition<SignedBlock> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(SignedBlock.class, SignedBlockBuilder.class)
        .initializer(SignedBlockBuilder::new)
        .finisher(SignedBlockBuilder::build)
        .withField("slot", UINT64_TYPE, SignedBlock::slot, SignedBlockBuilder::slot)
        .withOptionalField(
            "signing_root", BYTES32_TYPE, SignedBlock::signingRoot, SignedBlockBuilder::signingRoot)
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("signingRoot", signingRoot)
        .toString();
  }

  static class SignedBlockBuilder {
    UInt64 slot;
    Optional<Bytes32> signingRoot = Optional.empty();

    SignedBlockBuilder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    SignedBlockBuilder signingRoot(final Optional<Bytes32> signingRoot) {
      this.signingRoot = signingRoot;
      return this;
    }

    SignedBlock build() {
      return new SignedBlock(slot, signingRoot);
    }
  }
}
