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

package tech.pegasys.teku.spec.logic.versions.altair.helpers;

import static tech.pegasys.teku.spec.constants.ParticipationFlags.indexToFlag;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.IncentivizationWeights;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class MiscHelpersAltair extends MiscHelpers {

  public static final List<UInt64> PARTICIPATION_FLAG_WEIGHTS =
      List.of(
          IncentivizationWeights.TIMELY_SOURCE_WEIGHT,
          IncentivizationWeights.TIMELY_TARGET_WEIGHT,
          IncentivizationWeights.TIMELY_HEAD_WEIGHT);

  public MiscHelpersAltair(final SpecConfig specConfig) {
    super(specConfig);
  }

  /**
   * Return a new ParticipationFlags adding flagIndex to flags.
   *
   * @param participationFlags the current participation flags
   * @param flagIndex the flag index to add
   * @return the new participation flags value
   */
  public byte addFlag(final byte participationFlags, final int flagIndex) {
    final int flag = indexToFlag(flagIndex);
    return (byte) (participationFlags | flag);
  }

  public byte addFlags(final byte participationFlags, final byte newFlags) {
    return (byte) (participationFlags | newFlags);
  }

  /**
   * Return whether participationFlags has flagIndex set.
   *
   * @param participationFlags the participation flags to check
   * @param flagIndex the flag index to check for
   * @return true if flagIndex is set in participationFlags
   */
  public boolean hasFlag(final byte participationFlags, final int flagIndex) {
    final int flag = indexToFlag(flagIndex);
    return (participationFlags & flag) == flag;
  }

  @Override
  public Optional<MiscHelpersAltair> toVersionAltair() {
    return Optional.of(this);
  }
}
