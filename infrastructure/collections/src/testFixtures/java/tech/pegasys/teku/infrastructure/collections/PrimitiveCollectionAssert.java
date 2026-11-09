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

package tech.pegasys.teku.infrastructure.collections;

import it.unimi.dsi.fastutil.booleans.BooleanCollection;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Collection;
import java.util.List;
import org.assertj.core.api.AbstractCollectionAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;

public class PrimitiveCollectionAssert {
  public static ListAssert<Integer> assertThatIntCollection(final IntList collection) {
    return Assertions.assertThat((List<Integer>) collection);
  }

  public static AbstractCollectionAssert<
          ?, Collection<? extends Integer>, Integer, ObjectAssert<Integer>>
      assertThatIntCollection(final IntCollection collection) {
    return Assertions.assertThat(collection);
  }

  public static ListAssert<Long> assertThatLongCollection(final LongList collection) {
    return Assertions.assertThat((List<Long>) collection);
  }

  public static AbstractCollectionAssert<?, Collection<? extends Long>, Long, ObjectAssert<Long>>
      assertThatLongCollection(final LongCollection collection) {
    return Assertions.assertThat(collection);
  }

  public static ListAssert<Boolean> assertThatBooleanCollection(final BooleanList collection) {
    return Assertions.assertThat((List<Boolean>) collection);
  }

  public static AbstractCollectionAssert<
          ?, Collection<? extends Boolean>, Boolean, ObjectAssert<Boolean>>
      assertThatBooleanCollection(final BooleanCollection collection) {
    return Assertions.assertThat(collection);
  }
}
