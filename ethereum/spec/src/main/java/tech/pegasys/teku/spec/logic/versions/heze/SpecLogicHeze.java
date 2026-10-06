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

package tech.pegasys.teku.spec.logic.versions.heze;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerifier;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.VoluntaryExitValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlindBlockUtilFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlockProposalUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.block.BlockProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionPayloadVerifierGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.OperationSignatureVerifierGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.validation.AttestationDataValidatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.DataColumnSidecarUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ProposerPreferencesUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.weaksubjectivity.WeakSubjectivityCalculatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.heze.forktransition.HezeStateUpgrade;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

public class SpecLogicHeze extends AbstractSpecLogic {
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;
  private final Optional<LightClientUtil> lightClientUtil;
  private final Optional<WithdrawalsHelpers> withdrawalsHelpers;
  private final Optional<ExecutionRequestsProcessor> executionRequestsProcessor;
  private final Optional<ExecutionRequestsDataCodec> executionRequestsDataCodec;
  private final Optional<ExecutionPayloadVerifier> executionPayloadVerifier;
  private final Optional<ExecutionPayloadProposalUtil> executionPayloadProposalUtil;
  private final Optional<DataColumnSidecarUtil> dataColumnSidecarUtil;
  private final ProposerPreferencesUtil proposerPreferencesUtil;

  private SpecLogicHeze(
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final WeakSubjectivityCalculatorGloas weakSubjectivityCalculator,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilGloas attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorGloas epochProcessor,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsProcessorGloas executionRequestsProcessor,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final BlockProcessorGloas blockProcessor,
      final ExecutionPayloadVerifierGloas executionPayloadVerifier,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtilFulu blindBlockUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final LightClientUtil lightClientUtil,
      final ExecutionPayloadProposalUtil executionPayloadProposalUtil,
      final HezeStateUpgrade stateUpgrade,
      final DataColumnSidecarUtil dataColumnSidecarUtil,
      final ProposerPreferencesUtil proposerPreferencesUtil) {
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
    this.executionRequestsProcessor = Optional.of(executionRequestsProcessor);
    this.executionRequestsDataCodec = Optional.of(executionRequestsDataCodec);
    this.withdrawalsHelpers = Optional.of(withdrawalsHelpers);
    this.executionPayloadVerifier = Optional.of(executionPayloadVerifier);
    this.executionPayloadProposalUtil = Optional.of(executionPayloadProposalUtil);
    this.dataColumnSidecarUtil = Optional.of(dataColumnSidecarUtil);
    this.proposerPreferencesUtil = proposerPreferencesUtil;
  }

  public static SpecLogicHeze create(
      final SpecConfigHeze config,
      final SchemaDefinitionsHeze schemaDefinitions,
      final TimeProvider timeProvider) {
    // Helpers
    final PredicatesGloas predicates = new PredicatesGloas(config);
    final MiscHelpersGloas miscHelpers =
        new MiscHelpersGloas(config, predicates, schemaDefinitions);
    final BeaconStateAccessorsGloas beaconStateAccessors =
        new BeaconStateAccessorsGloas(config, schemaDefinitions, predicates, miscHelpers);
    final BeaconStateMutatorsGloas beaconStateMutators =
        new BeaconStateMutatorsGloas(config, miscHelpers, beaconStateAccessors, schemaDefinitions);

    // Operation validation
    final OperationSignatureVerifierGloas operationSignatureVerifier =
        new OperationSignatureVerifierGloas(miscHelpers, beaconStateAccessors, predicates);

    // Weak subjectivity
    final WeakSubjectivityCalculatorGloas weakSubjectivityCalculator =
        new WeakSubjectivityCalculatorGloas(config, beaconStateAccessors, miscHelpers);

    // Util
    final ValidatorsUtilGloas validatorsUtil =
        new ValidatorsUtilGloas(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(config, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtilGloas attestationUtil =
        new AttestationUtilGloas(config, schemaDefinitions, beaconStateAccessors, miscHelpers);
    final AttestationDataValidatorGloas attestationDataValidator =
        new AttestationDataValidatorGloas(config, miscHelpers, beaconStateAccessors);
    final VoluntaryExitValidatorElectra voluntaryExitValidator =
        new VoluntaryExitValidatorElectra(config, predicates, beaconStateAccessors);
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
    final EpochProcessorGloas epochProcessor =
        new EpochProcessorGloas(
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
    final WithdrawalsHelpersGloas withdrawalsHelpers =
        new WithdrawalsHelpersGloas(
            schemaDefinitions, miscHelpers, config, predicates, beaconStateMutators);
    final ExecutionRequestsProcessorGloas executionRequestsProcessor =
        new ExecutionRequestsProcessorGloas(
            schemaDefinitions,
            miscHelpers,
            config,
            predicates,
            validatorsUtil,
            beaconStateMutators,
            beaconStateAccessors);
    final BlockProcessorGloas blockProcessor =
        new BlockProcessorGloas(
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
    final ExecutionPayloadVerifierGloas executionPayloadVerifier =
        new ExecutionPayloadVerifierGloas(
            miscHelpers, beaconStateAccessors, executionRequestsDataCodec);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtilGloas(
            config,
            beaconStateAccessors,
            beaconStateMutators,
            epochProcessor,
            attestationUtil,
            miscHelpers,
            withdrawalsHelpers,
            blockProcessor);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtilFulu(schemaDefinitions, blockProcessor, config.getFuluForkEpoch());

    final BlindBlockUtilFulu blindBlockUtil = new BlindBlockUtilFulu(schemaDefinitions);

    final ExecutionPayloadProposalUtil executionPayloadProposalUtil =
        new ExecutionPayloadProposalUtil(schemaDefinitions);

    // State upgrade
    final HezeStateUpgrade stateUpgrade =
        new HezeStateUpgrade(config, schemaDefinitions, beaconStateAccessors);

    // Data column sidecar util
    final DataColumnSidecarUtil dataColumnSidecarUtil = new DataColumnSidecarUtilGloas(miscHelpers);

    // Proposer preferences util (introduced in Gloas, still active in Heze)
    final ProposerPreferencesUtil proposerPreferencesUtil =
        new ProposerPreferencesUtilGloas(schemaDefinitions);

    return new SpecLogicHeze(
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
        executionPayloadVerifier,
        forkChoiceUtil,
        blockProposalUtil,
        blindBlockUtil,
        syncCommitteeUtil,
        lightClientUtil,
        executionPayloadProposalUtil,
        stateUpgrade,
        dataColumnSidecarUtil,
        proposerPreferencesUtil);
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
    return executionPayloadVerifier;
  }

  @Override
  public Optional<ExecutionPayloadProposalUtil> getExecutionPayloadProposalUtil() {
    return executionPayloadProposalUtil;
  }

  @Override
  public Optional<DataColumnSidecarUtil> getDataColumnSidecarUtil() {
    return dataColumnSidecarUtil;
  }

  @Override
  public ProposerPreferencesUtil getProposerPreferencesUtil() {
    return proposerPreferencesUtil;
  }
}
