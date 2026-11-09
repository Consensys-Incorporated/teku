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

import com.google.common.base.Objects;

/**
 * Pair of values
 *
 * <p>Unlike {@link org.apache.commons.lang3.tuple.Pair} that version is not bounded by the {@link
 * java.util.Map.Entry#hashCode()} contract with weak hashing algorithm
 */
public final class TekuPair<TLeft, TRight> {

  public static <TLeft, TRight> TekuPair<TLeft, TRight> of(final TLeft left, final TRight right) {
    return new TekuPair<>(left, right);
  }

  private final TLeft left;
  private final TRight right;

  private TekuPair(final TLeft left, final TRight right) {
    this.left = left;
    this.right = right;
  }

  public TLeft getLeft() {
    return left;
  }

  public TRight getRight() {
    return right;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TekuPair<?, ?> tekuPair)) {
      return false;
    }
    return Objects.equal(getLeft(), tekuPair.getLeft())
        && Objects.equal(getRight(), tekuPair.getRight());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getLeft(), getRight());
  }

  @Override
  public String toString() {
    return "TekuPair{" + left + ", " + right + '}';
  }
}
