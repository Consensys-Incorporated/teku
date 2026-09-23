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

public class SigningHistoryTest {
  private static final Bytes32 GENESIS_ROOT =
      Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000123456");
  private final BLSPublicKey blsPubKey =
      BLSPublicKey.fromHexString(
          "0xb845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed");

  @Test
  public void shouldReadMetadataFromCompleteJson() throws IOException {
    final String minimalJson =
        Resources.toString(Resources.getResource("format1_complete.json"), StandardCharsets.UTF_8);

    final SlashingProtectionInterchangeFormat parsed =
        JsonUtil.parse(minimalJson, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());

    assertThat(parsed.metadata())
        .isEqualTo(new Metadata(Optional.empty(), INTERCHANGE_VERSION, Optional.of(GENESIS_ROOT)));

    assertThat(parsed.data())
        .containsExactly(
            new SigningHistory(
                blsPubKey,
                List.of(
                    new SignedBlock(
                        UInt64.valueOf(81952),
                        Optional.of(
                            Bytes32.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000001234")))),
                List.of(
                    new SignedAttestation(
                        UInt64.valueOf(2290),
                        UInt64.valueOf(3007),
                        Optional.of(
                            Bytes32.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000123"))))));
  }

  @Test
  public void shouldIgnoreMaxValueAttestationEpochsWhenConvertingToValidatorSigningRecord() {
    // An imported UInt64.MAX_VALUE epoch is indistinguishable from "never signed" once
    // persisted and reloaded - see
    // https://github.com/Consensys-Incorporated/teku-internal/issues/334
    final SigningHistory signingHistory =
        new SigningHistory(
            blsPubKey,
            List.of(),
            List.of(new SignedAttestation(UInt64.MAX_VALUE, UInt64.MAX_VALUE, Optional.empty())));

    final ValidatorSigningRecord record =
        signingHistory.toValidatorSigningRecord(Optional.empty(), GENESIS_ROOT);

    // No legitimate epoch was imported, so the floor stays unset rather than becoming MAX_VALUE.
    assertThat(record.attestationSourceEpoch()).isNull();
    assertThat(record.attestationTargetEpoch()).isNull();
  }
}
