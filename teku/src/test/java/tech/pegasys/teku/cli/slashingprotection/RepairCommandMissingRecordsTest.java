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

package tech.pegasys.teku.cli.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.ValidatorClientService;

/**
 * Runs the repair command with --check-only-enabled, which never prompts or exits, to check how
 * validator keys without a slashing protection record are reported.
 */
class RepairCommandMissingRecordsTest extends AbstractBeaconNodeCommandTest {
  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
  private final PrintStream originalStdOut = System.out;
  private final InputStream originalStdIn = System.in;

  @TempDir private Path dataDir;

  private Path slashProtectionPath;
  private Path localKeystorePath;
  private BLSPublicKey keyWithRecord;
  private BLSPublicKey keyWithoutRecord;
  private BLSPublicKey externalKeyWithoutRecord;

  @BeforeEach
  void setUp() throws IOException {
    System.setOut(new PrintStream(stdOut, true, StandardCharsets.UTF_8));
    final DataDirLayout dataDirLayout =
        DataDirLayout.createFrom(DataConfig.builder().dataBasePath(dataDir).build());
    slashProtectionPath = ValidatorClientService.getSlashingProtectionPath(dataDirLayout);
    localKeystorePath = ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout);
    Files.createDirectories(slashProtectionPath);

    keyWithRecord =
        KnownValidatorKeysTest.writeKeystore(
            localKeystorePath.resolve("with-record.json"), BLSTestUtil.randomKeyPair(1));
    Files.write(
        recordPath(keyWithRecord),
        new ValidatorSigningRecord(
                Optional.empty(), UInt64.valueOf(5000), UInt64.valueOf(600), UInt64.valueOf(600))
            .toBytes()
            .toArrayUnsafe());
    keyWithoutRecord =
        KnownValidatorKeysTest.writeKeystore(
            localKeystorePath.resolve("without-record.json"), BLSTestUtil.randomKeyPair(2));
    externalKeyWithoutRecord = BLSTestUtil.randomPublicKey(3);
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalStdOut);
    System.setIn(originalStdIn);
  }

  @Test
  void shouldReportKeysWithoutRecordsWhenUpdateAllDisabled() throws IOException {
    final String output =
        runRepair(
            "--check-only-enabled",
            "--validators-external-signer-public-keys",
            externalKeyWithoutRecord.toString());

    assertThat(output)
        .contains("Found 2 validator keys from key-manager keystores in " + localKeystorePath)
        .contains(
            "Found 1 validator key from keys given by --validators-external-signer-public-keys")
        .contains(displayString(keyWithoutRecord) + ": no slashing protection record")
        .contains(displayString(externalKeyWithoutRecord) + ": no slashing protection record")
        .doesNotContain(displayString(keyWithRecord) + ": no slashing protection record")
        .contains(
            "2 validator keys have no slashing protection record, so the validator client will"
                + " treat them as never having signed. --update-all-enabled would create the"
                + " missing records.")
        .doesNotContain("will create the missing");
    assertThat(recordFiles()).containsExactly(recordPath(keyWithRecord));
  }

  @Test
  void shouldReportRecordsThatWouldBeCreatedWhenUpdateAllEnabled() throws IOException {
    final String output =
        runRepair(
            "--check-only-enabled",
            "--update-all-enabled",
            "--validators-external-signer-public-keys",
            externalKeyWithoutRecord.toString());

    assertThat(output)
        .contains(displayString(keyWithoutRecord) + ": no slashing protection record")
        .contains(displayString(externalKeyWithoutRecord) + ": no slashing protection record")
        .doesNotContain("--update-all-enabled would create")
        .contains("Updates have been skipped as --check-only-enabled was set.")
        .contains(
            "2 validator keys have no slashing protection record, so the validator client will"
                + " treat them as never having signed. --update-all-enabled will create the"
                + " missing records.");
    assertThat(recordFiles()).containsExactly(recordPath(keyWithRecord));
  }

  @Test
  void shouldReadValidatorKeySourcesFromConfigFile() throws IOException {
    final Path keystore = dataDir.resolve("keys").resolve("validator-key.json");
    final Path password = dataDir.resolve("password.txt");
    final BLSPublicKey validatorKey =
        KnownValidatorKeysTest.writeKeystore(keystore, BLSTestUtil.randomKeyPair(4));
    Files.writeString(password, "password");
    final Path configFile = dataDir.resolve("config.yaml");
    Files.writeString(
        configFile,
        "network: \"minimal\"\n"
            + "validator-keys: \""
            + keystore
            + File.pathSeparator
            + password
            + "\"\n"
            + "validators-external-signer-public-keys: [\""
            + externalKeyWithoutRecord
            + "\", \"http://localhost:1/keys\"]\n"
            // options for other commands are allowed in the config file
            + "ee-endpoint: \"http://localhost:8551\"\n");

    final String output =
        runRepairWithArgs(
            "slashing-protection",
            "repair",
            "--config-file",
            configFile.toString(),
            "--data-path",
            dataDir.toString(),
            "--slot",
            "100",
            "--check-only-enabled",
            "--update-all-enabled");

    assertThat(output)
        .contains("Found 1 validator key from keystores given by --validator-keys")
        .contains(displayString(validatorKey) + ": no slashing protection record")
        .contains(displayString(externalKeyWithoutRecord) + ": no slashing protection record")
        .contains("These keys were not checked for a missing slashing protection record:")
        .contains(
            " - --validators-external-signer-public-keys: keys at http://localhost:1/keys were not"
                + " fetched")
        .contains(
            "3 validator keys have no slashing protection record, so the validator client will"
                + " treat them as never having signed. --update-all-enabled will create the"
                + " missing records.");
  }

  @Test
  void shouldCreateMissingRecordsOnlyWhenUpdateAllConfirmed() throws IOException {
    System.setIn(new ByteArrayInputStream("yes\n".getBytes(StandardCharsets.UTF_8)));

    final String output =
        runRepair(
            "--update-all-enabled",
            "--validators-external-signer-public-keys",
            externalKeyWithoutRecord.toString());

    assertThat(output)
        .contains(
            "2 new slashing protection records will be created for validator keys that don't"
                + " have one.")
        .contains(displayString(keyWithoutRecord) + ": updating")
        .contains(displayString(externalKeyWithoutRecord) + ": updating");
    // slot 100 is in epoch 12 on minimal; no genesis validators root is recorded
    final ValidatorSigningRecord expected =
        new ValidatorSigningRecord(
            Optional.empty(), UInt64.valueOf(100), UInt64.valueOf(12), UInt64.valueOf(12));
    assertThat(readRecord(keyWithoutRecord)).isEqualTo(expected);
    assertThat(readRecord(externalKeyWithoutRecord)).isEqualTo(expected);
    assertThat(readRecord(keyWithRecord))
        .isEqualTo(
            new ValidatorSigningRecord(
                Optional.empty(), UInt64.valueOf(5000), UInt64.valueOf(600), UInt64.valueOf(600)));
  }

  @Test
  void shouldNoteThatKeysWereNotCheckedWhenNoKeySourcesExist() throws IOException {
    deleteRecursively(localKeystorePath.getParent());

    final String output = runRepair("--check-only-enabled", "--update-all-enabled");

    assertThat(output)
        .contains(
            "No validator keys were found in the key-manager directories, --validator-keys or"
                + " --validators-external-signer-public-keys, so keys without a slashing"
                + " protection record were not checked.")
        .doesNotContain("will create the missing");
  }

  @Test
  void shouldOnlyCreateMissingSlashingProtectionPathWhenCreatingRecords() throws IOException {
    deleteRecursively(slashProtectionPath);
    final SubCommandLogger log = mock(SubCommandLogger.class);
    final KnownValidatorKeys withKeys =
        new KnownValidatorKeys(List.of(keyWithoutRecord), List.of(), true);
    final KnownValidatorKeys withoutKeys = new KnownValidatorKeys(List.of(), List.of(), true);

    assertThat(withKeys.willCreateSlashingProtectionPath(log, slashProtectionPath, false, false))
        .isFalse();
    assertThat(withKeys.willCreateSlashingProtectionPath(log, slashProtectionPath, true, true))
        .isFalse();
    assertThat(withoutKeys.willCreateSlashingProtectionPath(log, slashProtectionPath, true, false))
        .isFalse();
    verifyNoInteractions(log);
    assertThat(withKeys.willCreateSlashingProtectionPath(log, slashProtectionPath, true, false))
        .isTrue();
    verify(log)
        .display(
            slashProtectionPath + " does not exist, and will be created for the missing records.");

    Files.createDirectories(slashProtectionPath);
    assertThat(withKeys.willCreateSlashingProtectionPath(log, slashProtectionPath, true, false))
        .isFalse();
  }

  @Test
  void shouldCreateMissingSlashingProtectionPathWhenConfirmed() throws IOException {
    deleteRecursively(slashProtectionPath);
    System.setIn(new ByteArrayInputStream("yes\n".getBytes(StandardCharsets.UTF_8)));

    final String output = runRepair("--update-all-enabled");

    assertThat(output)
        .contains(
            slashProtectionPath + " does not exist, and will be created for the missing records.")
        .contains(
            "2 new slashing protection records will be created for validator keys that don't"
                + " have one.");
    assertThat(recordFiles())
        .containsExactlyInAnyOrder(recordPath(keyWithRecord), recordPath(keyWithoutRecord));
  }

  private String runRepair(final String... extraArgs) {
    final String[] args =
        Stream.concat(
                Stream.of(
                    "slashing-protection",
                    "repair",
                    "--data-path",
                    dataDir.toString(),
                    "--network",
                    "minimal",
                    "--slot",
                    "100"),
                Stream.of(extraArgs))
            .toArray(String[]::new);
    return runRepairWithArgs(args);
  }

  private String runRepairWithArgs(final String... args) {
    final int exitCode = beaconNodeCommand.parse(args);
    final String output = stdOut.toString(StandardCharsets.UTF_8);
    assertThat(exitCode).describedAs(output + getCommandLineOutput()).isZero();
    return output;
  }

  private Path recordPath(final BLSPublicKey publicKey) {
    return slashProtectionPath.resolve(displayString(publicKey) + ".yml");
  }

  private ValidatorSigningRecord readRecord(final BLSPublicKey publicKey) throws IOException {
    return ValidatorSigningRecord.fromBytes(Bytes.wrap(Files.readAllBytes(recordPath(publicKey))));
  }

  private List<Path> recordFiles() throws IOException {
    try (Stream<Path> files = Files.list(slashProtectionPath)) {
      return files.toList();
    }
  }

  private static String displayString(final BLSPublicKey publicKey) {
    return publicKey.toBytesCompressed().toUnprefixedHexString();
  }

  private static void deleteRecursively(final Path path) throws IOException {
    try (Stream<Path> files = Files.walk(path)) {
      for (final Path file : files.sorted((a, b) -> b.compareTo(a)).toList()) {
        Files.delete(file);
      }
    }
  }
}
