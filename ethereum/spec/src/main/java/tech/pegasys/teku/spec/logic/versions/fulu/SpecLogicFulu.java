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

package tech.pegasys.teku.spec.logic.versions.fulu;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
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
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.AttestationDataValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.VoluntaryExitValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.statetransition.epoch.EpochProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.logic.versions.electra.weaksubjectivity.WeakSubjectivityCalculatorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.withdrawals.WithdrawalsHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.block.BlockProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.execution.ExecutionRequestsProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.forktransition.FuluStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.statetransition.epoch.EpochProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlindBlockUtilFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlockProposalUtilFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.DataColumnSidecarUtilFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.ForkChoiceUtilFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class SpecLogicFulu extends AbstractSpecLogic {
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;
  private final Optional<LightClientUtil> lightClientUtil;
  private final Optional<WithdrawalsHelpers> withdrawalsHelpers;
  private final Optional<ExecutionRequestsProcessor> executionRequestsProcessor;
  private final Optional<ExecutionRequestsDataCodec> executionRequestsDataCodec;
  private final Optional<DataColumnSidecarUtil> dataColumnSidecarUtil;

  private SpecLogicFulu(
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final BeaconStateMutatorsBellatrix beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final WeakSubjectivityCalculator weakSubjectivityCalculator,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorElectra epochProcessor,
      final WithdrawalsHelpersElectra withdrawalsHelpers,
      final ExecutionRequestsProcessorElectra executionRequestsProcessor,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final BlockProcessorFulu blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtil blindBlockUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final LightClientUtil lightClientUtil,
      final FuluStateUpgrade stateUpgrade,
      final DataColumnSidecarUtil dataColumnSidecarUtil) {
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
    this.executionRequestsProcessor = Optional.of(executionRequestsProcessor);
    this.executionRequestsDataCodec = Optional.of(executionRequestsDataCodec);
    this.dataColumnSidecarUtil = Optional.of(dataColumnSidecarUtil);
  }

  public static SpecLogicFulu create(
      final SpecConfigFulu config,
      final SchemaDefinitionsFulu schemaDefinitions,
      final TimeProvider timeProvider) {
    // Helpers
    final PredicatesElectra predicates = new PredicatesElectra(config);
    final MiscHelpersFulu miscHelpers = new MiscHelpersFulu(config, predicates, schemaDefinitions);
    final BeaconStateAccessorsFulu beaconStateAccessors =
        new BeaconStateAccessorsFulu(config, predicates, miscHelpers);
    final BeaconStateMutatorsElectra beaconStateMutators =
        new BeaconStateMutatorsElectra(
            config, miscHelpers, beaconStateAccessors, schemaDefinitions);

    // Operation validation
    final OperationSignatureVerifier operationSignatureVerifier =
        new OperationSignatureVerifier(miscHelpers, beaconStateAccessors);

    // Weak subjectivity
    final WeakSubjectivityCalculatorElectra weakSubjectivityCalculator =
        new WeakSubjectivityCalculatorElectra(config, beaconStateAccessors, miscHelpers);

    // Util
    final ValidatorsUtil validatorsUtil =
        new ValidatorsUtil(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(config, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtil attestationUtil =
        new AttestationUtilElectra(config, schemaDefinitions, beaconStateAccessors, miscHelpers);
    final AttestationDataValidator attestationDataValidator =
        new AttestationDataValidatorElectra(config, miscHelpers, beaconStateAccessors);
    final VoluntaryExitValidatorElectra voluntaryExitValidatorElectra =
        new VoluntaryExitValidatorElectra(config, predicates, beaconStateAccessors);
    final OperationValidator operationValidator =
        new OperationValidatorCapella(
            predicates,
            beaconStateAccessors,
            attestationDataValidator,
            attestationUtil,
            voluntaryExitValidatorElectra);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(
            config,
            beaconStateUtil,
            attestationUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final EpochProcessorFulu epochProcessor =
        new EpochProcessorFulu(
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
    final ExecutionRequestsDataCodec executionRequestsDataCodec =
        new ExecutionRequestsDataCodec(schemaDefinitions.getExecutionRequestsSchema());
    final WithdrawalsHelpersElectra withdrawalsHelpers =
        new WithdrawalsHelpersElectra(
            schemaDefinitions, miscHelpers, config, predicates, beaconStateMutators);
    final ExecutionRequestsProcessorFulu executionRequestsProcessor =
        new ExecutionRequestsProcessorFulu(
            schemaDefinitions,
            miscHelpers,
            config,
            predicates,
            validatorsUtil,
            beaconStateMutators,
            beaconStateAccessors);
    final BlockProcessorFulu blockProcessor =
        new BlockProcessorFulu(
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
            withdrawalsHelpers,
            executionRequestsDataCodec,
            executionRequestsProcessor);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtilFulu(
            config, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtilFulu(schemaDefinitions, blockProcessor, config.getFuluForkEpoch());

    final BlindBlockUtilFulu blindBlockUtil = new BlindBlockUtilFulu(schemaDefinitions);

    // State upgrade
    final FuluStateUpgrade stateUpgrade =
        new FuluStateUpgrade(config, schemaDefinitions, beaconStateAccessors, miscHelpers);

    // Data column sidecar util
    final DataColumnSidecarUtil dataColumnSidecarUtil = new DataColumnSidecarUtilFulu(miscHelpers);

    return new SpecLogicFulu(
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
        executionRequestsProcessor,
        executionRequestsDataCodec,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        blindBlockUtil,
        syncCommitteeUtil,
        lightClientUtil,
        stateUpgrade,
        dataColumnSidecarUtil);
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
    return executionRequestsProcessor;
  }

  @Override
  public Optional<ExecutionRequestsDataCodec> getExecutionRequestsDataCodec() {
    return executionRequestsDataCodec;
  }

  @Override
  public Optional<ExecutionPayloadVerifier> getExecutionPayloadVerifier() {
    return Optional.empty();
  }

  @Override
  public Optional<ExecutionPayloadProposalUtil> getExecutionPayloadProposalUtil() {
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecarUtil> getDataColumnSidecarUtil() {
    return dataColumnSidecarUtil;
  }
}
