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

package tech.pegasys.teku.spec.logic.versions.deneb;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerifier;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlindBlockUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.util.BlindBlockUtilBellatrix;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.capella.statetransition.epoch.EpochProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.capella.withdrawals.WithdrawalsHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.forktransition.DenebStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.BeaconStateAccessorsDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.operations.validation.AttestationDataValidatorDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.util.AttestationUtilDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.util.ForkChoiceUtilDeneb;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator;
import tech.pegasys.teku.spec.logic.versions.phase0.util.BlockProposalUtilPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class SpecLogicDeneb extends AbstractSpecLogic {
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;
  private final Optional<LightClientUtil> lightClientUtil;
  private final Optional<WithdrawalsHelpers> withdrawalsHelpers;

  private SpecLogicDeneb(
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutatorsBellatrix beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final WeakSubjectivityCalculator weakSubjectivityCalculator,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorCapella epochProcessor,
      final WithdrawalsHelpersCapella withdrawalsHelpers,
      final BlockProcessorCapella blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtil blindBlockUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final LightClientUtil lightClientUtil,
      final DenebStateUpgrade stateUpgrade) {
    super(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        weakSubjectivityCalculator,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        Optional.of(blindBlockUtil),
        Optional.of(stateUpgrade));
    this.syncCommitteeUtil = Optional.of(syncCommitteeUtil);
    this.lightClientUtil = Optional.of(lightClientUtil);
    this.withdrawalsHelpers = Optional.of(withdrawalsHelpers);
  }

  public static SpecLogicDeneb create(
      final SpecConfigDeneb config,
      final SchemaDefinitionsDeneb schemaDefinitions,
      final TimeProvider timeProvider) {
    // Helpers
    final Predicates predicates = new Predicates(config);
    final MiscHelpersDeneb miscHelpers =
        new MiscHelpersDeneb(config, predicates, schemaDefinitions);
    final BeaconStateAccessorsDeneb beaconStateAccessors =
        new BeaconStateAccessorsDeneb(config, predicates, miscHelpers);
    final BeaconStateMutatorsBellatrix beaconStateMutators =
        new BeaconStateMutatorsBellatrix(config, miscHelpers, beaconStateAccessors);

    // Operation validation
    final OperationSignatureVerifier operationSignatureVerifier =
        new OperationSignatureVerifier(miscHelpers, beaconStateAccessors);

    // Weak subjectivity
    final WeakSubjectivityCalculator weakSubjectivityCalculator =
        new WeakSubjectivityCalculator(config, beaconStateAccessors, miscHelpers);

    // Util
    final ValidatorsUtil validatorsUtil =
        new ValidatorsUtil(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(config, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtil attestationUtil =
        new AttestationUtilDeneb(config, schemaDefinitions, beaconStateAccessors, miscHelpers);
    final AttestationDataValidator attestationDataValidator =
        new AttestationDataValidatorDeneb(config, miscHelpers, beaconStateAccessors);
    final VoluntaryExitValidator voluntaryExitValidator =
        new VoluntaryExitValidator(config, predicates, beaconStateAccessors);
    final OperationValidator operationValidator =
        new OperationValidatorCapella(
            predicates,
            beaconStateAccessors,
            attestationDataValidator,
            attestationUtil,
            voluntaryExitValidator);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(
            config,
            beaconStateUtil,
            attestationUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final EpochProcessorCapella epochProcessor =
        new EpochProcessorCapella(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory,
            schemaDefinitions,
            timeProvider);
    final SyncCommitteeUtil syncCommitteeUtil =
        new SyncCommitteeUtil(
            beaconStateAccessors, validatorsUtil, config, miscHelpers, schemaDefinitions);
    final LightClientUtil lightClientUtil =
        new LightClientUtil(
            beaconStateAccessors, syncCommitteeUtil, schemaDefinitions, miscHelpers, config);
    final WithdrawalsHelpersCapella withdrawalsHelpers =
        new WithdrawalsHelpersCapella(
            schemaDefinitions, miscHelpers, config, predicates, beaconStateMutators);
    final BlockProcessorDeneb blockProcessor =
        new BlockProcessorDeneb(
            config,
            predicates,
            miscHelpers,
            syncCommitteeUtil,
            beaconStateAccessors,
            beaconStateMutators,
            operationSignatureVerifier,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            operationValidator,
            schemaDefinitions,
            withdrawalsHelpers);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtilDeneb(
            config, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtilPhase0(schemaDefinitions, blockProcessor);

    final BlindBlockUtilBellatrix blindBlockUtil = new BlindBlockUtilBellatrix(schemaDefinitions);

    // State upgrade
    final DenebStateUpgrade stateUpgrade =
        new DenebStateUpgrade(config, schemaDefinitions, beaconStateAccessors);

    return new SpecLogicDeneb(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        weakSubjectivityCalculator,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        withdrawalsHelpers,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        blindBlockUtil,
        syncCommitteeUtil,
        lightClientUtil,
        stateUpgrade);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return syncCommitteeUtil;
  }

  @Override
  public Optional<LightClientUtil> getLightClientUtil() {
    return lightClientUtil;
  }

  @Override
  public Optional<BellatrixTransitionHelpers> getBellatrixTransitionHelpers() {
    return Optional.empty();
  }

  @Override
  public Optional<WithdrawalsHelpers> getWithdrawalsHelpers() {
    return withdrawalsHelpers;
  }

  @Override
  public Optional<ExecutionRequestsProcessor> getExecutionRequestsProcessor() {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionRequestsDataCodec> getExecutionRequestsDataCodec() {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionPayloadVerifier> getExecutionPayloadVerifier() {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionPayloadProposalUtil> getExecutionPayloadProposalUtil() {
    return Optional.empty();
  }
}
