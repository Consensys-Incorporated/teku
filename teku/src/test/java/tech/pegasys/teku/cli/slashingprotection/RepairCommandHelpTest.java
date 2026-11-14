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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;

class RepairCommandHelpTest extends AbstractBeaconNodeCommandTest {
  private final PrintStream originalStdOut = System.out;

  @TempDir private Path dataDir;

  @AfterEach
  void tearDown() {
    System.setOut(originalStdOut);
  }

  @Test
  void shouldOnlyShowOptionsThatRepairUses() {
    assertThat(beaconNodeCommand.parse(new String[] {"slashing-protection", "repair", "--help"}))
        .isZero();
    final String help = getCommandLineOutput();

    assertThat(help)
        .contains("--network")
        .contains("--initial-state")
        .contains("--genesis-state")
        .contains("--checkpoint-sync-url")
        .contains("--data-validator-path")
        .contains("--slot")
        .contains("--check-only-enabled")
        .contains("--update-all-enabled")
        .contains("--validator-keys")
        .contains("--validators-external-signer-public-keys")
        .contains("specified slot as a minimum. Should be a future");
    HideUnusedRepairOptions.UNUSED_OPTIONS.forEach(
        option -> assertThat(help).doesNotContain(option + "="));
    assertThat(help).doesNotContain("--ignore-weak-subjectivity-period-enabled");
  }

  @Test
  void shouldStillShowHiddenOptionsInBeaconNodeHelp() {
    assertThat(beaconNodeCommand.parse(new String[] {"--help"})).isZero();
    final String help = getCommandLineOutput();

    HideUnusedRepairOptions.UNUSED_OPTIONS.forEach(option -> assertThat(help).contains(option));
  }

  @Test
  void shouldStillAcceptHiddenOptions() throws IOException {
    final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdOut, true, StandardCharsets.UTF_8));
    Files.createDirectories(dataDir.resolve("validator").resolve("slashprotection"));

    final int exitCode =
        beaconNodeCommand.parse(
            new String[] {
              "slashing-protection",
              "repair",
              "--data-path",
              dataDir.toString(),
              "--network",
              "minimal",
              "--slot",
              "100",
              "--check-only-enabled",
              "--ignore-weak-subjectivity-period-enabled",
              "--validators-external-signer-url",
              "http://localhost:9000",
              "--validators-external-signer-timeout",
              "5000",
              "--validators-external-signer-concurrent-limit",
              "10"
            });

    assertThat(exitCode).describedAs(getCommandLineOutput()).isZero();
    assertThat(stdOut.toString(StandardCharsets.UTF_8)).contains("done");
  }
}
