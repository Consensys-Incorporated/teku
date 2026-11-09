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

record Block(BLSPublicKey pubkey, UInt64 slot, Bytes32 signingRoot, boolean shouldSucceed) {

  static DeserializableTypeDefinition<Block> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(Block.class, BlockBuilder.class)
        .initializer(BlockBuilder::new)
        .finisher(BlockBuilder::build)
        .withField("slot", UINT64_TYPE, Block::slot, BlockBuilder::slot)
        .withField("pubkey", SigningHistory.PUBKEY_TYPE, Block::pubkey, BlockBuilder::pubkey)
        .withField("signing_root", BYTES32_TYPE, Block::signingRoot, BlockBuilder::signingRoot)
        .withField(
            "should_succeed", BOOLEAN_TYPE, Block::shouldSucceed, BlockBuilder::shouldSucceed)
        .build();
  }

  static class BlockBuilder {
    BLSPublicKey pubkey;
    UInt64 slot;
    Bytes32 signingRoot;
    boolean shouldSucceed;

    BlockBuilder pubkey(final BLSPublicKey pubkey) {
      this.pubkey = pubkey;
      return this;
    }

    BlockBuilder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    BlockBuilder signingRoot(final Bytes32 signingRoot) {
      this.signingRoot = signingRoot;
      return this;
    }

    BlockBuilder shouldSucceed(final boolean shouldSucceed) {
      this.shouldSucceed = shouldSucceed;
      return this;
    }

    public Block build() {
      return new Block(pubkey, slot, signingRoot, shouldSucceed);
    }
  }
}
