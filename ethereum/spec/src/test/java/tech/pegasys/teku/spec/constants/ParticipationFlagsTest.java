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

package tech.pegasys.teku.spec.constants;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;

public class ParticipationFlagsTest {

  @Test
  public void isTimelyTarget() {
    assertThat(ParticipationFlags.isTimelyTarget(1)).isFalse();
    assertThat(ParticipationFlags.isTimelyTarget(2)).isTrue();
    assertThat(ParticipationFlags.isTimelyTarget(3)).isTrue();
    assertThat(ParticipationFlags.isTimelyTarget(4)).isFalse();
    assertThat(ParticipationFlags.isTimelyTarget(5)).isFalse();
    assertThat(ParticipationFlags.isTimelyTarget(6)).isTrue();
    assertThat(ParticipationFlags.isTimelyTarget(7)).isTrue();
    assertThat(ParticipationFlags.isTimelyTarget(8)).isFalse();
    assertThat(ParticipationFlags.isTimelyTarget(12)).isFalse();
  }

  @Test
  public void isAnyFlagSet() {
    assertThat(ParticipationFlags.isAnyFlagSet(1)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(2)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(3)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(4)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(5)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(6)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(7)).isTrue();
    assertThat(ParticipationFlags.isAnyFlagSet(9)).isTrue();

    assertThat(ParticipationFlags.isAnyFlagSet(0)).isFalse();
    assertThat(ParticipationFlags.isAnyFlagSet(8)).isFalse();
  }
}
