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

package tech.pegasys.teku.reference;

import com.google.common.collect.Maps;
import java.util.Map;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.NoOpKZG;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class KzgRetriever {

  private static final Map<String, String> TRUSTED_SETUP_FILES_BY_NETWORK = Maps.newHashMap();

  public static KZG getKzgWithLoadedTrustedSetup(final Spec spec, final String network) {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)
        || spec.isMilestoneSupported(SpecMilestone.ELECTRA)) {
      return getKzgWithLoadedTrustedSetup(network);
    }
    return NoOpKZG.INSTANCE;
  }

  public static KZG getKzgWithLoadedTrustedSetup(final String network) {
    final String trustedSetupFile =
        TRUSTED_SETUP_FILES_BY_NETWORK.computeIfAbsent(
            network,
            __ ->
                Eth2NetworkConfiguration.builder(network)
                    .build()
                    .getTrustedSetup()
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "No trusted setup configured for " + network)));
    final KZG kzg = KZG.getInstance(false);
    kzg.loadTrustedSetup(trustedSetupFile, 0);
    return kzg;
  }
}
