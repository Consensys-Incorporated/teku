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

public interface SpecLogic {
  Optional<StateUpgrade<?>> getStateUpgrade();

  ValidatorsUtil getValidatorsUtil();

  BeaconStateUtil getBeaconStateUtil();

  AttestationUtil getAttestationUtil();

  OperationValidator getOperationValidator();

  EpochProcessor getEpochProcessor();

  BlockProcessor getBlockProcessor();

  ForkChoiceUtil getForkChoiceUtil();

  BlockProposalUtil getBlockProposalUtil();

  Optional<BlindBlockUtil> getBlindBlockUtil();

  Optional<SyncCommitteeUtil> getSyncCommitteeUtil();

  Optional<LightClientUtil> getLightClientUtil();

  ValidatorStatusFactory getValidatorStatusFactory();

  Predicates predicates();

  MiscHelpers miscHelpers();

  BeaconStateAccessors beaconStateAccessors();

  BeaconStateMutators beaconStateMutators();

  OperationSignatureVerifier operationSignatureVerifier();

  WeakSubjectivityCalculator weakSubjectivityCalculator();

  Optional<BellatrixTransitionHelpers> getBellatrixTransitionHelpers();

  Optional<WithdrawalsHelpers> getWithdrawalsHelpers();

  Optional<ExecutionRequestsProcessor> getExecutionRequestsProcessor();

  Optional<ExecutionRequestsDataCodec> getExecutionRequestsDataCodec();

  Optional<ExecutionPayloadVerifier> getExecutionPayloadVerifier();

  Optional<ExecutionPayloadProposalUtil> getExecutionPayloadProposalUtil();

  Optional<DataColumnSidecarUtil> getDataColumnSidecarUtil();

  ProposerPreferencesUtil getProposerPreferencesUtil();
}
