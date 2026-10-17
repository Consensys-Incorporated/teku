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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class P2PConfigTest {

  @Test
  void seenTtl_shouldCoverTwoEpochs() {
    // minimal: 8 slots per epoch, 6000 ms slots
    assertThat(seenTtl(TestSpecFactory.createMinimalGloas())).isEqualTo(Duration.ofSeconds(96));
  }

  @Test
  void seenTtl_shouldUseTheLongerSlotDurationAcrossAnEip8198Change() {
    assertThat(seenTtl(createMinimalWithHezeSlotDuration(5000))).isEqualTo(Duration.ofSeconds(96));
    assertThat(seenTtl(createMinimalWithHezeSlotDuration(7000))).isEqualTo(Duration.ofSeconds(112));
  }

  private static Spec createMinimalWithHezeSlotDuration(final int slotDurationMsHeze) {
    return TestSpecFactory.createMinimalHeze(
        builder -> builder.hezeForkEpoch(UInt64.ONE).slotDurationMsHeze(slotDurationMsHeze));
  }

  private static Duration seenTtl(final Spec spec) {
    return P2PConfig.builder()
        .specProvider(spec)
        .build()
        .getNetworkConfig()
        .getGossipConfig()
        .getSeenTTL();
  }
}
