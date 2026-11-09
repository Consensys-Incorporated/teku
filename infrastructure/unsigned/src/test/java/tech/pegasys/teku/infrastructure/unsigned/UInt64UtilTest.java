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

package tech.pegasys.teku.infrastructure.unsigned;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UInt64UtilTest {
  @Test
  void shouldConvertIntegerListToUInt64() {
    assertThat(UInt64Util.intToUInt64List(IntList.of(1, 2, 3)))
        .containsExactly(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3));
  }

  @Test
  void shouldConvertLongListToUInt64() {
    assertThat(UInt64Util.longToUInt64List(LongList.of(1L, 2L, 3L)))
        .containsExactly(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3));
  }

  @Test
  void shouldConvertStringListToUInt64() {
    assertThat(UInt64Util.stringToUInt64List(List.of("1", "2", "3")))
        .containsExactly(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3));
  }
}
