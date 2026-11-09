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

package tech.pegasys.teku.spec.logic;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerifier;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
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
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;

public class DelegatingSpecLogic implements SpecLogic {
  private final SpecLogic specLogic;

  public DelegatingSpecLogic(final SpecLogic specLogic) {
    this.specLogic = specLogic;
  }

  @Override
  public Optional<StateUpgrade<?>> getStateUpgrade() {
    return specLogic.getStateUpgrade();
  }

  @Override
  public ValidatorsUtil getValidatorsUtil() {
    return specLogic.getValidatorsUtil();
  }

  @Override
  public BeaconStateUtil getBeaconStateUtil() {
    return specLogic.getBeaconStateUtil();
  }

  @Override
  public AttestationUtil getAttestationUtil() {
    return specLogic.getAttestationUtil();
  }

  @Override
  public OperationValidator getOperationValidator() {
    return specLogic.getOperationValidator();
  }

  @Override
  public EpochProcessor getEpochProcessor() {
    return specLogic.getEpochProcessor();
  }

  @Override
  public BlockProcessor getBlockProcessor() {
    return specLogic.getBlockProcessor();
  }

  @Override
  public ForkChoiceUtil getForkChoiceUtil() {
    return specLogic.getForkChoiceUtil();
  }

  @Override
  public BlockProposalUtil getBlockProposalUtil() {
    return specLogic.getBlockProposalUtil();
  }

  @Override
  public Optional<BlindBlockUtil> getBlindBlockUtil() {
    return specLogic.getBlindBlockUtil();
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return specLogic.getSyncCommitteeUtil();
  }

  @Override
  public Optional<LightClientUtil> getLightClientUtil() {
    return specLogic.getLightClientUtil();
  }

  @Override
  public ValidatorStatusFactory getValidatorStatusFactory() {
    return specLogic.getValidatorStatusFactory();
  }

  @Override
  public Optional<BellatrixTransitionHelpers> getBellatrixTransitionHelpers() {
    return specLogic.getBellatrixTransitionHelpers();
  }

  @Override
  public Optional<WithdrawalsHelpers> getWithdrawalsHelpers() {
    return specLogic.getWithdrawalsHelpers();
  }

  @Override
  public Optional<ExecutionRequestsProcessor> getExecutionRequestsProcessor() {
    return specLogic.getExecutionRequestsProcessor();
  }

  @Override
  public Optional<ExecutionRequestsDataCodec> getExecutionRequestsDataCodec() {
    return specLogic.getExecutionRequestsDataCodec();
  }

  @Override
  public Optional<ExecutionPayloadVerifier> getExecutionPayloadVerifier() {
    return specLogic.getExecutionPayloadVerifier();
  }

  @Override
  public Optional<ExecutionPayloadProposalUtil> getExecutionPayloadProposalUtil() {
    return specLogic.getExecutionPayloadProposalUtil();
  }

  @Override
  public Predicates predicates() {
    return specLogic.predicates();
  }

  @Override
  public MiscHelpers miscHelpers() {
    return specLogic.miscHelpers();
  }

  @Override
  public BeaconStateAccessors beaconStateAccessors() {
    return specLogic.beaconStateAccessors();
  }

  @Override
  public BeaconStateMutators beaconStateMutators() {
    return specLogic.beaconStateMutators();
  }

  @Override
  public OperationSignatureVerifier operationSignatureVerifier() {
    return specLogic.operationSignatureVerifier();
  }

  @Override
  public WeakSubjectivityCalculator weakSubjectivityCalculator() {
    return specLogic.weakSubjectivityCalculator();
  }

  @Override
  public Optional<DataColumnSidecarUtil> getDataColumnSidecarUtil() {
    return specLogic.getDataColumnSidecarUtil();
  }

  @Override
  public ProposerPreferencesUtil getProposerPreferencesUtil() {
    return specLogic.getProposerPreferencesUtil();
  }
}
