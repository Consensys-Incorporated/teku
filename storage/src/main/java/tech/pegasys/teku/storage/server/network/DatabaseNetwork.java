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

package tech.pegasys.teku.storage.server.network;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseNetwork {
  @JsonProperty(value = "fork_version", required = true)
  @VisibleForTesting
  final String forkVersion;

  @JsonProperty(value = "deposit_contract", required = true)
  @VisibleForTesting
  final String depositContract;

  @JsonProperty("deposit_chain_id")
  @VisibleForTesting
  final Long depositChainId;

  @JsonCreator
  DatabaseNetwork(
      @JsonProperty(value = "fork_version") final String forkVersion,
      @JsonProperty(value = "deposit_contract") final String depositContract,
      @JsonProperty("deposit_chain_id") final Long depositChainId) {
    this.forkVersion = forkVersion;
    this.depositContract = depositContract;
    this.depositChainId = depositChainId;
  }

  @VisibleForTesting
  DatabaseNetwork(final String forkVersion, final String depositContract) {
    this(forkVersion, depositContract, null);
  }

  public static DatabaseNetwork init(
      final File source,
      final Bytes4 forkVersion,
      final Eth1Address depositContract,
      final Long depositChainId,
      final Optional<Eth2Network> maybeNetwork)
      throws IOException {
    final String forkVersionString = forkVersion.toHexString().toLowerCase(Locale.ROOT);
    final String depositContractString = depositContract.toHexString().toLowerCase(Locale.ROOT);
    final ObjectMapper objectMapper =
        new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
    if (source.exists()) {
      final DatabaseNetwork databaseNetwork =
          objectMapper.readerFor(DatabaseNetwork.class).readValue(source);

      if (!forkVersionString.equals(databaseNetwork.forkVersion)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage("fork version", forkVersionString, databaseNetwork.forkVersion));
      }
      if (databaseNetwork.depositContract != null
          && !databaseNetwork.depositContract.equals(depositContractString)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage(
                "deposit contract", depositContractString, databaseNetwork.depositContract));
      }
      if (databaseNetwork.depositChainId != null
          && maybeNetwork.map(n -> !n.equals(Eth2Network.EPHEMERY)).orElse(true)
          && !databaseNetwork.depositChainId.equals(depositChainId)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage(
                "deposit chain id",
                String.valueOf(depositChainId),
                String.valueOf(databaseNetwork.depositChainId)));
      }
      if (databaseNetwork.depositChainId != null
          && maybeNetwork.map(n -> n.equals(Eth2Network.EPHEMERY)).orElse(false)
          && !databaseNetwork.depositChainId.equals(depositChainId)) {
        throw new EphemeryException();
      }
      return databaseNetwork;
    } else {
      DatabaseNetwork databaseNetwork =
          new DatabaseNetwork(forkVersionString, depositContractString, depositChainId);
      objectMapper.writerFor(DatabaseNetwork.class).writeValue(source, databaseNetwork);
      return databaseNetwork;
    }
  }

  public Long getDepositChainId() {
    return depositChainId;
  }

  private static String formatMessage(
      final String fieldName, final String expected, final String actual) {
    return String.format(
        "Supplied %s (%s) does not match the stored database (%s). "
            + "Check that the existing database matches the current network settings.",
        fieldName, expected, actual);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DatabaseNetwork that = (DatabaseNetwork) o;
    return Objects.equals(forkVersion, that.forkVersion)
        && Objects.equals(depositContract, that.depositContract)
        && Objects.equals(depositChainId, that.depositChainId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkVersion, depositContract);
  }
}
