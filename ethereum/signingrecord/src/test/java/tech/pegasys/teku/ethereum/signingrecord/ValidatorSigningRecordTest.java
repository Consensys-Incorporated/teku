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

package tech.pegasys.teku.ethereum.signingrecord;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ValidatorSigningRecordTest {
  private static final Bytes32 GENESIS_VALIDATORS_ROOT = Bytes32.fromHexString("0x1234");

  @Test
  void shouldReadSigningRecordWithoutGenesisRoot() throws IOException {
    final String yamlData =
        Resources.toString(Resources.getResource("signingrecord-withoutGenesis.yml"), UTF_8);
    Bytes yamlByteData = Bytes.wrap(yamlData.getBytes(UTF_8));

    ValidatorSigningRecord record = ValidatorSigningRecord.fromBytes(yamlByteData);
    assertThat(record)
        .isEqualTo(
            new ValidatorSigningRecord(
                Optional.empty(), UInt64.valueOf(11), UInt64.valueOf(12), UInt64.valueOf(13)));
  }

  @Test
  void shouldReadSigningRecordWitOldNeverSignedValue() throws IOException {
    final String yamlData =
        Resources.toString(Resources.getResource("signingrecord-oldNeverSigned.yml"), UTF_8);
    Bytes yamlByteData = Bytes.wrap(yamlData.getBytes(UTF_8));

    ValidatorSigningRecord record = ValidatorSigningRecord.fromBytes(yamlByteData);
    assertThat(record)
        .isEqualTo(new ValidatorSigningRecord(Optional.empty(), UInt64.valueOf(11), null, null));
  }

  @Test
  void shouldReadSigningRecordWithGenesisRoot() throws IOException {
    final String yamlData = Resources.toString(Resources.getResource("signingrecord.yml"), UTF_8);
    Bytes yamlByteData = Bytes.wrap(yamlData.getBytes(UTF_8));
    ValidatorSigningRecord record = ValidatorSigningRecord.fromBytes(yamlByteData);
    assertThat(record)
        .isEqualTo(
            new ValidatorSigningRecord(
                Optional.of(GENESIS_VALIDATORS_ROOT),
                UInt64.valueOf(1),
                UInt64.valueOf(2),
                UInt64.valueOf(3)));
  }

  @Test
  void shouldSerializeToBytes() throws IOException {
    final String yamlData = Resources.toString(Resources.getResource("signingrecord.yml"), UTF_8);
    final Bytes yamlByteData = Bytes.of(yamlData.getBytes(UTF_8));
    ValidatorSigningRecord record =
        new ValidatorSigningRecord(
            Optional.of(GENESIS_VALIDATORS_ROOT),
            UInt64.valueOf(1),
            UInt64.valueOf(2),
            UInt64.valueOf(3));
    assertThat(record.toBytes()).isEqualTo(yamlByteData);
  }

  @Test
  void shouldRoundTripDefaultValuesToBytes() {
    final ValidatorSigningRecord record =
        ValidatorSigningRecord.emptySigningRecord(GENESIS_VALIDATORS_ROOT);
    final Bytes bytes = record.toBytes();
    final ValidatorSigningRecord result = ValidatorSigningRecord.fromBytes(bytes);
    assertThat(result).isEqualTo(record);
  }

  @Test
  void shouldRoundTripToBytes() {
    final ValidatorSigningRecord record =
        new ValidatorSigningRecord(
            Optional.of(GENESIS_VALIDATORS_ROOT),
            UInt64.valueOf(10),
            UInt64.valueOf(20),
            UInt64.valueOf(30));
    final Bytes bytes = record.toBytes();
    final ValidatorSigningRecord result = ValidatorSigningRecord.fromBytes(bytes);
    assertThat(result).isEqualTo(record);
  }

  @ParameterizedTest(name = "signBlock({0})")
  @MethodSource("blockCases")
  void signBlock(
      @SuppressWarnings("unused") final String name,
      final ValidatorSigningRecord input,
      final UInt64 slot,
      final Optional<ValidatorSigningRecord> expectedResult) {
    assertThat(input.maySignBlock(GENESIS_VALIDATORS_ROOT, slot)).isEqualTo(expectedResult);
  }

  static List<Arguments> blockCases() {
    final ValidatorSigningRecord startingRecord =
        new ValidatorSigningRecord(
            Optional.of(GENESIS_VALIDATORS_ROOT),
            UInt64.valueOf(3),
            UInt64.valueOf(6),
            UInt64.valueOf(7));
    return List.of(
        Arguments.of(
            "noExistingRecord",
            ValidatorSigningRecord.emptySigningRecord(GENESIS_VALIDATORS_ROOT),
            ONE,
            Optional.of(
                new ValidatorSigningRecord(
                    Optional.of(GENESIS_VALIDATORS_ROOT),
                    ONE,
                    ValidatorSigningRecord.NEVER_SIGNED,
                    ValidatorSigningRecord.NEVER_SIGNED))),
        Arguments.of("=", startingRecord, UInt64.valueOf(3), Optional.empty()),
        Arguments.of("<", startingRecord, UInt64.valueOf(2), Optional.empty()),
        Arguments.of(">", startingRecord, UInt64.valueOf(4), allowed(4, 6, 7)));
  }

  @ParameterizedTest(name = "maySignAttestation({0})")
  @MethodSource("attestationCases")
  void maySignAttestation(
      @SuppressWarnings("unused") final String name,
      final ValidatorSigningRecord input,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch,
      final Optional<ValidatorSigningRecord> expectedResult) {
    assertThat(input.maySignAttestation(GENESIS_VALIDATORS_ROOT, sourceEpoch, targetEpoch))
        .isEqualTo(expectedResult);
  }

  static List<Arguments> attestationCases() {
    final ValidatorSigningRecord startingRecord =
        new ValidatorSigningRecord(
            Optional.of(GENESIS_VALIDATORS_ROOT), ONE, UInt64.valueOf(4), UInt64.valueOf(6));
    return List.of(
        // No record
        attestationArguments(
            "NEVER_SIGNED",
            "NEVER_SIGNED",
            ValidatorSigningRecord.emptySigningRecord(GENESIS_VALIDATORS_ROOT),
            1,
            2,
            allowed(0, 1, 2)),
        attestationArguments("=", "=", startingRecord, 4, 6, disallowed()),
        attestationArguments("=", "<", startingRecord, 4, 5, disallowed()),
        attestationArguments("=", ">", startingRecord, 4, 7, allowed(1, 4, 7)),
        attestationArguments("<", "=", startingRecord, 3, 6, disallowed()),
        attestationArguments("<", "<", startingRecord, 3, 5, disallowed()),
        attestationArguments("<", ">", startingRecord, 3, 7, disallowed()),
        attestationArguments(">", "=", startingRecord, 5, 6, disallowed()),
        attestationArguments(">", "<", startingRecord, 5, 5, disallowed()),
        attestationArguments(">", ">", startingRecord, 5, 7, allowed(1, 5, 7)));
  }

  private static Optional<ValidatorSigningRecord> disallowed() {
    return Optional.empty();
  }

  private static Optional<ValidatorSigningRecord> allowed(
      final int blockSlot, final int sourceEpoch, final int targetEpoch) {
    return Optional.of(
        new ValidatorSigningRecord(
            Optional.of(GENESIS_VALIDATORS_ROOT),
            UInt64.valueOf(blockSlot),
            UInt64.valueOf(sourceEpoch),
            UInt64.valueOf(targetEpoch)));
  }

  private static Arguments attestationArguments(
      final String sourceEpochDescription,
      final String targetEpochDescription,
      final ValidatorSigningRecord lastSignedRecord,
      final int sourceEpoch,
      final int targetEpoch,
      final Optional<ValidatorSigningRecord> expectedResult) {
    return Arguments.of(
        "source " + sourceEpochDescription + ", target " + targetEpochDescription,
        lastSignedRecord,
        UInt64.valueOf(sourceEpoch),
        UInt64.valueOf(targetEpoch),
        expectedResult);
  }
}
