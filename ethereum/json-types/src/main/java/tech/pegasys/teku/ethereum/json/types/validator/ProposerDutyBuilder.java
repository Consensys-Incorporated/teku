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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerDutyBuilder {
  public static final DeserializableTypeDefinition<ProposerDuty> PROPOSER_DUTY_TYPE =
      DeserializableTypeDefinition.object(ProposerDuty.class, ProposerDutyBuilder.class)
          .initializer(ProposerDutyBuilder::new)
          .finisher(ProposerDutyBuilder::build)
          .withField(
              "pubkey", PUBLIC_KEY_TYPE, ProposerDuty::getPublicKey, ProposerDutyBuilder::publicKey)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              ProposerDuty::getValidatorIndex,
              ProposerDutyBuilder::validatorIndex)
          .withField("slot", UINT64_TYPE, ProposerDuty::getSlot, ProposerDutyBuilder::slot)
          .build();

  private BLSPublicKey publicKey;
  private int validatorIndex;
  private UInt64 slot;

  public ProposerDutyBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public ProposerDutyBuilder validatorIndex(final int validatorIndex) {
    this.validatorIndex = validatorIndex;
    return this;
  }

  public ProposerDutyBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  public ProposerDuty build() {
    return new ProposerDuty(publicKey, validatorIndex, slot);
  }
}
