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

package tech.pegasys.teku.spec.logic.common;

import java.util.Optional;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlindBlockUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;

public abstract class AbstractSpecLogic implements SpecLogic {
  // Helpers
  protected final Predicates predicates;
  protected final MiscHelpers miscHelpers;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final BeaconStateMutators beaconStateMutators;
  // Operations
  protected final OperationSignatureVerifier operationSignatureVerifier;
  // Weak subjectivity
  protected final WeakSubjectivityCalculator weakSubjectivityCalculator;
  // Utils
  protected final ValidatorsUtil validatorsUtil;
  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final OperationValidator operationValidator;
  protected final ValidatorStatusFactory validatorStatusFactory;
  protected final EpochProcessor epochProcessor;
  protected final BlockProcessor blockProcessor;
  protected final ForkChoiceUtil forkChoiceUtil;
  protected final BlockProposalUtil blockProposalUtil;
  protected final Optional<BlindBlockUtil> blockConversionUtil;

  // State upgrade
  protected final Optional<StateUpgrade<?>> stateUpgrade;

  protected AbstractSpecLogic(
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final WeakSubjectivityCalculator weakSubjectivityCalculator,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactory validatorStatusFactory,
      final EpochProcessor epochProcessor,
      final BlockProcessor blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final Optional<BlindBlockUtil> blockConversionUtil,
      final Optional<StateUpgrade<?>> stateUpgrade) {
    this.predicates = predicates;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
    this.operationSignatureVerifier = operationSignatureVerifier;
    this.weakSubjectivityCalculator = weakSubjectivityCalculator;
    this.validatorsUtil = validatorsUtil;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.validatorStatusFactory = validatorStatusFactory;
    this.epochProcessor = epochProcessor;
    this.blockProcessor = blockProcessor;
    this.forkChoiceUtil = forkChoiceUtil;
    this.blockProposalUtil = blockProposalUtil;
    this.blockConversionUtil = blockConversionUtil;
    this.operationValidator = operationValidator;
    this.stateUpgrade = stateUpgrade;
  }

  @Override
  public Optional<StateUpgrade<?>> getStateUpgrade() {
    return stateUpgrade;
  }

  @Override
  public ValidatorsUtil getValidatorsUtil() {
    return validatorsUtil;
  }

  @Override
  public BeaconStateUtil getBeaconStateUtil() {
    return beaconStateUtil;
  }

  @Override
  public AttestationUtil getAttestationUtil() {
    return attestationUtil;
  }

  @Override
  public OperationValidator getOperationValidator() {
    return operationValidator;
  }

  @Override
  public EpochProcessor getEpochProcessor() {
    return epochProcessor;
  }

  @Override
  public BlockProcessor getBlockProcessor() {
    return blockProcessor;
  }

  @Override
  public ForkChoiceUtil getForkChoiceUtil() {
    return forkChoiceUtil;
  }

  @Override
  public BlockProposalUtil getBlockProposalUtil() {
    return blockProposalUtil;
  }

  @Override
  public Optional<BlindBlockUtil> getBlindBlockUtil() {
    return blockConversionUtil;
  }

  @Override
  public ValidatorStatusFactory getValidatorStatusFactory() {
    return validatorStatusFactory;
  }

  @Override
  public Predicates predicates() {
    return predicates;
  }

  @Override
  public MiscHelpers miscHelpers() {
    return miscHelpers;
  }

  @Override
  public BeaconStateAccessors beaconStateAccessors() {
    return beaconStateAccessors;
  }

  @Override
  public BeaconStateMutators beaconStateMutators() {
    return beaconStateMutators;
  }

  @Override
  public OperationSignatureVerifier operationSignatureVerifier() {
    return operationSignatureVerifier;
  }

  @Override
  public WeakSubjectivityCalculator weakSubjectivityCalculator() {
    return weakSubjectivityCalculator;
  }

  @Override
  public Optional<DataColumnSidecarUtil> getDataColumnSidecarUtil() {
    return Optional.empty();
  }

  @Override
  public ProposerPreferencesUtil getProposerPreferencesUtil() {
    return ProposerPreferencesUtil.NOOP;
  }
}
