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

package tech.pegasys.teku.cli.options;

import static com.google.common.base.Preconditions.checkNotNull;

import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networking.nat.NatConfiguration;
import tech.pegasys.teku.networking.nat.NatMethod;

public class NatOptions {

  @CommandLine.Option(
      names = {"--p2p-nat-method"},
      description =
          "Specify the NAT circumvention method to be used, possible values are ${COMPLETION-CANDIDATES}."
              + " NONE will require manual router configuration.")
  private final NatMethod natMethod = NatMethod.NONE;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.natConfig(natBuilder -> natBuilder.natMethod(natMethod));
  }

  public NatConfiguration getNetworkConfiguration() {
    checkNotNull(natMethod);
    return NatConfiguration.builder().natMethod(natMethod).build();
  }
}
