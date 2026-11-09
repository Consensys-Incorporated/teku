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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.SAVE_FOR_FUTURE;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

public class DenebAttestationValidatorTest extends AbstractAttestationValidatorTest {

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalDeneb(configAdapter);
  }

  @Test
  public void shouldAcceptValidAttestation() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    chainUpdater.setCurrentSlot(UInt64.valueOf(1));

    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldIgnoreAttestationAfterCurrentSlot() {
    // attestation will be fork choice eligible in 12 slots, so more than MAX_FUTURE_SLOT_ALLOWANCE
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead(), UInt64.valueOf(11));

    chainUpdater.setCurrentSlot(ZERO);

    assertThat(validate(attestation).code()).isEqualTo(IGNORE);
  }

  @Test
  public void shouldIgnoreAttestationNotInCurrentOrPreviousEpoch() {
    // attestation in epoch 0
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    // current epoch is 3
    chainUpdater.setCurrentSlot(UInt64.valueOf(25));

    assertThat(validate(attestation).code()).isEqualTo(IGNORE);
  }

  @Test
  public void shouldDeferAttestationAfterCurrentSlotButNotTooFarInTheFuture() {
    // attestation will be fork choice eligible in 3 slots, so within MAX_FUTURE_SLOT_ALLOWANCE
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead(), UInt64.valueOf(2));

    chainUpdater.setCurrentSlot(ZERO);

    assertThat(validate(attestation).code()).isEqualTo(SAVE_FOR_FUTURE);
  }
}
