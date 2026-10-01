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

package tech.pegasys.teku.spec.propertytest.suppliers;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;
import net.jqwik.api.Combinators;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecSupplier implements ArbitrarySupplier<Spec> {
  private final SpecMilestone minimumSpecMilestone;
  private final SpecMilestone maximumSpecMilestone;

  @SuppressWarnings("unused")
  public SpecSupplier() {
    this(SpecMilestone.PHASE0);
  }

  public SpecSupplier(final SpecMilestone minimumSpecMilestone) {
    this(minimumSpecMilestone, SpecMilestone.getHighestMilestone());
  }

  public SpecSupplier(
      final SpecMilestone minimumSpecMilestone, final SpecMilestone maximumSpecMilestone) {
    this.minimumSpecMilestone = minimumSpecMilestone;
    this.maximumSpecMilestone = maximumSpecMilestone;
  }

  @Override
  public Arbitrary<Spec> get() {
    Arbitrary<SpecMilestone> milestone =
        Arbitraries.of(SpecMilestone.class)
            .filter(m -> m.isGreaterThanOrEqualTo(minimumSpecMilestone))
            .filter(m -> m.isLessThanOrEqualTo(maximumSpecMilestone));
    // Ephemery loads its config from a remote URL, which would be fetched for every generated spec
    Arbitrary<Eth2Network> network =
        Arbitraries.of(Eth2Network.class).filter(n -> n != Eth2Network.EPHEMERY);
    return Combinators.combine(milestone, network)
        .as(TestSpecFactory::create)
        // Not all network and milestone combinations create a valid config
        .ignoreException(IllegalArgumentException.class);
  }
}
