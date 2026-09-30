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

package tech.pegasys.teku.ethereum.json.types.validator;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record InclusionListDuty(BLSPublicKey publicKey, UInt64 validatorIndex, UInt64 slot) {

  public static final DeserializableTypeDefinition<InclusionListDuty>
      INCLUSION_LIST_DUTY_TYPE_DEFINITION =
          DeserializableTypeDefinition.object(
                  InclusionListDuty.class, InclusionListDuty.Builder.class)
              .name("InclusionListDuty")
              .initializer(InclusionListDuty.Builder::new)
              .finisher(InclusionListDuty.Builder::build)
              .withField(
                  "pubkey",
                  PUBLIC_KEY_TYPE,
                  InclusionListDuty::publicKey,
                  InclusionListDuty.Builder::publicKey)
              .withField(
                  "validator_index",
                  UINT64_TYPE,
                  InclusionListDuty::validatorIndex,
                  InclusionListDuty.Builder::validatorIndex)
              .withField(
                  "slot", UINT64_TYPE, InclusionListDuty::slot, InclusionListDuty.Builder::slot)
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

    public InclusionListDuty build() {
      return new InclusionListDuty(publicKey, validatorIndex, slot);
    }
  }
}
