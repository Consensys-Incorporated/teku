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

package tech.pegasys.teku.networking.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

class DiscoveryConfigTest {
  @Test
  void minRandomlySelectedPeers_shouldNotExceedMaxPeers() {
    assertThatThrownBy(
            () -> DiscoveryConfig.builder().maxPeers(10).minRandomlySelectedPeers(11).build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Invalid minRandomlySelectedPeers: 11 exceeds maxPeers: 10");
  }
}
