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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.xmss.XmssPublicKey;

class PqValidatorPublicDataTest {

  private static final XmssPublicKey XMSS_PUBLIC_KEY =
      XmssPublicKey.fromHexString(
          "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
  private static final Bytes32 VRF_ROOT =
      Bytes32.fromHexString("0xa1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0");

  private final PredicatesPq predicates =
      (PredicatesPq) TestSpecFactory.createMinimalPq().getGenesisSpec().predicates();
  private final PqValidatorPublicData publicData =
      new PqValidatorPublicData(XMSS_PUBLIC_KEY, VRF_ROOT);

  @Test
  void pqData_isXmssPubkeyThenVrfRoot() {
    assertThat(publicData.getPqData())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"
                    + "a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0"));
  }

  @Test
  void depositPubkey_isPrefixedXmssPubkey() {
    assertThat(publicData.getDepositPubkey())
        .isEqualTo(
            Bytes.fromHexString(
                "0x00000000000000000000000000000000"
                    + "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"));
    // The state transition reads it as a post-fork deposit, not a legacy one
    assertThat(predicates.isLegacyPubkey(publicData.getDepositPubkey())).isFalse();
  }

  @Test
  void depositSignature_isVrfRootThen64ZeroBytes() {
    final Bytes signature = publicData.getDepositSignature();

    assertThat(signature.size()).isEqualTo(96);
    assertThat(signature.slice(0, 32)).isEqualTo(VRF_ROOT);
    assertThat(signature.slice(32)).isEqualTo(Bytes.wrap(new byte[64]));
  }
}
