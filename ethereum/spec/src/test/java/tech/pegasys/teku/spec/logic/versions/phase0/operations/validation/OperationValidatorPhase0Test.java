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

package tech.pegasys.teku.spec.logic.versions.phase0.operations.validation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;

public class OperationValidatorPhase0Test {

  @Test
  void shouldRejectBlsOperationsPhase0() {
    final Predicates predicates = mock(Predicates.class);
    final BeaconStateAccessors beaconStateAccessors = mock(BeaconStateAccessors.class);
    final AttestationDataValidator attestationDataValidator = mock(AttestationDataValidator.class);
    final AttestationUtil attestationUtil = mock(AttestationUtil.class);
    final VoluntaryExitValidator voluntaryExitValidator = mock(VoluntaryExitValidator.class);
    final OperationValidator operationValidator =
        new OperationValidatorPhase0(
            predicates,
            beaconStateAccessors,
            attestationDataValidator,
            attestationUtil,
            voluntaryExitValidator);

    assertThat(
            operationValidator.validateBlsToExecutionChange(
                mock(Fork.class), mock(BeaconState.class), mock(BlsToExecutionChange.class)))
        .containsInstanceOf(OperationInvalidReason.class);
    verifyNoInteractions(predicates);
    verifyNoInteractions(beaconStateAccessors);
    verifyNoInteractions(attestationDataValidator);
    verifyNoInteractions(attestationUtil);
    verifyNoInteractions(voluntaryExitValidator);
  }
}
