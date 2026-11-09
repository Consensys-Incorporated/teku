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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class DatabaseNetworkTest {
  DataStructureUtil dataStructureUtil = new DataStructureUtil(TestSpecFactory.createDefault());
  private ObjectMapper objectMapper;
  private static final String NETWORK_FILENAME = "network.yml";

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
  }

  @Test
  public void shouldCreateNetworkFile(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();
    final Long depositChainId = dataStructureUtil.randomLong();
    assertThat(
            DatabaseNetwork.init(networkFile, fork, eth1Address, depositChainId, Optional.empty()))
        .isEqualTo(
            new DatabaseNetwork(
                fork.toHexString().toLowerCase(Locale.ROOT),
                eth1Address.toHexString().toLowerCase(Locale.ROOT),
                depositChainId));
    assertThat(networkFile).exists();
  }

  @Test
  public void shouldThrowIfForkDiffers(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();
    final Long depositChainId = dataStructureUtil.randomLong();
    DatabaseNetwork.init(
        networkFile,
        dataStructureUtil.randomFork().getCurrentVersion(),
        eth1Address,
        depositChainId,
        Optional.empty());

    assertThatThrownBy(
            () ->
                DatabaseNetwork.init(
                    networkFile, fork, eth1Address, depositChainId, Optional.empty()))
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageStartingWith("Supplied fork version");
  }

  @Test
  public void shouldThrowIfDepositContractDiffers(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();
    final Long depositChainId = dataStructureUtil.randomLong();

    DatabaseNetwork.init(
        networkFile, fork, dataStructureUtil.randomEth1Address(), depositChainId, Optional.empty());

    assertThatThrownBy(
            () ->
                DatabaseNetwork.init(
                    networkFile, fork, eth1Address, depositChainId, Optional.empty()))
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageStartingWith("Supplied deposit contract");
  }

  @Test
  public void shouldNotThrowIfForkAndContractMatch(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();
    final Long depositChainId = dataStructureUtil.randomLong();

    DatabaseNetwork.init(networkFile, fork, eth1Address, depositChainId, Optional.empty());

    assertDoesNotThrow(
        () ->
            DatabaseNetwork.init(networkFile, fork, eth1Address, depositChainId, Optional.empty()));
  }

  @Test
  void shouldWriteAndReadDatabaseNetworkWithDepositChainId(@TempDir final File tempDir)
      throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);

    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();
    final Long depositChainId = dataStructureUtil.randomLong();
    final DatabaseNetwork databaseNetwork =
        new DatabaseNetwork(fork.toHexString(), eth1Address.toHexString(), depositChainId);

    objectMapper.writerFor(DatabaseNetwork.class).writeValue(networkFile, databaseNetwork);
    final DatabaseNetwork readDatabaseNetwork =
        objectMapper.readerFor(DatabaseNetwork.class).readValue(networkFile);

    assertEquals(fork.toHexString(), readDatabaseNetwork.forkVersion);
    assertEquals(eth1Address.toHexString(), readDatabaseNetwork.depositContract);
    assertEquals(depositChainId, readDatabaseNetwork.depositChainId);
  }

  @Test
  void shouldWriteAndReadDatabaseNetworkWithoutDepositChainId(@TempDir final File tempDir)
      throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);

    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();

    final DatabaseNetwork databaseNetwork =
        new DatabaseNetwork(fork.toHexString(), eth1Address.toHexString());

    objectMapper.writerFor(DatabaseNetwork.class).writeValue(networkFile, databaseNetwork);
    String networkContent = Files.readString(networkFile.toPath());

    final DatabaseNetwork readDatabaseNetwork =
        objectMapper.readerFor(DatabaseNetwork.class).readValue(networkFile);

    assertFalse(networkContent.contains("deposit_chain_id"));
    assertEquals(fork.toHexString(), readDatabaseNetwork.forkVersion);
    assertEquals(eth1Address.toHexString(), readDatabaseNetwork.depositContract);
    assertNull(readDatabaseNetwork.depositChainId);
  }

  @Test
  void shouldNotIncludeDepositChainIdWhenNull(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, NETWORK_FILENAME);

    final Bytes4 fork = dataStructureUtil.randomFork().getCurrentVersion();
    final Eth1Address eth1Address = dataStructureUtil.randomEth1Address();

    final DatabaseNetwork databaseNetwork =
        new DatabaseNetwork(fork.toHexString(), eth1Address.toHexString(), null);

    objectMapper.writerFor(DatabaseNetwork.class).writeValue(networkFile, databaseNetwork);
    String networkContent = Files.readString(networkFile.toPath());

    final DatabaseNetwork readDatabaseNetwork =
        objectMapper.readerFor(DatabaseNetwork.class).readValue(networkFile);

    assertFalse(networkContent.contains("deposit_chain_id"));
    assertNull(readDatabaseNetwork.depositChainId);
  }
}
