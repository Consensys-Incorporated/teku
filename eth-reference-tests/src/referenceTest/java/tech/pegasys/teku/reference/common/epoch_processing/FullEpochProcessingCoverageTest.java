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

package tech.pegasys.teku.reference.common.epoch_processing;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.common.epoch_processing.EpochProcessingTestExecutor.PRE_EPOCH_STATE_FILE;
import static tech.pegasys.teku.reference.common.epoch_processing.EpochProcessingTestExecutor.SKIP_FULL_EPOCH_CHECK;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethtests.finder.ReferenceTestFinder;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

/**
 * The full epoch check in {@link EpochProcessingTestExecutor} silently skips vectors without full
 * epoch states or listed in its skip list. Fail if either stops meaning what it should, e.g. the
 * file was renamed or a skipped vector was removed.
 */
class FullEpochProcessingCoverageTest {

  private static List<TestDefinition> epochProcessingTests;

  @BeforeAll
  static void findEpochProcessingTests() throws Exception {
    try (Stream<TestDefinition> tests = ReferenceTestFinder.findReferenceTests()) {
      epochProcessingTests =
          tests.filter(test -> test.getTestType().startsWith("epoch_processing/")).toList();
    }
  }

  @Test
  void everyForkHasEpochProcessingVectorsWithFullEpochStates() {
    final Map<String, Boolean> hasFullEpochStatesByConfigAndFork =
        epochProcessingTests.stream()
            .collect(
                Collectors.toMap(
                    test -> test.getConfigName() + " - " + test.getFork(),
                    test -> Files.exists(test.getTestDirectory().resolve(PRE_EPOCH_STATE_FILE)),
                    Boolean::logicalOr));

    assertThat(hasFullEpochStatesByConfigAndFork).isNotEmpty().doesNotContainValue(false);
  }

  @Test
  void everySkippedFullEpochCheckMatchesAVector() {
    final Set<String> testNames =
        epochProcessingTests.stream().map(TestDefinition::getDisplayName).collect(toSet());
    assertThat(SKIP_FULL_EPOCH_CHECK.stream().filter(skipped -> !testNames.contains(skipped)))
        .describedAs("stale entries in SKIP_FULL_EPOCH_CHECK")
        .isEmpty();
  }
}
