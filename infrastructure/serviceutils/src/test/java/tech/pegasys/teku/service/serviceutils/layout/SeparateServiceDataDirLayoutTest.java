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

package tech.pegasys.teku.service.serviceutils.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout.BEACON_DATA_DIR_NAME;
import static tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout.BOOTNODE_DATA_DIR_NAME;
import static tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout.VALIDATOR_DATA_DIR_NAME;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeparateServiceDataDirLayoutTest {
  @TempDir Path tempDir;

  private SeparateServiceDataDirLayout layout;

  @BeforeEach
  void setUp() {
    layout =
        new SeparateServiceDataDirLayout(
            tempDir, Optional.empty(), Optional.empty(), Optional.empty(), false);
  }

  @Test
  void shouldUseDefaultBeaconDataDirectory() {
    assertThat(layout.getBeaconDataDirectory()).isEqualTo(tempDir.resolve(BEACON_DATA_DIR_NAME));
  }

  @Test
  void shouldUseDefaultBootnodeDataDirectory() {
    assertThat(layout.getBootnodeDataDirectory())
        .isEqualTo(tempDir.resolve(BOOTNODE_DATA_DIR_NAME));
  }

  @Test
  void shouldUseDefaultValidatorDataDirectory() {
    assertThat(layout.getValidatorDataDirectory())
        .isEqualTo(tempDir.resolve(VALIDATOR_DATA_DIR_NAME));
  }
}
