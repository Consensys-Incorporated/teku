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

import static tech.pegasys.teku.ethereum.json.types.beacon.ValidatorTypeDefinition.VALIDATOR_PUBKEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A payload timeliness committee duty. {@code pubkey} is the validator's signing key: a BLS key, or
 * from PQ its 32-byte XMSS key.
 */
public record PtcDuty(Bytes pubkey, UInt64 validatorIndex, UInt64 slot) {

  public PtcDuty(final BLSPublicKey publicKey, final UInt64 validatorIndex, final UInt64 slot) {
    this(publicKey.toBytesCompressed(), validatorIndex, slot);
  }

  /**
   * @throws IllegalArgumentException for a PQ validator, which is named by its XMSS key
   */
  public BLSPublicKey publicKey() {
    return BLSPublicKey.fromBytesCompressed(Bytes48.wrap(pubkey));
  }

  public static final DeserializableTypeDefinition<PtcDuty> PTC_DUTY_TYPE_DEFINITION =
      DeserializableTypeDefinition.object(PtcDuty.class, PtcDuty.Builder.class)
          .name("PtcDuty")
          .initializer(PtcDuty.Builder::new)
          .finisher(PtcDuty.Builder::build)
          .withField("pubkey", VALIDATOR_PUBKEY_TYPE, PtcDuty::pubkey, PtcDuty.Builder::publicKey)
          .withField(
              "validator_index",
              UINT64_TYPE,
              PtcDuty::validatorIndex,
              PtcDuty.Builder::validatorIndex)
          .withField("slot", UINT64_TYPE, PtcDuty::slot, PtcDuty.Builder::slot)
          .build();

  public static class Builder {

    private Bytes publicKey;
    private UInt64 validatorIndex;
    private UInt64 slot;

    public Builder publicKey(final Bytes publicKey) {
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
