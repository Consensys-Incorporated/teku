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

package tech.pegasys.teku.bls.keystore.pq;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PqKeyStoreSecretsTest {

  // BLS secret keys at m/12381/3600/i/0/0 (EIP-2333) for the mnemonic "giant issue aisle success
  // illegal bike spike question tent bar rely arctic volcano long crawl hungry vocal artwork sniff
  // fantasy very lucky have athlete", with sha256("leanxmss-seed" ‖ sk) and sha256("vrf-seed" ‖ sk)
  // as ethpandaops/eth-beacon-genesis (pq branch) derives them. Its VRF test pins validator 0's
  // seed.
  @ParameterizedTest(name = "validator {0}")
  @CsvSource({
    "0, 0x0dce41fa73ae9f6bdfd51df4d422d75eee174553dba5fd450c4437e4ed3fc903,"
        + " 0x6d78e7072bfcbde1a75261f6e16772ea352de90462819cdc53e05f10d4700ad1,"
        + " 0x543fc5a4d86e79a434e9741770f7ee17c25bdc31be8d61436d7f9836538b03df",
    "63, 0x4e68934eb46cc31a0d4f36a973b2d8d691c5b39331fa832c9e161df3f92c96a4,"
        + " 0xca69591a53efcce207970c4ad25be8789efe1ec4d924c4120ea43585124c0e11,"
        + " 0xa800b132eeca5586fbd4082bb89b166e9b5d1f652a04627e40c923f7e1cd45eb"
  })
  void deriveFromBlsSecretKey_matchesDevnetGenesisTool(
      final int validatorIndex,
      final String blsSecretKey,
      final String expectedXmssSeed,
      final String expectedVrfSeed) {
    final PqKeyStoreSecrets secrets =
        PqKeyStoreSecrets.deriveFromBlsSecretKey(Bytes32.fromHexString(blsSecretKey));

    assertThat(secrets.xmssSeed()).isEqualTo(Bytes32.fromHexString(expectedXmssSeed));
    assertThat(secrets.vrfSeed()).isEqualTo(Bytes32.fromHexString(expectedVrfSeed));
  }
}
