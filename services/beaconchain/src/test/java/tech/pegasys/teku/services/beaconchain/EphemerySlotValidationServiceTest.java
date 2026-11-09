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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.networks.Eth2Network;

class EphemerySlotValidationServiceTest {
  private static final int SECONDS_PER_SLOT = 12;

  private EphemerySlotValidationService ephemerySlotValidationService;

  @BeforeEach
  void setUp() {
    ephemerySlotValidationService =
        new EphemerySlotValidationService(Optional.empty(), SECONDS_PER_SLOT);
  }

  @Test
  public void onSlot_shouldNotThrow_whenSlotIsValid() {
    ephemerySlotValidationService.onSlot(
        UInt64.valueOf(ephemerySlotValidationService.getMaxEphemerySlot()));
  }

  @Test
  public void onSlot_shouldThrowException_whenSlotExceedsMaxEphemerySlot_forEphemeryNetwork() {
    final Eth2Network network = Eth2Network.EPHEMERY;
    final Optional<Eth2Network> ephemeryNetwork = Optional.of(network);
    final long maxSlot = ephemerySlotValidationService.getMaxEphemerySlot();
    final UInt64 invalidSlot = UInt64.valueOf(maxSlot + 1);

    assertThat(ephemeryNetwork).contains(Eth2Network.EPHEMERY);
    assertThatThrownBy(() -> ephemerySlotValidationService.onSlot(invalidSlot))
        .isInstanceOf(EphemeryLifecycleException.class)
        .hasMessageContaining(
            String.format(
                "Slot %s exceeds maximum allowed slot %s for ephemery network",
                invalidSlot, maxSlot));
  }

  @Test
  public void onSlot_shouldRespectConfiguredResetPeriod() {
    final long customPeriodSeconds = 1_209_600L; // 14 days
    final EphemerySlotValidationService service =
        new EphemerySlotValidationService(
            Optional.of(UInt64.valueOf(customPeriodSeconds)), SECONDS_PER_SLOT);
    final long expectedMaxSlot = (customPeriodSeconds / SECONDS_PER_SLOT) - 1;

    assertThat(service.getMaxEphemerySlot()).isEqualTo(expectedMaxSlot);
    service.onSlot(UInt64.valueOf(expectedMaxSlot));
    assertThatThrownBy(() -> service.onSlot(UInt64.valueOf(expectedMaxSlot + 1)))
        .isInstanceOf(EphemeryLifecycleException.class);
  }

  @Test
  void shouldCompleteWhenServiceStartsAndStops() {
    final SafeFuture<?> startFuture = ephemerySlotValidationService.doStart();
    assertTrue(startFuture.isDone());
    final SafeFuture<?> stopFuture = ephemerySlotValidationService.doStop();
    assertTrue(stopFuture.isDone());
  }
}
