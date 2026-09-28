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

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ShufflingTestFinder implements TestFinder {

  public static final String SHUFFLING_TEST_CATEGORY = "shuffling";

  @Override
  @MustBeClosed
  public Stream<TestDefinition> findTests(
      final String fork, final String config, final Path testRoot) throws IOException {
    final Path shufflingDir = testRoot.resolve(SHUFFLING_TEST_CATEGORY);
    if (!Files.exists(shufflingDir)) {
      return Stream.empty();
    }
    return Files.walk(shufflingDir)
        .filter(path -> Files.exists(path.resolve("mapping.yaml")))
        .map(
            testDir -> {
              final String testName = shufflingDir.relativize(testDir).toString();
              return new TestDefinition(
                  fork,
                  config,
                  SHUFFLING_TEST_CATEGORY,
                  testName,
                  testRoot.relativize(testDir).toString());
            });
  }
}
