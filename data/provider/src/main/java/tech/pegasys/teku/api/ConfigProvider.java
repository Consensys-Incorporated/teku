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

package tech.pegasys.teku.api;

import java.util.List;
import java.util.Map;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class ConfigProvider {
  final Spec spec;

  public ConfigProvider(final Spec spec) {
    this.spec = spec;
  }

  public Map<String, Object> getConfig() {
    final SpecConfigData configuration =
        new SpecConfigData(spec.getSpecConfigAndParent().specConfig());
    return configuration.getConfigMap();
  }

  public SpecConfig getSpecConfig() {
    return spec.getSpecConfigAndParent().specConfig();
  }

  public static String formatValue(final Object v) {
    if (v instanceof UInt256) {
      return ((UInt256) v).toDecimalString();
    }
    if (v == null) {
      return null;
    }
    return v.toString();
  }

  public List<Fork> getStateForkSchedule() {
    return spec.getForkSchedule().getFullForkList();
  }

  public SpecConfig getGenesisSpecConfig() {
    return spec.getGenesisSpecConfig();
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return spec.computeEpochAtSlot(slot);
  }
}
