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

package tech.pegasys.teku.spec;

import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.function.Consumer;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class SpecFactory {

  public static Spec create(final String configName) {
    return create(configName, false, __ -> {});
  }

  public static Spec create(
      final String configName,
      final boolean strictConfigLoadingEnabled,
      final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig(configName, strictConfigLoadingEnabled, modifier);
    return create(config);
  }

  public static Spec create(final SpecConfigAndParent<? extends SpecConfig> config) {
    final SpecConfig specConfig = config.specConfig();
    if (!specConfig.getHezeForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, HEZE);
    } else if (!specConfig.getGloasForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, GLOAS);
    } else if (!specConfig.getFuluForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, FULU);
    } else if (!specConfig.getElectraForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, ELECTRA);
    } else if (!specConfig.getDenebForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, DENEB);
    } else if (!specConfig.getCapellaForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, CAPELLA);
    } else if (!specConfig.getBellatrixForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, BELLATRIX);
    } else if (!specConfig.getAltairForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      return Spec.create(config, ALTAIR);
    }

    return Spec.create(config, PHASE0);
  }
}
