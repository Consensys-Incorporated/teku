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

package tech.pegasys.teku.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

public class StartupLogConfigTest {

  private static final int NETWORK_REPORT_INDEX = 0;
  private static final int HARDWARE_REPORT_INDEX = 1;
  private static final int BEACON_API_REPORT_INDEX = 2;
  private static final int VALIDATOR_API_REPORT_INDEX = 3;

  @Test
  public void checkNetworkAndStorageModeReport() {
    final StartupLogConfig startupLogConfig =
        startupLogConfigBuilder().network("mainnet").storageMode("PRUNE").build();

    final List<String> report = startupLogConfig.getReport();

    assertThat(report.get(NETWORK_REPORT_INDEX))
        .isEqualTo("Configuration | Network: mainnet, Storage Mode: PRUNE");
  }

  @Test
  public void checkHardwareReport() {
    final HardwareAbstractionLayer hardwareInfo = mock(HardwareAbstractionLayer.class);
    final GlobalMemory memory = mock(GlobalMemory.class);
    when(hardwareInfo.getMemory()).thenReturn(memory);
    when(memory.getTotal()).thenReturn(Long.valueOf("17179869184"));

    final CentralProcessor centralProcessor = mock(CentralProcessor.class);
    when(hardwareInfo.getProcessor()).thenReturn(centralProcessor);
    when(centralProcessor.getLogicalProcessorCount()).thenReturn(12);

    final StartupLogConfig startupLogConfig =
        startupLogConfigBuilder().hardwareInfo(hardwareInfo).maxHeapSize(1_073_741_824L).build();

    final List<String> report = startupLogConfig.getReport();

    assertThat(report.get(HARDWARE_REPORT_INDEX))
        .isEqualTo(
            "Host Configuration | Maximum Heap Size: 1.00 GB, Total Memory: 16.00 GB, CPU Cores: 12");
  }

  @Test
  public void checkReportWithBeaconRestApiEnabled() {
    final StartupLogConfig startupLogConfig =
        startupLogConfigBuilder()
            .beaconChainRestApiEnabled(true)
            .beaconChainRestApiInterface("127.0.0.1")
            .beaconChainRestApiPort(5678)
            .beaconChainRestApiAllow(List.of("127.0.0.1", "localhost"))
            .build();

    final List<String> report = startupLogConfig.getReport();

    assertThat(report.get(BEACON_API_REPORT_INDEX))
        .isEqualTo(
            "Rest Api Configuration | Enabled: true, Listen Address: 127.0.0.1, Port: 5678, Allow: [127.0.0.1, localhost]");
  }

  @Test
  public void checkReportWithValidatorRestApiDisabled() {
    final StartupLogConfig startupLogConfig =
        startupLogConfigBuilder().beaconChainRestApiEnabled(false).build();

    final List<String> report = startupLogConfig.getReport();

    assertThat(report.get(BEACON_API_REPORT_INDEX))
        .isEqualTo("Rest Api Configuration | Enabled: false");
  }

  @Test
  public void checkReportWithValidatorRestApiEnabled() {
    final StartupLogConfig startupLogConfig =
        startupLogConfigBuilder()
            .validatorRestApiEnabled(true)
            .validatorRestApiInterface("127.0.0.1")
            .validatorRestApiPort(5679)
            .validatorRestApiAllow(List.of("127.0.0.1", "localhost"))
            .build();

    final List<String> report = startupLogConfig.getReport();

    assertThat(report.get(VALIDATOR_API_REPORT_INDEX))
        .isEqualTo(
            "Validator Api Configuration | Enabled: true, Listen Address: 127.0.0.1, Port: 5679, Allow: [127.0.0.1, "
                + "localhost]");
  }

  @Test
  public void checkReportWithBeaconRestApiDisabled() {
    final StartupLogConfig startupLogConfig =
        startupLogConfigBuilder().validatorRestApiEnabled(false).build();

    final List<String> report = startupLogConfig.getReport();

    assertThat(report.get(VALIDATOR_API_REPORT_INDEX))
        .isEqualTo("Validator Api Configuration | Enabled: false");
  }

  private StartupLogConfig.Builder startupLogConfigBuilder() {
    final HardwareAbstractionLayer hardwareInfo = mock(HardwareAbstractionLayer.class);
    final GlobalMemory memory = mock(GlobalMemory.class);
    when(hardwareInfo.getMemory()).thenReturn(memory);
    when(memory.getTotal()).thenReturn(Long.valueOf("123"));

    final CentralProcessor centralProcessor = mock(CentralProcessor.class);
    when(hardwareInfo.getProcessor()).thenReturn(centralProcessor);
    when(centralProcessor.getLogicalProcessorCount()).thenReturn(2);

    return StartupLogConfig.builder()
        .network("mainnet")
        .storageMode("PRUNE")
        .hardwareInfo(hardwareInfo)
        .maxHeapSize(4_000_000_000L)
        .beaconChainRestApiEnabled(true)
        .beaconChainRestApiInterface("127.0.0.1")
        .beaconChainRestApiPort(5678)
        .beaconChainRestApiAllow(List.of("127.0.0.1", "localhost"))
        .validatorRestApiEnabled(true)
        .validatorRestApiInterface("127.0.0.1")
        .validatorRestApiPort(6789)
        .validatorRestApiAllow(List.of("127.0.0.1", "localhost"));
  }
}
