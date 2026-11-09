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

package tech.pegasys.teku.spec.propertytest.suppliers;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;
import net.jqwik.api.Combinators;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class DataStructureUtilSupplier<T> implements ArbitrarySupplier<T> {
  private final Function<DataStructureUtil, T> accessor;
  private final SpecMilestone minimumSpecMilestone;
  private final SpecMilestone maximumSpecMilestone;

  protected DataStructureUtilSupplier(final Function<DataStructureUtil, T> accessor) {
    this(accessor, SpecMilestone.PHASE0);
  }

  protected DataStructureUtilSupplier(
      final Function<DataStructureUtil, T> accessor, final SpecMilestone minimumSpecMilestone) {
    this(accessor, minimumSpecMilestone, SpecMilestone.getHighestMilestone());
  }

  protected DataStructureUtilSupplier(
      final Function<DataStructureUtil, T> accessor,
      final SpecMilestone minimumSpecMilestone,
      final SpecMilestone maximumSpecMilestone) {
    this.accessor = accessor;
    this.minimumSpecMilestone = minimumSpecMilestone;
    this.maximumSpecMilestone = maximumSpecMilestone;
  }

  @Override
  public Arbitrary<T> get() {
    Arbitrary<Integer> seed = Arbitraries.integers();
    Arbitrary<Spec> spec = new SpecSupplier(minimumSpecMilestone, maximumSpecMilestone).get();
    Arbitrary<DataStructureUtil> dsu = Combinators.combine(seed, spec).as(DataStructureUtil::new);
    return dsu.map(accessor);
  }
}
