/*
 * Copyright Consensys Software Inc., 2025
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

public class InclusionListDutyBuilder {
  public static final DeserializableTypeDefinition<InclusionListDuty> INCLUSION_LIST_DUTY_TYPE =
      DeserializableTypeDefinition.object(InclusionListDuty.class, InclusionListDutyBuilder.class)
          .initializer(InclusionListDutyBuilder::new)
          .finisher(InclusionListDutyBuilder::build)
          .withField(
              "pubkey",
              PUBLIC_KEY_TYPE,
              InclusionListDuty::publicKey,
              InclusionListDutyBuilder::publicKey)
          .withField(
              "validator_index",
              UINT64_TYPE.withDescription("Index of validator in validator registry."),
              InclusionListDuty::validatorIndex,
              InclusionListDutyBuilder::validatorIndex)
          .withField(
              "slot",
              UINT64_TYPE.withDescription(
                  "The slot at which the validator must propose an inclusion list."),
              InclusionListDuty::slot,
              InclusionListDutyBuilder::slot)
          .build();

  private BLSPublicKey publicKey;
  private UInt64 validatorIndex;
  private UInt64 slot;

  public InclusionListDutyBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public InclusionListDutyBuilder validatorIndex(final UInt64 validatorIndex) {
    this.validatorIndex = validatorIndex;
    return this;
  }

  public InclusionListDutyBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  public InclusionListDuty build() {
    return new InclusionListDuty(publicKey, validatorIndex, slot);
  }
}
