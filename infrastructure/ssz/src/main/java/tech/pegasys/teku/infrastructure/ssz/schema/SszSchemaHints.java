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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;

/**
 * A set of hints for {@link SszSchema} classes on strategies to use for optimizing memory or/and
 * performance.
 */
public class SszSchemaHints {

  static class SszSchemaHint {}

  @Override
  public String toString() {
    return hints.stream()
        .map(hint -> hint.getClass().getSimpleName())
        .collect(Collectors.joining(", ", "(", ")"));
  }

  /**
   * Hint to use {@link SszSuperNode} for lists/vectors to save the memory when the list content is
   * expected to be rarely updated
   *
   * <p>The <code>depth</code> parameter specifies the maximum number (<code>2 ^ depth</code>) of
   * list/vector elements a single node can contain. Increasing this parameter saves memory but
   * makes list/vector update and hashTreeRoot recalculation more CPU expensive
   */
  public static final class SszSuperNodeHint extends SszSchemaHint {
    private final int depth;

    public SszSuperNodeHint(final int depth) {
      this.depth = depth;
    }

    public int getDepth() {
      return depth;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SszSuperNodeHint that)) {
        return false;
      }
      return depth == that.depth;
    }

    @Override
    public int hashCode() {
      return Integer.hashCode(depth);
    }
  }

  /**
   * Hint for fixed and progressive list-of-byte-list schemas to back the outer list with a single
   * packed node holding the serialized bytes, instead of a materialized tree. Requires the element
   * schema to be a compatible byte list schema.
   */
  public static final class SszPackedByteListsHint extends SszSchemaHint {
    @Override
    public boolean equals(final Object o) {
      return o instanceof SszPackedByteListsHint;
    }

    @Override
    public int hashCode() {
      return SszPackedByteListsHint.class.hashCode();
    }
  }

  public static SszSchemaHints of(final SszSchemaHint... hints) {
    return new SszSchemaHints(Arrays.asList(hints));
  }

  public static SszSchemaHints none() {
    return of();
  }

  public static SszSchemaHints sszSuperNode(final int superNodeDepth) {
    return of(new SszSuperNodeHint(superNodeDepth));
  }

  public static SszSchemaHints sszPackedByteLists() {
    return of(new SszPackedByteListsHint());
  }

  private final List<SszSchemaHint> hints;

  private SszSchemaHints(final List<SszSchemaHint> hints) {
    this.hints = hints;
  }

  @SuppressWarnings("unchecked")
  public <C extends SszSchemaHint> Optional<C> getHint(final Class<C> hintClass) {
    return (Optional<C>) hints.stream().filter(h -> h.getClass() == hintClass).findFirst();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszSchemaHints that)) {
      return false;
    }
    return hints.equals(that.hints);
  }

  @Override
  public int hashCode() {
    return hints.hashCode();
  }
}
