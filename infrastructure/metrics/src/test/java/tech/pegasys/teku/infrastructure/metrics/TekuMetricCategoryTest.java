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

package tech.pegasys.teku.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.DISCOVERY;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EVENTBUS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EXECUTOR;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR_DUTY;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR_PERFORMANCE;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TekuMetricCategoryTest {

  @Test
  public void shouldProvideExpectedDefaultCategories() {
    final Set<TekuMetricCategory> expectedDefaultCategories =
        Set.of(
            BEACON,
            DISCOVERY,
            EVENTBUS,
            EXECUTOR,
            LIBP2P,
            NETWORK,
            STORAGE,
            STORAGE_HOT_DB,
            STORAGE_FINALIZED_DB,
            VALIDATOR,
            VALIDATOR_PERFORMANCE,
            VALIDATOR_DUTY);

    assertThat(TekuMetricCategory.defaultCategories()).containsAll(expectedDefaultCategories);
  }

  @Test
  public void shouldNotEnableExperimentalGossipMetricsByDefault() {
    assertThat(TekuMetricCategory.defaultCategories())
        .doesNotContain(TekuMetricCategory.LIBP2P_GOSSIP);
  }

  @Test
  public void experimentalGossipCategoryShouldNotShareANameWithLibp2p() {
    // Besu matches categories by name, so a shared name would make LIBP2P enable this one too.
    assertThat(TekuMetricCategory.LIBP2P_GOSSIP.getName())
        .isNotEqualTo(TekuMetricCategory.LIBP2P.getName());
  }
}
