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

package tech.pegasys.teku.spec.constants;

public class ParticipationFlags {
  public static final int TIMELY_SOURCE_FLAG_INDEX = 0;
  public static final int TIMELY_TARGET_FLAG_INDEX = 1;
  public static final int TIMELY_HEAD_FLAG_INDEX = 2;

  public static final byte NO_PARTICIPATION_FLAGS = (byte) 0b0000_0000;

  public static final int TIMELY_SOURCE_FLAG = indexToFlag(TIMELY_SOURCE_FLAG_INDEX);
  public static final int TIMELY_TARGET_FLAG = indexToFlag(TIMELY_TARGET_FLAG_INDEX);
  public static final int TIMELY_HEAD_FLAG = indexToFlag(TIMELY_HEAD_FLAG_INDEX);

  private static final int ALL_FLAGS =
      combineFlags(TIMELY_HEAD_FLAG, TIMELY_SOURCE_FLAG, TIMELY_TARGET_FLAG);

  public static boolean isTimelyTarget(final int value) {
    return checkIfAnyFlagIsSet(value, TIMELY_TARGET_FLAG);
  }

  public static boolean isAnyFlagSet(final int value) {
    return checkIfAnyFlagIsSet(value, ALL_FLAGS);
  }

  public static int indexToFlag(final int flagIndex) {
    return 1 << flagIndex;
  }

  private static boolean checkIfAnyFlagIsSet(final int value, final int flags) {
    return (value & flags) != 0;
  }

  private static int combineFlags(final int... flags) {
    int combined = 0;
    for (int flag : flags) {
      combined = combined | flag;
    }
    return combined;
  }
}
