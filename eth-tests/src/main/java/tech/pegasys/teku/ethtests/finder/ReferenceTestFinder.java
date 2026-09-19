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

  /** Finds every reference test in the location configured by system properties. */
  @MustBeClosed
  public static Stream<TestDefinition> findReferenceTests() throws IOException {
    return findReferenceTests(ReferenceTestRoot.fromSystemProperties());
  }

  @MustBeClosed
  public static Stream<TestDefinition> findReferenceTests(final ReferenceTestRoot root)
      throws IOException {
    return root.listSpecDirectories().flatMap(unchecked(ReferenceTestFinder::findTestTypes));
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
              if (!Files.exists(testsPath)) {
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
                              // see https://github.com/Consensys/teku/issues/10608
                              "gloas - minimal - fork_choice/reorg - include_votes_another_empty_chain_with_enough_ffg_votes_previous_epoch",
                              "gloas - minimal - fork_choice/reorg - simple_attempted_reorg_without_enough_ffg_votes",
                              "gloas - minimal - fork_choice/reorg - include_votes_another_empty_chain_with_enough_ffg_votes_current_epoch",
                              "gloas - minimal - fork_choice/reorg - include_votes_another_empty_chain_without_enough_ffg_votes_current_epoch")),
                      new MerkleProofTestFinder())
                  .flatMap(unchecked(finder -> finder.findTests(fork, spec, testsPath)));
            });
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
