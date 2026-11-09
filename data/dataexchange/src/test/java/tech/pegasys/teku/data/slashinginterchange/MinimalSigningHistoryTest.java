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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MinimalSigningHistoryTest {
  private static final Optional<Bytes32> GENESIS_ROOT =
      Optional.of(
          Bytes32.fromHexString(
              "0x0000000000000000000000000000000000000000000000000000000000123456"));
  private final BLSPublicKey blsPubKey =
      BLSPublicKey.fromHexString(
          "0xb845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed");

  @Test
  public void shouldReadMetadataFromMinimalJson() throws IOException {
    final String minimalJson =
        Resources.toString(Resources.getResource("format2_minimal.json"), StandardCharsets.UTF_8);

    final SlashingProtectionInterchangeFormat parsed =
        JsonUtil.parse(minimalJson, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());

    assertThat(parsed.metadata())
        .isEqualTo(new Metadata(Optional.empty(), INTERCHANGE_VERSION, GENESIS_ROOT));

    final List<SigningHistory> minimalSigningHistoryList = parsed.data();

    final SigningHistory element =
        SigningHistory.createSigningHistory(
            blsPubKey,
            new ValidatorSigningRecord(
                GENESIS_ROOT, UInt64.valueOf(81952), UInt64.valueOf(2290), UInt64.valueOf(3007)));
    assertThat(minimalSigningHistoryList).containsExactly(element);
  }
}
