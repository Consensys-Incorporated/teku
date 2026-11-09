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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat.AUTO;
import static tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat.CLIENT_CODES;
import static tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat.DISABLED;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

public class GraffitiBuilderTest {
  private ClientGraffitiAppendFormat clientGraffitiAppendFormat = AUTO;
  private GraffitiBuilder graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat);

  private static final ClientVersion TEKU_CLIENT_VERSION =
      new GraffitiBuilder(DISABLED).getConsensusClientVersion();
  private static final ClientVersion BESU_CLIENT_VERSION =
      new ClientVersion("BU", "Besu", "23.4.1", Bytes4.fromHexString("abcdef12"));
  private static final String V_2BYTES =
      TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4);
  private static final String V_1BYTE =
      TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2);

  private final String asciiGraffiti0 = "";
  private static final String ASCII_GRAFFITI_20 = "I've proposed ablock";
  private static final String ASCII_GRAFFITI_27 = "27 bytes of user's graffiti";
  private static final String ASCII_GRAFFITI_28 = "28 bytes of user's graffiti!";
  private static final String ASCII_GRAFFITI_30 = "30 bytes of a user's graffiti!";
  private final String asciiGraffiti32 = "I've proposed a good Teku block!";

  private static final String UTF_8_GRAFFITI_4 = "\uD83D\uDE80";
  private final String utf8Graffiti20 = "\uD83D\uDE80 !My block! \uD83D\uDE80";

  @BeforeEach
  public void setup() {
    this.clientGraffitiAppendFormat = AUTO;
    this.graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat);
  }

  @Test
  public void onExecutionClientVersion_shouldLogGraffitiWatermark() {
    this.graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat);
    try (final LogCaptor logCaptor = LogCaptor.forClass(EventLogger.class)) {
      graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);
      logCaptor.assertInfoLog(
          "Using graffiti watermark: \"BUabcdTK"
              + V_2BYTES
              + "\". "
              + "This will be appended to any user-defined graffiti or used if none is defined. Refer to validator graffiti options to customize.");
    }
  }

  @Test
  public void onExecutionClientVersionNotAvailable_shouldLogGraffitiWatermark() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(EventLogger.class)) {
      graffitiBuilder.onExecutionClientVersionNotAvailable();
      logCaptor.assertInfoLog(
          "Using graffiti watermark: \"TK"
              + V_2BYTES
              + "\". This will be appended to any user-defined graffiti or used if none is defined. Refer to validator graffiti options to customize.");
    }
  }

  @Test
  public void buildGraffiti_shouldNotFail() {
    this.graffitiBuilder =
        new GraffitiBuilder(clientGraffitiAppendFormat) {
          @Override
          protected int calculateGraffitiLength(final Optional<Bytes32> graffiti) {
            throw new RuntimeException("");
          }
        };
    assertThat(graffitiBuilder.buildGraffiti(Optional.empty())).isEqualTo(Bytes32.ZERO);
    final Bytes32 graffiti = Bytes32Parser.toBytes32(ASCII_GRAFFITI_20);
    assertThat(graffitiBuilder.buildGraffiti(Optional.of(graffiti))).isEqualTo(graffiti);
  }

  @Test
  public void buildGraffiti_shouldPreferCallInput() {
    final Bytes32 userGraffiti = Bytes32Parser.toBytes32(ASCII_GRAFFITI_20);
    final Bytes32 expectedGraffiti = Bytes32Parser.toBytes32(ASCII_GRAFFITI_20 + " TK");
    this.graffitiBuilder = new GraffitiBuilder(CLIENT_CODES);
    assertThat(graffitiBuilder.buildGraffiti(Optional.of(userGraffiti)))
        .isEqualTo(expectedGraffiti);
  }

  @ParameterizedTest(name = "format={0}, userGraffiti={1}")
  @MethodSource("getBuildGraffitiFixtures")
  public void buildGraffiti_shouldProvideCorrectOutput(
      final ClientGraffitiAppendFormat clientGraffitiAppendFormat,
      final Optional<String> maybeUserGraffiti,
      final String expectedGraffiti) {
    this.graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat);
    graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);
    final Bytes32 expectedGraffitiBytes = Bytes32Parser.toBytes32(expectedGraffiti);
    assertThat(
            new String(
                Arrays.copyOfRange(
                    expectedGraffitiBytes.toArray(),
                    0,
                    32 - expectedGraffitiBytes.numberOfTrailingZeroBytes()),
                StandardCharsets.UTF_8))
        .isEqualTo(expectedGraffiti);
    assertThat(graffitiBuilder.buildGraffiti(maybeUserGraffiti.map(Bytes32Parser::toBytes32)))
        .isEqualTo(expectedGraffitiBytes);
  }

  @ParameterizedTest(name = "format={0}, userGraffiti={1}")
  @MethodSource("getBuildGraffitiFixturesElInfoNa")
  public void buildGraffiti_shouldProvideCorrectOutput_whenElInfoNa(
      final ClientGraffitiAppendFormat clientGraffitiAppendFormat,
      final Optional<String> maybeUserGraffiti,
      final String expectedGraffiti) {
    this.graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat);
    final Bytes32 expectedGraffitiBytes = Bytes32Parser.toBytes32(expectedGraffiti);
    assertThat(
            new String(
                Arrays.copyOfRange(
                    expectedGraffitiBytes.toArray(),
                    0,
                    32 - expectedGraffitiBytes.numberOfTrailingZeroBytes()),
                StandardCharsets.UTF_8))
        .isEqualTo(expectedGraffiti);
    assertThat(graffitiBuilder.buildGraffiti(maybeUserGraffiti.map(Bytes32Parser::toBytes32)))
        .isEqualTo(expectedGraffitiBytes);
  }

  @Test
  public void extractGraffiti_shouldReturnEmptyString() {
    assertThat(graffitiBuilder.extractGraffiti(Optional.empty(), 0)).isEqualTo("");
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti0)), 0))
        .isEqualTo("");
  }

  @Test
  public void extractGraffiti_shouldReturnAsciiString() {
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(ASCII_GRAFFITI_20)), 20))
        .isEqualTo(ASCII_GRAFFITI_20);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti32)), 32))
        .isEqualTo(asciiGraffiti32);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti32)), 16))
        .isEqualTo(asciiGraffiti32.substring(0, 16));
  }

  @Test
  public void extractGraffiti_shouldReturnUtf8String() {
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(UTF_8_GRAFFITI_4)), 4))
        .isEqualTo(UTF_8_GRAFFITI_4);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(utf8Graffiti20)), 20))
        .isEqualTo(utf8Graffiti20);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(utf8Graffiti20)), 24))
        .isEqualTo(utf8Graffiti20 + "\u0000\u0000\u0000\u0000");
  }

  @Test
  public void calculateGraffitiLength_shouldHandleEmptyString() {
    assertThat(graffitiBuilder.calculateGraffitiLength(Optional.empty())).isEqualTo(0);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti0))))
        .isEqualTo(0);
  }

  @Test
  public void calculateGraffitiLength_shouldHandleAsciiString() {
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(ASCII_GRAFFITI_20))))
        .isEqualTo(20);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti32))))
        .isEqualTo(32);
  }

  @Test
  public void calculateGraffitiLength_shouldHandleUtf8String() {
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(UTF_8_GRAFFITI_4))))
        .isEqualTo(4);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(utf8Graffiti20))))
        .isEqualTo(20);
  }

  @Test
  public void joinNonEmpty_shouldJoin() {
    assertThat(graffitiBuilder.joinNonEmpty("", "aa", "bb", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("aabbcc"));
    assertThat(graffitiBuilder.joinNonEmpty(" ", "aa", "bb", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("aa bb cc"));
  }

  @Test
  public void joinNonEmpty_shouldJoinSkippingEmpty() {
    assertThat(graffitiBuilder.joinNonEmpty(" ", "aa", "", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("aa cc"));
    assertThat(graffitiBuilder.joinNonEmpty(" ", "", "bb", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("bb cc"));
    assertThat(graffitiBuilder.joinNonEmpty(" ", "", "", "")).isEqualTo(Bytes32.ZERO);
  }

  @ParameterizedTest
  @MethodSource("graffitiWatermarks")
  void graffitiWatermarksRunner(final int watermarkMaxLength, final String expectedWatermark) {
    graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);
    final String clientInfo = graffitiBuilder.formatClientsInfo(watermarkMaxLength);
    assertThat(clientInfo).isEqualTo(expectedWatermark);
  }

  @ParameterizedTest(name = "code={0}")
  @MethodSource("getClientCodes")
  public void formatClientInfo_shouldHandleBadCodeOnClientNamesAndFullCommit(
      final String code, final String expectedCode) {
    graffitiBuilder.onExecutionClientVersion(
        new ClientVersion(
            code,
            BESU_CLIENT_VERSION.name(),
            BESU_CLIENT_VERSION.version(),
            BESU_CLIENT_VERSION.commit()));

    // 12: BU0f91a6LH1be525
    assertThat(graffitiBuilder.formatClientsInfo(12))
        .isEqualTo(expectedCode + "abcdTK" + V_2BYTES)
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(12));
  }

  @ParameterizedTest(name = "code={0}")
  @MethodSource("getClientCodes")
  public void formatClientInfo_shouldHandleBadCodeOnClientNamesAndHalfCommit(
      final String code, final String expectedCode) {
    graffitiBuilder.onExecutionClientVersion(
        new ClientVersion(
            code,
            BESU_CLIENT_VERSION.name(),
            BESU_CLIENT_VERSION.version(),
            BESU_CLIENT_VERSION.commit()));

    // 12: LH1be5BU0f91
    assertThat(graffitiBuilder.formatClientsInfo(12))
        .isEqualTo(expectedCode + "abcdTK" + V_2BYTES)
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(12));
  }

  @ParameterizedTest(name = "code={0}")
  @MethodSource("getClientCodes")
  public void formatClientInfo_shouldHandleBadCodeOnClientNamesAnd1stCommitByte(
      final String code, final String expectedCode) {
    graffitiBuilder.onExecutionClientVersion(
        new ClientVersion(
            code,
            BESU_CLIENT_VERSION.name(),
            BESU_CLIENT_VERSION.version(),
            BESU_CLIENT_VERSION.commit()));

    // 8: LH1bBU0f
    assertThat(graffitiBuilder.formatClientsInfo(8))
        .isEqualTo(expectedCode + "abTK" + V_1BYTE)
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8));
  }

  @ParameterizedTest(name = "code={0}")
  @MethodSource("getClientCodes")
  public void formatClientInfo_shouldHandleBadCodeOnClientNames(
      final String code, final String expectedCode) {
    graffitiBuilder.onExecutionClientVersion(
        new ClientVersion(
            code,
            BESU_CLIENT_VERSION.name(),
            BESU_CLIENT_VERSION.version(),
            BESU_CLIENT_VERSION.commit()));

    // 4: LHBU
    assertThat(graffitiBuilder.formatClientsInfo(4))
        .isEqualTo(expectedCode + "TK")
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(4));
  }

  private static Stream<Arguments> getClientCodes() {
    return Stream.of(
        Arguments.of("bu", "BU"),
        Arguments.of("bU", "BU"),
        Arguments.of("bur", "BU"),
        Arguments.of("b", "NA"),
        Arguments.of("12", "NA"),
        Arguments.of("", "NA"),
        Arguments.of(null, "NA"));
  }

  private static Stream<Arguments> getBuildGraffitiFixtures() {
    return Stream.of(
        Arguments.of(AUTO, Optional.empty(), "BUabcdTK" + V_2BYTES),
        Arguments.of(AUTO, Optional.of("small"), "small " + "BUabcdTK" + V_2BYTES),
        Arguments.of(
            AUTO, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4 + " " + "BUabcdTK" + V_2BYTES),
        Arguments.of(
            AUTO, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20 + " " + "BUabTK" + V_1BYTE),
        Arguments.of(AUTO, Optional.of(ASCII_GRAFFITI_27), ASCII_GRAFFITI_27 + " " + "BUTK"),
        Arguments.of(AUTO, Optional.of(ASCII_GRAFFITI_28), ASCII_GRAFFITI_28 + "BUTK"),
        Arguments.of(CLIENT_CODES, Optional.empty(), "BUTK"),
        Arguments.of(CLIENT_CODES, Optional.of("small"), "small " + "BUTK"),
        Arguments.of(CLIENT_CODES, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4 + " " + "BUTK"),
        Arguments.of(
            CLIENT_CODES, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20 + " " + "BUTK"),
        Arguments.of(
            CLIENT_CODES, Optional.of(ASCII_GRAFFITI_27), ASCII_GRAFFITI_27 + " " + "BUTK"),
        Arguments.of(CLIENT_CODES, Optional.of(ASCII_GRAFFITI_28), ASCII_GRAFFITI_28 + "BUTK"),
        Arguments.of(CLIENT_CODES, Optional.of(ASCII_GRAFFITI_30), ASCII_GRAFFITI_30 + "BU"),
        Arguments.of(DISABLED, Optional.empty(), ""),
        Arguments.of(DISABLED, Optional.of("small"), "small"),
        Arguments.of(DISABLED, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4),
        Arguments.of(DISABLED, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20));
  }

  private static Stream<Arguments> getBuildGraffitiFixturesElInfoNa() {
    return Stream.of(
        Arguments.of(AUTO, Optional.empty(), "TK" + V_2BYTES),
        Arguments.of(AUTO, Optional.of("small"), "small " + "TK" + V_2BYTES),
        Arguments.of(AUTO, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4 + " " + "TK" + V_2BYTES),
        Arguments.of(
            AUTO, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20 + " " + "TK" + V_1BYTE),
        Arguments.of(AUTO, Optional.of(ASCII_GRAFFITI_27), ASCII_GRAFFITI_27 + " " + "TK"),
        Arguments.of(AUTO, Optional.of(ASCII_GRAFFITI_28), ASCII_GRAFFITI_28 + "TK"),
        Arguments.of(CLIENT_CODES, Optional.empty(), "TK"),
        Arguments.of(CLIENT_CODES, Optional.of("small"), "small " + "TK"),
        Arguments.of(CLIENT_CODES, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4 + " " + "TK"),
        Arguments.of(CLIENT_CODES, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20 + " " + "TK"),
        Arguments.of(CLIENT_CODES, Optional.of(ASCII_GRAFFITI_27), ASCII_GRAFFITI_27 + " " + "TK"),
        Arguments.of(CLIENT_CODES, Optional.of(ASCII_GRAFFITI_28), ASCII_GRAFFITI_28 + "TK"),
        Arguments.of(CLIENT_CODES, Optional.of(ASCII_GRAFFITI_30), ASCII_GRAFFITI_30 + "TK"),
        Arguments.of(DISABLED, Optional.empty(), ""),
        Arguments.of(DISABLED, Optional.of("small"), "small"),
        Arguments.of(DISABLED, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4),
        Arguments.of(DISABLED, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20));
  }

  private static Stream<Arguments> graffitiWatermarks() {
    // watermark_max_length, expected_result
    return Stream.of(
        Arguments.of(32, "BUabcdTK" + V_2BYTES),
        Arguments.of(12, "BUabcdTK" + V_2BYTES),
        Arguments.of(11, "BUabTK" + V_1BYTE),
        Arguments.of(8, "BUabTK" + V_1BYTE),
        Arguments.of(7, "BUTK"),
        Arguments.of(4, "BUTK"),
        Arguments.of(3, "BU"),
        Arguments.of(2, "BU"),
        Arguments.of(1, ""),
        Arguments.of(-1, ""));
  }
}
