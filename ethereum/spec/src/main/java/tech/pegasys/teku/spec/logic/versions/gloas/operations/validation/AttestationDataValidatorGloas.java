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

package tech.pegasys.teku.spec.logic.versions.gloas.operations.validation;

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.AttestationDataValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

public class AttestationDataValidatorGloas extends AttestationDataValidatorElectra {

  public AttestationDataValidatorGloas(
      final SpecConfigGloas specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    super(specConfig, miscHelpers, beaconStateAccessors);
  }

  /**
   * <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/beacon-chain.md#modified-process_attestation">Modified
   * process_attestation</a>
   */
  @Override
  protected Optional<OperationInvalidReason> checkCommitteeIndex(final AttestationData data) {
    return check(
        // signalling payload availability
        data.getIndex().isLessThan(2),
        AttestationInvalidReason.COMMITTEE_INDEX_MUST_BE_LESS_THAN_TWO);
  }
}
