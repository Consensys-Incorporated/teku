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

package tech.pegasys.teku.spec.datastructures.operations;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class IntersectingIndicesCalculator {
  private final Supplier<Set<UInt64>> intersectingIndices;

  public IntersectingIndicesCalculator(final AttesterSlashing attesterSlashing) {
    this.intersectingIndices =
        Suppliers.memoize(() -> calculateIntersectionIndices(attesterSlashing));
  }

  private static Set<UInt64> calculateIntersectionIndices(final AttesterSlashing attesterSlashing) {
    return Sets.intersection(
        new TreeSet<>(
            attesterSlashing
                .getAttestation1()
                .getAttestingIndices()
                .asListUnboxed()), // TreeSet as must be sorted
        new HashSet<>(attesterSlashing.getAttestation2().getAttestingIndices().asListUnboxed()));
  }

  public Set<UInt64> getIntersectingValidatorIndices() {
    return intersectingIndices.get();
  }
}
