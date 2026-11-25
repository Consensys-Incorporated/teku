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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("historiesWithMaxValues")
  void shouldRejectMaxValueWhenConvertingToValidatorSigningRecord(
      final String description,
      final List<SignedBlock> signedBlocks,
      final List<SignedAttestation> signedAttestations,
      final String expectedError) {
    // A stored UInt64.MAX_VALUE reads back as "never signed", so it must not be imported - see
    // https://github.com/Consensys-Incorporated/teku-internal/issues/383
    final SigningHistory signingHistory =
        new SigningHistory(blsPubKey, signedBlocks, signedAttestations);

    assertThat(signingHistory.findUnsupportedValue()).contains(expectedError);
    assertThatThrownBy(
            () ->
                signingHistory.toValidatorSigningRecord(
                    Optional.of(
                        new ValidatorSigningRecord(
                            Optional.of(GENESIS_ROOT), ONE, ONE, UInt64.valueOf(2))),
                    GENESIS_ROOT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedError);
  }

  static Stream<Arguments> historiesWithMaxValues() {
    final String attestationError =
        "signed attestation epoch 18446744073709551615 is not supported";
    return Stream.of(
        Arguments.of(
            "max source epoch",
            List.of(),
            List.of(attestation(UInt64.MAX_VALUE, UInt64.valueOf(200))),
            attestationError),
        Arguments.of(
            "max target epoch",
            List.of(),
            List.of(attestation(ZERO, UInt64.MAX_VALUE)),
            attestationError),
        Arguments.of(
            "max epoch alongside a finite attestation",
            List.of(),
            List.of(attestation(ONE, UInt64.valueOf(2)), attestation(ZERO, UInt64.MAX_VALUE)),
            attestationError),
        Arguments.of(
            "max block slot",
            List.of(new SignedBlock(UInt64.MAX_VALUE, Optional.empty())),
            List.of(),
            "signed block slot 18446744073709551615 is not supported"));
  }

  @Test
  void shouldConvertFiniteValuesToValidatorSigningRecord() {
    final SigningHistory signingHistory =
        new SigningHistory(
            blsPubKey,
            List.of(new SignedBlock(UInt64.valueOf(12), Optional.empty())),
            List.of(
                attestation(ONE, UInt64.valueOf(3)),
                attestation(UInt64.valueOf(2), UInt64.valueOf(2))));

    assertThat(signingHistory.findImportError()).isEmpty();
    assertThat(signingHistory.toValidatorSigningRecord(Optional.empty(), GENESIS_ROOT))
        .isEqualTo(
            new ValidatorSigningRecord(
                Optional.of(GENESIS_ROOT),
                UInt64.valueOf(12),
                UInt64.valueOf(2),
                UInt64.valueOf(3)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("historiesWithSourceAfterTarget")
  void shouldRejectAttestationWithSourceAfterTarget(
      final String description,
      final List<SignedAttestation> signedAttestations,
      final String expectedError) {
    // Importing a source epoch above the target sets a floor no honest attestation can pass - see
    // https://github.com/Consensys-Incorporated/teku/issues/11606
    final SigningHistory signingHistory =
        new SigningHistory(blsPubKey, List.of(), signedAttestations);

    assertThat(signingHistory.findAttestationWithSourceAfterTarget()).contains(expectedError);
    assertThat(signingHistory.findImportError()).contains(expectedError);
  }

  static Stream<Arguments> historiesWithSourceAfterTarget() {
    return Stream.of(
        Arguments.of(
            "source one after target",
            List.of(attestation(UInt64.valueOf(8), UInt64.valueOf(7))),
            "signed attestation source epoch 8 is after its target epoch 7"),
        Arguments.of(
            "far future source",
            List.of(attestation(UInt64.valueOf(999999999), ONE)),
            "signed attestation source epoch 999999999 is after its target epoch 1"),
        Arguments.of(
            "invalid attestation hidden by a later valid one",
            List.of(
                attestation(UInt64.valueOf(8), UInt64.valueOf(7)),
                attestation(UInt64.valueOf(9), UInt64.valueOf(10))),
            "signed attestation source epoch 8 is after its target epoch 7"));
  }

  @Test
  void shouldAcceptAttestationWithSourceEqualToTarget() {
    final SigningHistory signingHistory =
        new SigningHistory(blsPubKey, List.of(), List.of(attestation(ZERO, ZERO)));

    assertThat(signingHistory.findImportError()).isEmpty();
  }

  @Test
  void shouldReportMaxValueBeforeSourceAfterTarget() {
    final SigningHistory signingHistory =
        new SigningHistory(
            blsPubKey,
            List.of(new SignedBlock(UInt64.MAX_VALUE, Optional.empty())),
            List.of(attestation(UInt64.valueOf(8), UInt64.valueOf(7))));

    assertThat(signingHistory.findImportError())
        .contains("signed block slot 18446744073709551615 is not supported");
  }

  private static SignedAttestation attestation(final UInt64 source, final UInt64 target) {
    return new SignedAttestation(source, target, Optional.empty());
  }
}
