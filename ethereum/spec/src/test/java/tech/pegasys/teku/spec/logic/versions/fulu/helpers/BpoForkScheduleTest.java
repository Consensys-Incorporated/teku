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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

public class BpoForkScheduleTest {

  private final Spec spec =
      TestSpecFactory.createMinimalFulu(
          b ->
              b.fuluBuilder(
                  fb ->
                      fb.blobSchedule(
                          List.of(
                              new BlobScheduleEntry(UInt64.valueOf(100), 100),
                              new BlobScheduleEntry(UInt64.valueOf(150), 175),
                              new BlobScheduleEntry(UInt64.valueOf(200), 200),
                              new BlobScheduleEntry(UInt64.valueOf(250), 275),
                              new BlobScheduleEntry(UInt64.valueOf(275), 250),
                              new BlobScheduleEntry(UInt64.valueOf(300), 300)))));

  private final BpoForkSchedule bpoForkSchedule =
      new BpoForkSchedule(
          SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig()));

  @Test
  public void emptyBpoForkWhenPriorBpoForks() {
    assertThat(bpoForkSchedule.getBpoFork(UInt64.valueOf(50))).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("getsBpoForkForAGivenEpochScenarios")
  public void getsBpoForkForAGivenEpoch(final UInt64 epoch, final BlobParameters expectedBpoFork) {
    assertThat(bpoForkSchedule.getBpoFork(epoch)).hasValue(expectedBpoFork);
  }

  @ParameterizedTest
  @MethodSource("getsNextBpoForkForAGivenEpochScenarios")
  public void getsNextBpoForkForAGivenEpoch(
      final UInt64 epoch, final BlobParameters expectedBpoFork) {
    assertThat(bpoForkSchedule.getNextBpoFork(epoch)).hasValue(expectedBpoFork);
  }

  @Test
  public void emptyNextBpoForkWhenNoFutureBpoForks() {
    assertThat(bpoForkSchedule.getNextBpoFork(UInt64.valueOf(300))).isEmpty();
    assertThat(bpoForkSchedule.getNextBpoFork(UInt64.valueOf(340))).isEmpty();
  }

  @Test
  public void getsHighestMaxBlobsPerBlock() {
    assertThat(bpoForkSchedule.getHighestMaxBlobsPerBlock()).hasValue(300);
  }

  public static Stream<Arguments> getsBpoForkForAGivenEpochScenarios() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(100), new BlobParameters(UInt64.valueOf(100), 100)),
        Arguments.of(UInt64.valueOf(130), new BlobParameters(UInt64.valueOf(100), 100)),
        Arguments.of(UInt64.valueOf(150), new BlobParameters(UInt64.valueOf(150), 175)),
        Arguments.of(UInt64.valueOf(251), new BlobParameters(UInt64.valueOf(250), 275)),
        Arguments.of(UInt64.valueOf(309), new BlobParameters(UInt64.valueOf(300), 300)));
  }

  public static Stream<Arguments> getsNextBpoForkForAGivenEpochScenarios() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(100), new BlobParameters(UInt64.valueOf(150), 175)),
        Arguments.of(UInt64.valueOf(130), new BlobParameters(UInt64.valueOf(150), 175)),
        Arguments.of(UInt64.valueOf(150), new BlobParameters(UInt64.valueOf(200), 200)),
        Arguments.of(UInt64.valueOf(251), new BlobParameters(UInt64.valueOf(275), 250)),
        Arguments.of(UInt64.valueOf(299), new BlobParameters(UInt64.valueOf(300), 300)));
  }
}
