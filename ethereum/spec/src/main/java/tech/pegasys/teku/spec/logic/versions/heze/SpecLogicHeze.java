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
import tech.pegasys.teku.spec.logic.versions.gloas.operations.validation.AttestationDataValidatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.DataColumnSidecarUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ProposerPreferencesUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.weaksubjectivity.WeakSubjectivityCalculatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.heze.forktransition.HezeStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.heze.statetransition.epoch.EpochProcessorHeze;
import tech.pegasys.teku.spec.logic.versions.pq.block.BlockProcessorPq;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkBackend;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.execution.ExecutionPayloadVerifierPq;
import tech.pegasys.teku.spec.logic.versions.pq.execution.ExecutionRequestsProcessorPq;
import tech.pegasys.teku.spec.logic.versions.pq.forktransition.PqStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.logic.versions.pq.operations.OperationSignatureVerifierPq;
import tech.pegasys.teku.spec.logic.versions.pq.util.AttestationUtilPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

/** On this branch Heze builds on PQ: PQ's logic with EIP-8365's deposit rule. */
public class SpecLogicHeze extends AbstractSpecLogic {
  private final Optional<WithdrawalsHelpers> withdrawalsHelpers;
  private final Optional<ExecutionRequestsProcessor> executionRequestsProcessor;
  private final Optional<ExecutionRequestsDataCodec> executionRequestsDataCodec;
  private final Optional<ExecutionPayloadVerifier> executionPayloadVerifier;
  private final Optional<ExecutionPayloadProposalUtil> executionPayloadProposalUtil;
  private final Optional<DataColumnSidecarUtil> dataColumnSidecarUtil;
  private final ProposerPreferencesUtil proposerPreferencesUtil;

  private SpecLogicHeze(
      final PredicatesPq predicates,
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final BeaconStateMutatorsPq beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final WeakSubjectivityCalculatorGloas weakSubjectivityCalculator,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilPq attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorHeze epochProcessor,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsProcessorPq executionRequestsProcessor,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final BlockProcessorPq blockProcessor,
      final ExecutionPayloadVerifierPq executionPayloadVerifier,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtilFulu blindBlockUtil,
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
    final PredicatesPq predicates = new PredicatesPq(config);
    final MiscHelpersPq miscHelpers = new MiscHelpersPq(config, predicates, schemaDefinitions);
    final BeaconStateAccessorsPq beaconStateAccessors =
        new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);
    final BeaconStateMutatorsPq beaconStateMutators =
        new BeaconStateMutatorsPq(config, miscHelpers, beaconStateAccessors, schemaDefinitions);

    // Signature verification. Blocks, bids and envelopes are XMSS-signed and attestations are
    // aggregated into a STARK proof; the BLS operation verifier only serves operations PQ no
    // longer has.
    final XmssSignatureVerifier xmssSignatureVerifier = XmssSignatureVerifier.LEAN_XMSS;
    final OperationSignatureVerifierPq operationSignatureVerifier =
        new OperationSignatureVerifierPq(
            miscHelpers, beaconStateAccessors, predicates, xmssSignatureVerifier);
    // The concatenation stand-in, or leanVM proofs once the node has a leanVM sidecar
    final StarkProofVerifier starkProofVerifier = StarkBackend.verifier(xmssSignatureVerifier);

    // Weak subjectivity
    final WeakSubjectivityCalculatorGloas weakSubjectivityCalculator =
        new WeakSubjectivityCalculatorGloas(config, beaconStateAccessors, miscHelpers);

    // Util
    final ValidatorsUtilGloas validatorsUtil =
        new ValidatorsUtilGloas(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(config, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtilPq attestationUtil =
        new AttestationUtilPq(
            config,
            schemaDefinitions,
            beaconStateAccessors,
            miscHelpers,
            starkProofVerifier,
            xmssSignatureVerifier);
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
    final EpochProcessorHeze epochProcessor =
        new EpochProcessorHeze(
            config,
            predicates,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory,
            schemaDefinitions,
            timeProvider);
    final ExecutionRequestsDataCodec executionRequestsDataCodec =
        new ExecutionRequestsDataCodec(schemaDefinitions.getExecutionRequestsSchema());
    final WithdrawalsHelpersGloas withdrawalsHelpers =
        new WithdrawalsHelpersGloas(
            schemaDefinitions, miscHelpers, config, predicates, beaconStateMutators);
    final ExecutionRequestsProcessorPq executionRequestsProcessor =
        new ExecutionRequestsProcessorPq(
            schemaDefinitions,
            miscHelpers,
            config,
            predicates,
            validatorsUtil,
            beaconStateMutators,
            beaconStateAccessors);
    final BlockProcessorPq blockProcessor =
        new BlockProcessorPq(
            config,
            predicates,
            miscHelpers,
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
            executionRequestsProcessor,
            xmssSignatureVerifier,
            starkProofVerifier);
    final ExecutionPayloadVerifierPq executionPayloadVerifier =
        new ExecutionPayloadVerifierPq(
            miscHelpers, beaconStateAccessors, executionRequestsDataCodec, xmssSignatureVerifier);
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
        new HezeStateUpgrade(
            config,
            schemaDefinitions,
            beaconStateAccessors,
            new PqStateUpgrade(config, schemaDefinitions, beaconStateAccessors, miscHelpers));

    // Data column sidecar util
    final DataColumnSidecarUtil dataColumnSidecarUtil = new DataColumnSidecarUtilGloas(miscHelpers);

    // Proposer preferences util
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
        executionPayloadProposalUtil,
        stateUpgrade,
        dataColumnSidecarUtil,
        proposerPreferencesUtil);
  }

  /** On this branch Heze builds on PQ, which has no sync committees. */
  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return Optional.empty();
  }

  /** PQ has no sync committees, so no light client either. */
  @Override
  public Optional<LightClientUtil> getLightClientUtil() {
    return Optional.empty();
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
