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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.storage.store.StoreConfig;

public class StoreOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void hotStatePersistenceFrequency_shouldRespectCLIArg() {
    final String[] args = {
      "--Xhot-state-persistence-frequency", "99",
    };
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments(args);
    final StoreConfig globalConfiguration = tekuConfiguration.beaconChain().storeConfig();
    assertThat(globalConfiguration.getHotStatePersistenceFrequencyInEpochs()).isEqualTo(99);
  }

  @Test
  public void hotStatePersistenceFrequency_shouldSetDefaultValue() {
    final StoreConfig globalConfiguration =
        getTekuConfigurationFromArguments().beaconChain().storeConfig();
    assertThat(globalConfiguration.getHotStatePersistenceFrequencyInEpochs()).isEqualTo(2);
  }

  @Test
  public void hotStatePersistenceFrequency_invalidNumber() {
    final String[] args = {
      "--Xhot-state-persistence-frequency", "1.5",
    };
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();

    assertThat(output).isNotEmpty();
    assertThat(output).contains("Invalid value");
  }

  @Test
  public void hotStatePersistenceFrequency_missingValue() {
    final String[] args = {
      "--Xhot-state-persistence-frequency", "",
    };
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();

    assertThat(output).isNotEmpty();
    assertThat(output).contains("Invalid value");
  }
}
