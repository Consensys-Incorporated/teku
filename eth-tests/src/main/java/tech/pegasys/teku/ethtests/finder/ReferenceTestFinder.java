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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFunction;

@SuppressWarnings("MustBeClosedChecker")
public class ReferenceTestFinder {

  // Can be overridden with -Dteku.ref-test-module.override-root="<path>"
  private static final Path TEST_PATH_FROM_MODULE =
      Path.of("src", "referenceTest", "resources", "consensus-spec-tests", "tests");
  private static final List<String> SUPPORTED_FORKS =
      List.of(
          TestFork.PHASE0,
          TestFork.ALTAIR,
          TestFork.BELLATRIX,
          TestFork.CAPELLA,
          TestFork.DENEB,
          TestFork.ELECTRA,
          TestFork.FULU,
          TestFork.GLOAS);

  @MustBeClosed
  public static Stream<TestDefinition> findReferenceTests() throws IOException {
    return findSpecDirectories().flatMap(unchecked(ReferenceTestFinder::findTestTypes));
  }

  @MustBeClosed
  private static Stream<TestDefinition> findTestTypes(final Path specDirectory) throws IOException {
    final String spec = specDirectory.getFileName().toString();
    if (spec.equals("bls")) {
      return new BlsRefTestFinder().findTests("", spec, specDirectory);
    }
    if (spec.equals("slashing-protection-interchange")) {
      return new SlashingProtectionInterchangeRefTestFinder().findTests("", spec, specDirectory);
    }
    return SUPPORTED_FORKS.stream()
        .flatMap(
            fork -> {
              final Path testsPath = specDirectory.resolve(fork);
              if (!testsPath.toFile().exists()) {
                return Stream.empty();
              }
              return Stream.of(
                      new BlsTestFinder(),
                      new KzgTestFinder(),
                      new SszTestFinder("ssz_generic"),
                      new SszTestFinder("ssz_static"),
                      new ShufflingTestFinder(),
                      new PyspecTestFinder(
                          List.of(),
                          List.of(
                              // TODO-GLOAS: the following tests require equivocation
                              // see https://github.com/Consensys-Incorporated/teku/issues/10608
                              "gloas - minimal - fork_choice/reorg - include_votes_another_empty_chain_with_enough_ffg_votes_previous_epoch",
                              "gloas - minimal - fork_choice/reorg - simple_attempted_reorg_without_enough_ffg_votes",
                              "gloas - minimal - fork_choice/reorg - include_votes_another_empty_chain_with_enough_ffg_votes_current_epoch",
                              "gloas - minimal - fork_choice/reorg - include_votes_another_empty_chain_without_enough_ffg_votes_current_epoch")),
                      new MerkleProofTestFinder())
                  .flatMap(unchecked(finder -> finder.findTests(fork, spec, testsPath)));
            });
  }

  @MustBeClosed
  private static Stream<Path> findSpecDirectories() throws IOException {
    return Files.list(findReferenceTestRootDirectory());
  }

  public static Path findReferenceTestRootDirectory() {
    final List<Path> searchPaths =
        List.of(
            Path.of(System.getProperty("teku.ref-test-module.path", "")), // Set explicitly
            Path.of(System.getProperty("user.dir")), // Run from eth-reference-tests module
            Path.of(System.getProperty("user.dir"), "eth-reference-tests") // Run from teku root
            );
    return searchPaths.stream()
        .map(
            path ->
                path.resolve(
                    System.getProperty(
                        "teku.ref-test-module.override-root", TEST_PATH_FROM_MODULE.toString())))
        .filter(path -> path.toFile().exists())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to find the reference tests module. Try setting teku.ref-test-module.path system property"
                        + " and ensure you have run ./gradlew expandRefTests"));
  }

  static <I, O> Function<I, O> unchecked(final ExceptionThrowingFunction<I, O> function) {
    return input -> {
      try {
        return function.apply(input);
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    };
  }
}
