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

import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public enum TekuMetricCategory implements MetricCategory {
  BEACON("beacon"),
  DISCOVERY("discovery"),
  EVENTBUS("eventbus"),
  EXECUTOR("executor"),
  LIBP2P("libp2p"),
  /**
   * Experimental gossipsub router internals. Deliberately absent from {@link #defaultCategories()}.
   * Eventually will be promoted to "regular" LIBP2P.
   */
  LIBP2P_GOSSIP("libp2p_gossip"),
  NETWORK("network"),
  STORAGE("storage"),
  STORAGE_HOT_DB("storage_hot"),
  STORAGE_FINALIZED_DB("storage_finalized"),
  VALIDATOR("validator"),
  VALIDATOR_PERFORMANCE("validator_performance"),
  VALIDATOR_DUTY("validator_duty");

  private final String name;

  TekuMetricCategory(final String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Optional<String> getApplicationPrefix() {
    return Optional.empty();
  }

  public static Set<TekuMetricCategory> defaultCategories() {
    return Set.of(
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
  }
}
