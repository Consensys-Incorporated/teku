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

package tech.pegasys.teku.ethtests.finder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.ethtests.TestSpecConfig;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.YamlConfigReader;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifierImpl;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestDefinition {

  private final String fork;
  private final String configName;
  private final String testType;
  private final String testName;
  private final String pathFromPhaseTestDir;

  private Spec spec;

  public TestDefinition(
      final String fork,
      final String configName,
      final String testType,
      final String testName,
      final String pathFromPhaseTestDir) {
    this.configName = configName;
    this.fork = fork;
    this.testType = testType.replace("\\", "/");
    this.testName = testName.replace("\\", "/");
    this.pathFromPhaseTestDir = pathFromPhaseTestDir.replace("\\", "/");
  }

  public String getConfigName() {
    return configName;
  }

  public String getFork() {
    return fork;
  }

  public SpecMilestone getMilestone() {
    return switch (fork) {
      case TestFork.PHASE0 -> SpecMilestone.PHASE0;
      case TestFork.ALTAIR -> SpecMilestone.ALTAIR;
      case TestFork.BELLATRIX -> SpecMilestone.BELLATRIX;
      case TestFork.CAPELLA -> SpecMilestone.CAPELLA;
      case TestFork.DENEB -> SpecMilestone.DENEB;
      case TestFork.ELECTRA -> SpecMilestone.ELECTRA;
      case TestFork.FULU -> SpecMilestone.FULU;
      case TestFork.GLOAS -> SpecMilestone.GLOAS;
      default -> throw new IllegalArgumentException("Unknown fork: " + fork);
    };
  }

  public Spec getSpec() {
    return getSpec(true);
  }

  public Spec getSpec(final boolean blsSignatureVerificationEnabled) {
    if (spec == null) {
      createSpec(blsSignatureVerificationEnabled);
    }
    return spec;
  }

  private void createSpec(final boolean blsSignatureVerificationEnabled) {
    final Eth2Network network =
        switch (configName) {
          case TestSpecConfig.MAINNET -> Eth2Network.MAINNET;
          case TestSpecConfig.MINIMAL -> Eth2Network.MINIMAL;
          default -> throw new IllegalArgumentException("Unknown configName: " + configName);
        };
    final SpecMilestone milestone = getMilestone();
    final BLSSignatureVerifier blsSignatureVerifier =
        blsSignatureVerificationEnabled ? BLSSignatureVerifier.SIMPLE : BLSSignatureVerifier.NOOP;
    final Supplier<BatchSignatureVerifier> batchSignatureVerifierSupplier =
        blsSignatureVerificationEnabled
            ? BatchSignatureVerifierImpl::new
            : () -> BatchSignatureVerifier.NOOP;
    spec =
        TestSpecFactory.create(
            milestone,
            network,
            builder ->
                builder
                    .blsSignatureVerifier(blsSignatureVerifier)
                    .batchSignatureVerifierSupplier(batchSignatureVerifierSupplier),
            readConfigOverrides(milestone));
  }

  public String getTestType() {
    return testType;
  }

  public String getTestName() {
    return testName;
  }

  @Override
  public String toString() {
    return fork + " - " + configName + " - " + testType + " - " + testName;
  }

  public String getDisplayName() {
    return toString();
  }

  /** Path of the test directory relative to {@code <config>/<fork>}, always {@code /}-separated. */
  public String getPathFromPhaseTestDir() {
    return pathFromPhaseTestDir;
  }

  public Path getTestDirectory() {
    Path directory = ReferenceTestRoot.fromSystemProperties().getSpecDirectory(configName);
    if (!fork.isEmpty()) {
      directory = directory.resolve(fork);
    }
    if (!pathFromPhaseTestDir.isEmpty()) {
      directory = directory.resolve(pathFromPhaseTestDir);
    }
    return directory;
  }

  /// some reference tests ship a partial `config.yaml` overriding a handful of constants on top of
  /// the builtin config
  private Map<String, Object> readConfigOverrides(final SpecMilestone milestone) {
    final Path configOverridesYaml = getTestDirectory().resolve("config.yaml");
    if (!Files.exists(configOverridesYaml)) {
      return Map.of();
    }
    try (final InputStream in = Files.newInputStream(configOverridesYaml)) {
      final Map<String, Object> overrides = new YamlConfigReader().readValues(in);
      final UInt64 forkEpoch =
          Optional.ofNullable(overrides.get(forkEpochKey(milestone)))
              .map(value -> UInt64.valueOf(value.toString()))
              .orElse(UInt64.ZERO);
      return overrides.entrySet().stream()
          .filter(entry -> !isLaterPriorForkEpoch(entry, milestone, forkEpoch))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Vectors may pin the test fork's epoch after genesis while leaving earlier forks at later epochs
   * (e.g. FAR_FUTURE_EPOCH or the network's real fork epochs), since the state starts on the test
   * fork either way. Teku requires an ordered fork schedule, so drop those overrides and keep the
   * earlier forks active from genesis.
   */
  private static boolean isLaterPriorForkEpoch(
      final Map.Entry<String, Object> entry,
      final SpecMilestone milestone,
      final UInt64 forkEpoch) {
    return SpecMilestone.getAllPriorMilestones(milestone).stream()
            .anyMatch(prior -> entry.getKey().equals(forkEpochKey(prior)))
        && UInt64.valueOf(entry.getValue().toString()).isGreaterThan(forkEpoch);
  }

  private static String forkEpochKey(final SpecMilestone milestone) {
    return milestone.name() + "_FORK_EPOCH";
  }
}
