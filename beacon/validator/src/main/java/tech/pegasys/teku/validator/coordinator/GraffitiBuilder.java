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

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

/**
 * Generates graffiti by combining user-supplied graffiti with information from CL/EL clients,
 * according to the clientGraffitiAppendFormat configuration.
 */
public class GraffitiBuilder implements ExecutionClientVersionChannel {

  private static final Logger LOG = LogManager.getLogger();

  private static final String SPACE = " ";

  private volatile Optional<ClientVersion> executionClientVersion = Optional.empty();

  private final ClientGraffitiAppendFormat clientGraffitiAppendFormat;
  private final ClientVersion consensusClientVersion;

  public GraffitiBuilder(final ClientGraffitiAppendFormat clientGraffitiAppendFormat) {
    this.clientGraffitiAppendFormat = clientGraffitiAppendFormat;
    this.consensusClientVersion = createTekuClientVersion();
  }

  private ClientVersion createTekuClientVersion() {
    return new ClientVersion(
        ClientVersion.TEKU_CLIENT_CODE,
        VersionProvider.CLIENT_IDENTITY,
        VersionProvider.IMPLEMENTATION_VERSION,
        VersionProvider.COMMIT_HASH
            .map(commitHash -> Bytes4.fromHexString(commitHash.substring(0, 8)))
            .orElse(Bytes4.ZERO));
  }

  public ClientVersion getConsensusClientVersion() {
    return consensusClientVersion;
  }

  @Override
  public void onExecutionClientVersion(final ClientVersion executionClientVersion) {
    this.executionClientVersion = Optional.of(executionClientVersion);
    logGraffitiWatermark();
  }

  @Override
  public void onExecutionClientVersionNotAvailable() {
    logGraffitiWatermark();
  }

  private void logGraffitiWatermark() {
    final Optional<Bytes32> graffitiWatermark = Optional.of(buildGraffiti(Optional.empty()));
    EVENT_LOG.logGraffitiWatermark(
        extractGraffiti(graffitiWatermark, calculateGraffitiLength(graffitiWatermark)));
  }

  public Bytes32 buildGraffiti(final Optional<Bytes32> userGraffiti) {
    try {
      final int userGraffitiLength = calculateGraffitiLength(userGraffiti);

      return switch (clientGraffitiAppendFormat) {
        case AUTO -> {
          final int clientInfoLength = Bytes32.SIZE - 1 - userGraffitiLength;
          // Could drop SPACE's `-1` in a corner case
          if (clientInfoLength == 3) {
            yield joinNonEmpty(
                "", extractGraffiti(userGraffiti, userGraffitiLength), formatClientsInfo(4));
          }
          yield joinNonEmpty(
              SPACE,
              extractGraffiti(userGraffiti, userGraffitiLength),
              formatClientsInfo(clientInfoLength));
        }
        case CLIENT_CODES -> {
          final int spaceAvailable = Bytes32.SIZE - userGraffitiLength;
          if (spaceAvailable == 2 || spaceAvailable == 4) {
            yield joinNonEmpty(
                "",
                extractGraffiti(userGraffiti, userGraffitiLength),
                formatClientsInfo(spaceAvailable));
          }
          if (spaceAvailable < 2) {
            yield joinNonEmpty(SPACE, extractGraffiti(userGraffiti, userGraffitiLength));
          }
          yield joinNonEmpty(
              SPACE,
              extractGraffiti(userGraffiti, userGraffitiLength),
              formatClientsInfo(Math.min(5, spaceAvailable)));
        }
        case DISABLED -> userGraffiti.orElse(Bytes32.ZERO);
      };
    } catch (final Exception ex) {
      LOG.error("Unexpected error when preparing block graffiti", ex);
      return userGraffiti.orElse(Bytes32.ZERO);
    }
  }

  @VisibleForTesting
  protected String extractGraffiti(final Optional<Bytes32> graffiti, final int length) {
    return graffiti
        .map(Bytes::toArray)
        .map(bytes -> Arrays.copyOf(bytes, length))
        .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .orElse("");
  }

  @VisibleForTesting
  protected int calculateGraffitiLength(final Optional<Bytes32> graffiti) {
    return Bytes32.SIZE - graffiti.map(Bytes::numberOfTrailingZeroBytes).orElse(Bytes32.SIZE);
  }

  @VisibleForTesting
  protected Bytes32 joinNonEmpty(final String delimiter, final String... parts) {
    final String graffiti =
        Arrays.stream(parts).filter(part -> !part.isEmpty()).collect(Collectors.joining(delimiter));
    return Bytes32Parser.toBytes32(graffiti);
  }

  @VisibleForTesting
  protected String formatClientsInfo(final int watermarkMaxLength) {
    final String consensusCode = consensusClientVersion.code();
    final String executionCode = getExecutionCodeSafely();

    // BU0f91LH1be5
    if (watermarkMaxLength >= 12) {
      return String.format(
          "%s%s%s%s",
          executionCode,
          executionClientVersion.map(clientVersion -> getCommit(clientVersion, 4)).orElse(""),
          consensusCode,
          getCommit(consensusClientVersion, 4));
    }
    // BU0fLH1b
    if (watermarkMaxLength >= 8) {
      return String.format(
          "%s%s%s%s",
          executionCode,
          executionClientVersion.map(clientVersion -> getCommit(clientVersion, 2)).orElse(""),
          consensusCode,
          getCommit(consensusClientVersion, 2));
    }
    // BULH
    if (watermarkMaxLength >= 4) {
      return String.format("%s%s", executionCode, consensusCode);
    }

    // BU
    if (watermarkMaxLength >= 2) {
      return String.format("%s", executionCode.isEmpty() ? consensusCode : executionCode);
    }

    return "";
  }

  private String getExecutionCodeSafely() {
    return executionClientVersion
        .map(
            clientVersion -> {
              final String code = clientVersion.code();
              final boolean isValid =
                  code != null && code.length() >= 2 && code.substring(0, 2).matches("[a-zA-Z]{2}");
              return isValid
                  ? code.substring(0, 2).toUpperCase(Locale.ROOT)
                  : ClientVersion.UNKNOWN_CLIENT_CODE;
            })
        .orElse("");
  }

  private String getCommit(final ClientVersion clientVersion) {
    return clientVersion.commit().toUnprefixedHexString();
  }

  private String getCommit(final ClientVersion clientVersion, final int length) {
    return getCommit(clientVersion).substring(0, length);
  }
}
