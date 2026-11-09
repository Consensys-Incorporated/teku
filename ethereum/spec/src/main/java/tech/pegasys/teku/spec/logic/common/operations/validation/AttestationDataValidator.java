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

package tech.pegasys.teku.spec.logic.common.operations.validation;

import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

public interface AttestationDataValidator
    extends OperationStateTransitionValidator<AttestationData> {

  enum AttestationInvalidReason implements OperationInvalidReason {
    COMMITTEE_INDEX_TOO_HIGH("CommitteeIndex too high"),
    COMMITTEE_INDEX_MUST_BE_ZERO("CommitteeIndex must be set to zero"),
    COMMITTEE_INDEX_MUST_BE_LESS_THAN_TWO(
        "CommitteeIndex (used for payload availability) must be less than two"),
    PARTICIPANTS_COUNT_MISMATCH("Attesting participants count do not match aggregation bits"),
    NOT_FROM_CURRENT_OR_PREVIOUS_EPOCH("Attestation not from current or previous epoch"),
    SLOT_NOT_IN_EPOCH("Attestation slot not in specified epoch"),
    SUBMITTED_TOO_QUICKLY("Attestation submitted too quickly"),
    SUBMITTED_TOO_LATE("Attestation submitted too late"),
    INCORRECT_CURRENT_JUSTIFIED_CHECKPOINT(
        "Attestation source does not match current justified checkpoint"),
    INCORRECT_PREVIOUS_JUSTIFIED_CHECKPOINT(
        "Attestation source does not match previous justified checkpoint");

    private final String description;

    AttestationInvalidReason(final String description) {
      this.description = description;
    }

    @Override
    public String describe() {
      return description;
    }
  }
}
