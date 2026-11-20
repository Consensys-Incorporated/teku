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

package tech.pegasys.teku.spec.signatures.xmss;

import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_DEPOSIT_PREFIX;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.xmss.XmssPublicKey;

/**
 * The public values a PQ validator registers: its leanXMSS public key and its VRF root, and the
 * forms they take in an EIP-8483 registration and in a post-fork deposit.
 */
public record PqValidatorPublicData(XmssPublicKey xmssPublicKey, Bytes32 vrfRoot) {

  /** The size of a deposit's signature field, which a post-fork deposit fills with its VRF root. */
  public static final int DEPOSIT_SIGNATURE_SIZE = 96;

  /** EIP-8483 {@code pq_data} for a validator: {@code xmss_pubkey ‖ vrf_root}. */
  public Bytes getPqData() {
    return Bytes.concatenate(xmssPublicKey.toBytes(), vrfRoot);
  }

  /**
   * A post-fork deposit's {@code pubkey} (PQ validator.md, "Submit deposit"): {@code
   * XMSS_DEPOSIT_PREFIX ‖ xmss_pubkey}.
   */
  public Bytes48 getDepositPubkey() {
    return Bytes48.wrap(Bytes.concatenate(XMSS_DEPOSIT_PREFIX, xmssPublicKey.toBytes()));
  }

  /**
   * A post-fork deposit's {@code signature} (PQ validator.md, "Submit deposit"): the VRF root
   * followed by 64 zero bytes. A deposit carries no signature after the fork.
   */
  public Bytes getDepositSignature() {
    return Bytes.concatenate(vrfRoot, Bytes.wrap(new byte[DEPOSIT_SIGNATURE_SIZE - Bytes32.SIZE]));
  }
}
