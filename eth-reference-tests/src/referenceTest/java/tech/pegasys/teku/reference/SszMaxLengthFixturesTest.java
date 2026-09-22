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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszMaxLengthExceededException;

class SszMaxLengthFixturesTest {

  private static final TestDefinition LISTED =
      new TestDefinition("gloas", "minimal", "operations/attestation", "invalid_too_many", "x");
  private static final TestDefinition UNLISTED =
      new TestDefinition("gloas", "minimal", "operations/attestation", "valid", "y");
  private static final Set<String> LIST =
      Set.of("gloas - minimal - operations/attestation - invalid_too_many");

  private final SszMaxLengthFixtures fixtures = new SszMaxLengthFixtures(LIST);

  @Test
  void listedFixturePassesWhenRejectedByLimit() {
    assertThatNoException()
        .isThrownBy(
            () ->
                fixtures.run(
                    LISTED,
                    __ -> {
                      throw new SszMaxLengthExceededException("List", 9, 8);
                    }));
  }

  @Test
  void listedFixtureFailsWhenNoLongerRejectedByLimit() {
    assertThatThrownBy(() -> fixtures.run(LISTED, __ -> {}))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("invalid_too_many")
        .hasMessageContaining("remove");
  }

  @Test
  void listedFixtureStillFailsOnOtherErrors() {
    assertThatThrownBy(
            () ->
                fixtures.run(
                    LISTED,
                    __ -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unlistedFixtureFailsWhenRejectedByLimit() {
    assertThatThrownBy(
            () ->
                fixtures.run(
                    UNLISTED,
                    __ -> {
                      throw new SszMaxLengthExceededException("List", 9, 8);
                    }))
        .isInstanceOf(SszMaxLengthExceededException.class);
  }

  @Test
  void unlistedFixtureRunsNormally() {
    assertThatNoException().isThrownBy(() -> fixtures.run(UNLISTED, __ -> {}));
  }
}
