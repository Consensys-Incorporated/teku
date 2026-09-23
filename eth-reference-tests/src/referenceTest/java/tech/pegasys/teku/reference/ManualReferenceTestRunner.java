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

package tech.pegasys.teku.reference;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethtests.finder.ReferenceTestFinder;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

/**
 * IntelliJ doesn't seem to like running tests from the generated source directory so this provides
 * a convenient way to run reference tests in IntelliJ.
 *
 * <p>The test case is disabled as the tests run via the generated classes in CI, but it still runs
 * without removing the @Disabled in IntelliJ.
 */
@Disabled
public class ManualReferenceTestRunner extends Eth2ReferenceTestCase {

  /**
   * Filter test to run to those with test type starting with this value.
   *
   * <p>e.g. set to "ssz_static" to run only ssz static tests or "ssz_static/Attestation" for only
   * attestation ssz tests.
   *
   * <p>May be overridden by the ENV_TEST_TYPE environment variable.
   */
  private static final String TEST_TYPE = "";

  /**
   * Filter test to run to those from the specified spec. One of general, minimal or mainnet
   *
   * <p>May be overridden by the ENV_SPEC environment variable.
   */
  private static final String SPEC = "";

  /**
   * Filter test to run only those for a specific milestone. Use values from TestFork.
   *
   * <p>May be overridden by the ENV_MILESTONE environment variable.
   */
  private static final String MILESTONE = "";

  /**
   * Filter tests to run only those where the display name contains this string.
   *
   * <p>May be overridden by the ENV_DISPLAY_NAME environment variable.
   */
  private static final String DISPLAY_NAME = "";

  @ParameterizedTest(name = "{0}")
  @MethodSource("loadReferenceTests")
  void shouldRunReferenceTest(final String name, final TestDefinition testDefinition)
      throws Throwable {
    runReferenceTest(testDefinition);
  }

  @SuppressWarnings("ConstantConditions")
  @MustBeClosed
  static Stream<Arguments> loadReferenceTests() throws IOException {
    return ReferenceTestFinder.findReferenceTests()
        .filter(ManualReferenceTestRunner::filterBySpec)
        .filter(ManualReferenceTestRunner::filterByTestType)
        .filter(ManualReferenceTestRunner::filterByMilestone)
        .filter(ManualReferenceTestRunner::filterByDisplayName)
        .map(testDefinition -> Arguments.of(testDefinition.getDisplayName(), testDefinition));
  }

  private static boolean filterByTestType(final TestDefinition testDefinition) {
    final Optional<String> maybeTestTypeOverride = environmentVariableOverride("ENV_TEST_TYPE");

    if (TEST_TYPE.isBlank() && maybeTestTypeOverride.isEmpty()) {
      return true;
    }

    return testDefinition.getTestType().startsWith(maybeTestTypeOverride.orElse(TEST_TYPE));
  }

  @SuppressWarnings("ConstantConditions")
  private static boolean filterBySpec(final TestDefinition testDefinition) {
    final Optional<String> maybeSpecOverride = environmentVariableOverride("ENV_SPEC");

    if (SPEC.isBlank() && maybeSpecOverride.isEmpty()) {
      return true;
    }

    return testDefinition.getConfigName().equalsIgnoreCase(maybeSpecOverride.orElse(SPEC));
  }

  @SuppressWarnings("ConstantConditions")
  private static boolean filterByMilestone(final TestDefinition testDefinition) {
    final Optional<String> maybeMilestoneOverride = environmentVariableOverride("ENV_MILESTONE");

    if (MILESTONE.isBlank() && maybeMilestoneOverride.isEmpty()) {
      return true;
    }

    return maybeMilestoneOverride.orElse(MILESTONE).equals(testDefinition.getFork());
  }

  @SuppressWarnings("ConstantConditions")
  private static boolean filterByDisplayName(final TestDefinition testDefinition) {
    final Optional<String> maybeDisplayNameOverride =
        environmentVariableOverride("ENV_DISPLAY_NAME");

    if (DISPLAY_NAME.isBlank() && maybeDisplayNameOverride.isEmpty()) {
      return true;
    }

    return testDefinition.getDisplayName().contains(maybeDisplayNameOverride.orElse(DISPLAY_NAME));
  }

  private static Optional<String> environmentVariableOverride(final String name) {
    return Optional.ofNullable(System.getenv(name));
  }
}
