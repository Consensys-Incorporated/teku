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

package tech.pegasys.teku.ethereum.json.types.validator;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record PtcDuty(BLSPublicKey publicKey, UInt64 validatorIndex, UInt64 slot) {

  public static final DeserializableTypeDefinition<PtcDuty> PTC_DUTY_TYPE_DEFINITION =
      DeserializableTypeDefinition.object(PtcDuty.class, PtcDuty.Builder.class)
          .name("PtcDuty")
          .initializer(PtcDuty.Builder::new)
          .finisher(PtcDuty.Builder::build)
          .withField("pubkey", PUBLIC_KEY_TYPE, PtcDuty::publicKey, PtcDuty.Builder::publicKey)
          .withField(
              "validator_index",
              UINT64_TYPE,
              PtcDuty::validatorIndex,
              PtcDuty.Builder::validatorIndex)
          .withField("slot", UINT64_TYPE, PtcDuty::slot, PtcDuty.Builder::slot)
          .build();

  public static class Builder {

    private BLSPublicKey publicKey;
    private UInt64 validatorIndex;
    private UInt64 slot;

    public Builder publicKey(final BLSPublicKey publicKey) {
      this.publicKey = publicKey;
      return this;
    }

    public Builder validatorIndex(final UInt64 validatorIndex) {
      this.validatorIndex = validatorIndex;
      return this;
    }

    public Builder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    public PtcDuty build() {
      return new PtcDuty(publicKey, validatorIndex, slot);
    }
  }
}
