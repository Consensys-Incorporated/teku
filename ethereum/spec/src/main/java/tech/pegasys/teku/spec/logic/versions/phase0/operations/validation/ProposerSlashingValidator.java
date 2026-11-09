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

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationStateTransitionValidator;

public class ProposerSlashingValidator
    implements OperationStateTransitionValidator<ProposerSlashing> {

  private final Predicates predicates;
  private final BeaconStateAccessors beaconStateAccessors;

  ProposerSlashingValidator(
      final Predicates predicates, final BeaconStateAccessors beaconStateAccessors) {
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final ProposerSlashing proposerSlashing) {
    final BeaconBlockHeader header1 = proposerSlashing.getHeader1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader2().getMessage();
    return firstOf(
        () ->
            check(
                header1.getSlot().equals(header2.getSlot()),
                ProposerSlashingInvalidReason.HEADER_SLOTS_DIFFERENT),
        () ->
            check(
                header1.getProposerIndex().equals(header2.getProposerIndex()),
                ProposerSlashingInvalidReason.PROPOSER_INDICES_DIFFERENT),
        () -> check(!Objects.equals(header1, header2), ProposerSlashingInvalidReason.SAME_HEADER),
        () ->
            check(
                UInt64.valueOf(state.getValidators().size()).compareTo(header1.getProposerIndex())
                    > 0,
                ProposerSlashingInvalidReason.INVALID_PROPOSER),
        () ->
            check(
                predicates.isSlashableValidator(
                    state.getValidators().get(header1.getProposerIndex().intValue()),
                    beaconStateAccessors.getCurrentEpoch(state)),
                ProposerSlashingInvalidReason.PROPOSER_NOT_SLASHABLE));
  }

  public enum ProposerSlashingInvalidReason implements OperationInvalidReason {
    HEADER_SLOTS_DIFFERENT("Header slots don't match"),
    PROPOSER_INDICES_DIFFERENT("Header proposer indices don't match"),
    SAME_HEADER("Headers are not different"),
    INVALID_PROPOSER("Invalid proposer index"),
    PROPOSER_NOT_SLASHABLE("Proposer is not slashable"),
    INVALID_SIGNATURE("Slashing fails signature verification");

    private final String description;

    ProposerSlashingInvalidReason(final String description) {
      this.description = description;
    }

    @Override
    public String describe() {
      return description;
    }
  }
}
