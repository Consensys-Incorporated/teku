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

package tech.pegasys.teku.spec.logic.common.block;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public abstract class AbstractBlockProcessor implements BlockProcessor {

  @VisibleForTesting
  public static final BLSSignatureVerifier DEFAULT_DEPOSIT_SIGNATURE_VERIFIER =
      BLSSignatureVerifier.SIMPLE;

  /**
   * For debug/test purposes only enables/disables {@link DepositData} BLS signature verification
   * Setting to <code>false</code> significantly speeds up state initialization
   */
  @VisibleForTesting
  @SuppressWarnings("NonFinalStaticField")
  public static BLSSignatureVerifier depositSignatureVerifier = DEFAULT_DEPOSIT_SIGNATURE_VERIFIER;

  private static final Logger LOG = LogManager.getLogger();

  protected final SpecConfig specConfig;
  protected final Predicates predicates;
  protected final MiscHelpers miscHelpers;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final BeaconStateMutators beaconStateMutators;

  protected final OperationSignatureVerifier operationSignatureVerifier;
  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final ValidatorsUtil validatorsUtil;
  protected final OperationValidator operationValidator;

  protected AbstractBlockProcessor(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator) {
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.beaconStateMutators = beaconStateMutators;
    this.operationSignatureVerifier = operationSignatureVerifier;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.validatorsUtil = validatorsUtil;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.operationValidator = operationValidator;
  }

  @Override
  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final IndexedAttestationCache indexedAttestationCache,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    final BatchSignatureVerifier signatureVerifier = specConfig.createBatchSignatureVerifier();
    final BeaconState result =
        processAndValidateBlock(
            signedBlock,
            blockSlotState,
            indexedAttestationCache,
            signatureVerifier,
            payloadExecutor);
    if (!signatureVerifier.batchVerify()) {
      throw new StateTransitionException(
          "Batch signature verification failed for block " + signedBlock.toLogString());
    }
    return result;
  }

  @Override
  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    try {
      final BlockValidationResult preValidationResult =
          validateBlockPreProcessing(blockSlotState, signedBlock, signatureVerifier);
      if (!preValidationResult.isValid()) {
        throw new BlockProcessingException(preValidationResult.getFailureReason());
      }

      // Process_block
      final BeaconState postState =
          processUnsignedBlock(
              blockSlotState,
              signedBlock.getMessage(),
              indexedAttestationCache,
              signatureVerifier,
              payloadExecutor);

      final BlockValidationResult blockValidationResult =
          validateBlockPostProcessing(
              blockSlotState, signedBlock, postState, indexedAttestationCache, signatureVerifier);

      if (!blockValidationResult.isValid()) {
        throw new BlockProcessingException(blockValidationResult.getFailureReason());
      }

      return postState;
    } catch (final IllegalArgumentException | BlockProcessingException e) {
      LOG.warn(
          String.format(
              "State transition error while importing block %s (%s)",
              signedBlock.getSlot(), signedBlock.getRoot()),
          e);
      throw new StateTransitionException(e);
    }
  }

  @CheckReturnValue
  protected BlockValidationResult validateBlockPreProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    return BlockValidationResult.SUCCESSFUL;
  }

  /**
   * Validate the given block. Checks signatures in the block and verifies stateRoot matches
   * postState
   *
   * @param preState The preState to which the block is applied
   * @param block The block being validated
   * @param postState The post state resulting from processing the block on top of the preState
   * @param indexedAttestationCache A cache for calculating indexed attestations
   * @return A block validation result
   */
  @CheckReturnValue
  private BlockValidationResult validateBlockPostProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BeaconState postState,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier) {
    return BlockValidationResult.allOf(
        () -> verifyBlockSignatures(preState, block, indexedAttestationCache, signatureVerifier),
        () -> validatePostState(postState, block));
  }

  @CheckReturnValue
  protected BlockValidationResult verifyBlockSignatures(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier) {
    final BeaconBlock blockMessage = block.getMessage();
    final BeaconBlockBody blockBody = blockMessage.getBody();

    return BlockValidationResult.allOf(
        () -> verifyBlockSignature(preState, block, signatureVerifier),
        () ->
            verifyAttestationSignatures(
                preState, blockBody.getAttestations(), signatureVerifier, indexedAttestationCache),
        () -> verifyRandao(preState, blockMessage, signatureVerifier),
        () ->
            verifyProposerSlashings(preState, blockBody.getProposerSlashings(), signatureVerifier),
        () -> verifyVoluntaryExits(preState, blockBody.getVoluntaryExits(), signatureVerifier));
  }

  @CheckReturnValue
  private BlockValidationResult verifyBlockSignature(
      final BeaconState state,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier) {
    // this fetches the block proposer index hence we pass the block slot and not only the state
    final int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state, block.getSlot());
    final Optional<BLSPublicKey> proposerPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, UInt64.valueOf(proposerIndex));
    if (proposerPublicKey.isEmpty()) {
      return BlockValidationResult.failed("Public key not found for validator " + proposerIndex);
    }
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(
            block.getMessage(), getDomain(state, Domain.BEACON_PROPOSER));
    if (!signatureVerifier.verify(proposerPublicKey.get(), signingRoot, block.getSignature())) {
      return BlockValidationResult.failed("Invalid block signature: " + block);
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  private Bytes32 getDomain(final BeaconState state, final Bytes4 beaconProposer) {
    return beaconStateAccessors.getDomain(
        state.getForkInfo(), beaconProposer, beaconStateAccessors.getCurrentEpoch(state));
  }

  @CheckReturnValue
  private BlockValidationResult validatePostState(
      final BeaconState postState, final SignedBeaconBlock block) {
    if (!block.getMessage().getStateRoot().equals(postState.hashTreeRoot())) {
      return BlockValidationResult.failed(
          "Block state root does NOT match the calculated state root!\n"
              + "Block state root: "
              + block.getMessage().getStateRoot().toHexString()
              + "\n  New state root: "
              + postState.hashTreeRoot().toHexString()
              + "\n block proposer: "
              + block.getProposerIndex());
    } else {
      return BlockValidationResult.SUCCESSFUL;
    }
  }

  @Override
  @CheckReturnValue
  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return operationValidator.validateAttestationData(state.getFork(), state, data);
  }

  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    final AttestationData data = attestation.getData();

    final Optional<OperationInvalidReason> invalidReason = validateAttestation(state, data);
    checkArgument(
        invalidReason.isEmpty(),
        "process_attestations: %s",
        invalidReason.map(OperationInvalidReason::describe).orElse(""));

    IntList committee =
        beaconStateAccessors.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        attestation.getAggregationBits().size() == committee.size(),
        "process_attestations: Attestation aggregation bits and committee don't have the same length - committee %s, aggregation bits %s",
        committee.size(),
        attestation.getAggregationBits().size());
  }

  @Override
  public BeaconState processUnsignedBlock(
      final BeaconState preState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    return preState.updated(
        state -> {
          processBlock(state, block, indexedAttestationCache, signatureVerifier, payloadExecutor);
          BeaconStateCache.getSlotCaches(state).onBlockProcessed();
        });
  }

  protected void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final UInt64 parentSlot = state.getLatestBlockHeader().getSlot();
    processBlockHeader(state, block);
    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(
        state,
        block.getBody(),
        indexedAttestationCache,
        getValidatorExitContextSupplier(state),
        parentSlot);
  }

  protected Supplier<ValidatorExitContext> getValidatorExitContextSupplier(
      final MutableBeaconState state) {
    return beaconStateMutators.createValidatorExitContextSupplier(state);
  }

  @Override
  public void processBlockHeader(
      final MutableBeaconState state, final BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          checkArgument(
              blockHeader.getSlot().equals(state.getSlot()),
              "process_block_header: Verify that the slots match");
          checkArgument(
              blockHeader.getProposerIndex().longValue()
                  == beaconStateAccessors.getBeaconProposerIndex(state),
              "process_block_header: Verify that proposer index is the correct index");
          checkArgument(
              blockHeader.getParentRoot().equals(state.getLatestBlockHeader().hashTreeRoot()),
              "process_block_header: Verify that the parent matches");
          checkArgument(
              blockHeader.getSlot().compareTo(state.getLatestBlockHeader().getSlot()) > 0,
              "process_block_header: Verify that the block is newer than latest block header");

          // Cache the current block as the new latest block
          state.setLatestBlockHeader(
              new BeaconBlockHeader(
                  blockHeader.getSlot(),
                  blockHeader.getProposerIndex(),
                  blockHeader.getParentRoot(),
                  Bytes32.ZERO, // Overwritten in the next `process_slot` call
                  blockHeader.getBodyRoot()));

          // Only if we are processing blocks (not proposing them)
          final Validator proposer =
              state.getValidators().get(blockHeader.getProposerIndex().intValue());
          checkArgument(
              !proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");
        });
  }

  protected void processRandaoNoValidation(
      final MutableBeaconState state, final BeaconBlockBody body) throws BlockProcessingException {
    safelyProcess(
        () -> {
          final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);

          final Bytes32 mix =
              beaconStateAccessors
                  .getRandaoMix(state, epoch)
                  .xor(Hash.sha256(body.getRandaoReveal().toSSZBytes()));
          final int index = epoch.mod(specConfig.getEpochsPerHistoricalVector()).intValue();
          state.getRandaoMixes().setElement(index, mix);
        });
  }

  protected BlockValidationResult verifyRandao(
      final BeaconState state, final BeaconBlock block, final BLSSignatureVerifier bls) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(block.getSlot());
    // Verify RANDAO reveal
    final BLSPublicKey proposerPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, block.getProposerIndex()).orElseThrow();
    final Bytes32 domain = getDomain(state, Domain.RANDAO);
    final Bytes signingRoot = miscHelpers.computeSigningRoot(epoch, domain);
    if (!bls.verify(proposerPublicKey, signingRoot, block.getBody().getRandaoReveal())) {
      return BlockValidationResult.failed("Randao reveal is invalid.");
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  protected void processEth1Data(final MutableBeaconState state, final BeaconBlockBody body) {
    state.getEth1DataVotes().append(body.getEth1Data());
    final long voteCount = getVoteCount(state, body.getEth1Data());
    if (isEnoughVotesToUpdateEth1Data(voteCount)) {
      state.setEth1Data(body.getEth1Data());
    }
  }

  @Override
  public boolean isEnoughVotesToUpdateEth1Data(final long voteCount) {
    return voteCount * 2
        > (long) specConfig.getEpochsPerEth1VotingPeriod() * specConfig.getSlotsPerEpoch();
  }

  @Override
  public long getVoteCount(final BeaconState state, final Eth1Data eth1Data) {
    return state.getEth1DataVotes().stream().filter(item -> item.equals(eth1Data)).count();
  }

  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier,
      final UInt64 parentSlot)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          verifyOutstandingDepositsAreProcessed(state, body);

          processProposerSlashingsNoValidation(
              state, body.getProposerSlashings(), validatorExitContextSupplier);
          processAttesterSlashings(
              state, body.getAttesterSlashings(), validatorExitContextSupplier);
          processAttestationsNoVerification(
              state, body.getAttestations(), indexedAttestationCache, parentSlot);
          processDeposits(state, body.getDeposits());
          processVoluntaryExitsNoValidation(
              state, body.getVoluntaryExits(), validatorExitContextSupplier);
        });
  }

  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {
    final int expectedDepositCount =
        Math.min(
            specConfig.getMaxDeposits(),
            state.getEth1Data().getDepositCount().minus(state.getEth1DepositIndex()).intValue());

    checkArgument(
        body.getDeposits().size() == expectedDepositCount,
        "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
  }

  @Override
  public void processProposerSlashings(
      final MutableBeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        getValidatorExitContextSupplier(state);
    processProposerSlashingsNoValidation(state, proposerSlashings, validatorExitContextSupplier);
    final BlockValidationResult validationResult =
        verifyProposerSlashings(state, proposerSlashings, signatureVerifier);
    if (!validationResult.isValid()) {
      throw new BlockProcessingException("Slashing signature is invalid");
    }
  }

  protected void processProposerSlashingsNoValidation(
      final MutableBeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          // For each proposer_slashing in block.body.proposer_slashings:
          for (ProposerSlashing proposerSlashing : proposerSlashings) {
            Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateProposerSlashing(
                    state.getFork(), state, proposerSlashing);
            checkArgument(
                invalidReason.isEmpty(),
                "process_proposer_slashings: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));

            beaconStateMutators.slashValidator(
                state,
                proposerSlashing.getHeader1().getMessage().getProposerIndex().intValue(),
                validatorExitContextSupplier);
          }
        });
  }

  protected BlockValidationResult verifyProposerSlashings(
      final BeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final BLSSignatureVerifier signatureVerifier) {
    // For each proposer_slashing in block.body.proposer_slashings:
    for (ProposerSlashing proposerSlashing : proposerSlashings) {

      boolean slashingSignatureValid =
          operationSignatureVerifier.verifyProposerSlashingSignature(
              state.getFork(), state, proposerSlashing, signatureVerifier);
      if (!slashingSignatureValid) {
        return BlockValidationResult.failed(
            "Proposer slashing signature is invalid " + proposerSlashing);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  @Override
  public void processAttesterSlashings(
      final MutableBeaconState state, final SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          final Supplier<ValidatorExitContext> validatorExitContextSupplier =
              getValidatorExitContextSupplier(state);
          processAttesterSlashings(state, attesterSlashings, validatorExitContextSupplier);
        });
  }

  @Override
  public void processAttesterSlashings(
      final MutableBeaconState state,
      final SszList<AttesterSlashing> attesterSlashings,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          // For each attester_slashing in block.body.attester_slashings:
          for (AttesterSlashing attesterSlashing : attesterSlashings) {
            List<UInt64> indicesToSlash = new ArrayList<>();
            final Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateAttesterSlashing(
                    state.getFork(), state, attesterSlashing, indicesToSlash::add);

            checkArgument(
                invalidReason.isEmpty(),
                "process_attester_slashings: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));

            indicesToSlash.forEach(
                indexToSlash ->
                    beaconStateMutators.slashValidator(
                        state, indexToSlash.intValue(), validatorExitContextSupplier));
          }
        });
  }

  @Override
  public void processAttestations(
      final MutableBeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final CapturingIndexedAttestationCache indexedAttestationCache =
        IndexedAttestationCache.capturing();
    processAttestationsNoVerification(state, attestations, indexedAttestationCache);

    final BlockValidationResult result =
        verifyAttestationSignatures(
            state, attestations, signatureVerifier, indexedAttestationCache);
    if (!result.isValid()) {
      throw new BlockProcessingException(result.getFailureReason());
    }
  }

  protected void processAttestationsNoVerification(
      final MutableBeaconState state,
      final SszList<Attestation> attestations,
      final IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    processAttestationsNoVerification(
        state,
        attestations,
        indexedAttestationCache,
        beaconStateAccessors.getAttestationParentSlot(state));
  }

  protected void processAttestationsNoVerification(
      final MutableBeaconState state,
      final SszList<Attestation> attestations,
      final IndexedAttestationCache indexedAttestationCache,
      final UInt64 parentSlot)
      throws BlockProcessingException {
    final IndexedAttestationProvider indexedAttestationProvider =
        createIndexedAttestationProvider(state, indexedAttestationCache);
    safelyProcess(
        () -> {
          for (Attestation attestation : attestations) {
            // Validate
            assertAttestationValid(state, attestation);
            processAttestation(state, attestation, indexedAttestationProvider, parentSlot);
          }
        });
  }

  public IndexedAttestationProvider createIndexedAttestationProvider(
      final BeaconState state, final IndexedAttestationCache indexedAttestationCache) {
    return (attestation) ->
        indexedAttestationCache.computeIfAbsent(
            attestation, () -> attestationUtil.getIndexedAttestation(state, attestation));
  }

  /**
   * Corresponds to fork-specific logic from "process_attestation" spec method. Common validation
   * and signature verification logic can be found in {@link
   * #verifyAttestationSignatures(BeaconState, SszList, BLSSignatureVerifier,
   * IndexedAttestationCache)}.
   *
   * @param genericState The state corresponding to the block being processed
   * @param attestation An attestation in the body of the block being processed
   * @param indexedAttestationProvider provider of indexed attestations
   */
  protected abstract void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider,
      final UInt64 parentSlot);

  @CheckReturnValue
  protected BlockValidationResult verifyAttestationSignatures(
      final BeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier,
      final IndexedAttestationCache indexedAttestationCache) {
    return verifyAttestationSignatures(
        state,
        attestations,
        signatureVerifier,
        createIndexedAttestationProvider(state, indexedAttestationCache));
  }

  @CheckReturnValue
  protected BlockValidationResult verifyAttestationSignatures(
      final BeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier,
      final IndexedAttestationProvider indexedAttestationProvider) {

    Optional<AttestationProcessingResult> processResult =
        attestations.stream()
            .map(indexedAttestationProvider::getIndexedAttestation)
            .map(
                attestation ->
                    attestationUtil.isValidIndexedAttestation(
                        state.getFork(), state, attestation, signatureVerifier))
            .filter(result -> !result.isSuccessful())
            .findAny();
    return processResult
        .map(
            attestationProcessingResult ->
                BlockValidationResult.failed(
                    "Invalid attestation: " + attestationProcessingResult.getInvalidReason()))
        .orElse(BlockValidationResult.SUCCESSFUL);
  }

  @Override
  public void processDeposits(final MutableBeaconState state, final SszList<Deposit> deposits)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          final boolean depositSignaturesAreAllGood = batchVerifyDepositSignatures(deposits);
          for (Deposit deposit : deposits) {
            processDeposit(state, deposit, depositSignaturesAreAllGood);
          }
        });
  }

  private boolean batchVerifyDepositSignatures(final SszList<Deposit> deposits) {
    try {
      final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
      final List<Bytes> messages = new ArrayList<>();
      final List<BLSSignature> signatures = new ArrayList<>();
      for (final Deposit deposit : deposits) {
        final BLSPublicKey pubkey = deposit.getData().getPubkey();
        publicKeys.add(List.of(pubkey));
        messages.add(
            miscHelpers.computeDepositSigningRoot(
                pubkey,
                deposit.getData().getWithdrawalCredentials(),
                deposit.getData().getAmount()));
        signatures.add(deposit.getData().getSignature());
      }
      // Overwhelmingly often we expect all the deposit signatures to be good
      return specConfig.getBLSSignatureVerifier().verify(publicKeys, messages, signatures);
    } catch (final BlsException e) {
      return false;
    }
  }

  /** process_deposit */
  public void processDeposit(
      final MutableBeaconState state,
      final Deposit deposit,
      final boolean signatureAlreadyVerified) {
    checkArgument(
        predicates.isValidMerkleBranch(
            deposit.getData().hashTreeRoot(),
            deposit.getProof(),
            specConfig.getDepositContractTreeDepth() + 1, // Add 1 for the List length mix-in
            state.getEth1DepositIndex().intValue(),
            state.getEth1Data().getDepositRoot()),
        "process_deposit: Verify the Merkle branch");

    processDepositWithoutCheckingMerkleProof(
        state, deposit, Optional.empty(), signatureAlreadyVerified);
  }

  @Override
  public void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {

    state.setEth1DepositIndex(state.getEth1DepositIndex().plus(UInt64.ONE));

    applyDeposit(
        state,
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawalCredentials(),
        deposit.getData().getAmount(),
        deposit.getData().getSignature(),
        maybePubkeyToIndexMap,
        signatureAlreadyVerified);
  }

  /** apply_deposit */
  public void applyDeposit(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {
    // Find the validator index associated with this deposit, if it exists
    final Optional<Integer> existingIndex =
        maybePubkeyToIndexMap
            .flatMap(
                pubkeyToIndexMap -> {
                  if (pubkeyToIndexMap.containsKey(pubkey)) {
                    return Optional.of(pubkeyToIndexMap.getInt(pubkey));
                  } else {
                    pubkeyToIndexMap.put(pubkey, state.getValidators().size());
                    return Optional.empty();
                  }
                })
            .or(() -> validatorsUtil.getValidatorIndex(state, pubkey));

    if (existingIndex.isEmpty()) {
      // This is a new validator
      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (signatureAlreadyVerified
          || miscHelpers.isValidDepositSignature(
              pubkey, withdrawalCredentials, amount, signature)) {
        beaconStateMutators.addValidatorToRegistry(state, pubkey, withdrawalCredentials, amount);
      } else {
        handleInvalidDeposit(pubkey, maybePubkeyToIndexMap);
      }
    } else {
      // Increase balance by deposit amount
      beaconStateMutators.increaseBalance(state, existingIndex.get(), amount);
    }
  }

  protected void handleInvalidDeposit(
      final BLSPublicKey pubkey,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap) {
    LOG.debug("Skipping invalid deposit with pubkey {}", pubkey);
    maybePubkeyToIndexMap.ifPresent(
        pubkeyToIndexMap -> {
          // The validator won't be created so the calculated index won't be correct
          pubkeyToIndexMap.removeInt(pubkey);
        });
  }

  @Override
  public void processVoluntaryExits(
      final MutableBeaconState state,
      final SszList<SignedVoluntaryExit> exits,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        getValidatorExitContextSupplier(state);
    processVoluntaryExitsNoValidation(state, exits, validatorExitContextSupplier);
    final BlockValidationResult signaturesValid =
        verifyVoluntaryExits(state, exits, signatureVerifier);
    if (!signaturesValid.isValid()) {
      throw new BlockProcessingException(signaturesValid.getFailureReason());
    }
  }

  protected void processVoluntaryExitsNoValidation(
      final MutableBeaconState state,
      final SszList<SignedVoluntaryExit> exits,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {

    safelyProcess(
        () -> {
          // For each exit in block.body.voluntaryExits:
          for (SignedVoluntaryExit signedExit : exits) {
            final Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateVoluntaryExit(state.getFork(), state, signedExit);
            checkArgument(
                invalidReason.isEmpty(),
                "process_voluntary_exits: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));
            // - Run initiate_validator_exit(state, exit.validator_index)
            beaconStateMutators.initiateValidatorExit(
                state,
                signedExit.getMessage().getValidatorIndex().intValue(),
                validatorExitContextSupplier);
          }
        });
  }

  protected BlockValidationResult verifyVoluntaryExits(
      final BeaconState state,
      final SszList<SignedVoluntaryExit> exits,
      final BLSSignatureVerifier signatureVerifier) {
    for (SignedVoluntaryExit signedExit : exits) {
      final boolean exitSignatureValid =
          operationSignatureVerifier.verifyVoluntaryExitSignature(
              state, signedExit, signatureVerifier);
      if (!exitSignatureValid) {
        return BlockValidationResult.failed("Exit signature is invalid: " + signedExit);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  // Catch generic errors and wrap them in a BlockProcessingException
  protected void safelyProcess(final BlockProcessingAction action) throws BlockProcessingException {
    try {
      action.run();
    } catch (ArithmeticException | IllegalArgumentException | IndexOutOfBoundsException e) {
      if (Throwables.getRootCause(e) instanceof IllegalArgumentException) {
        LOG.warn("Failed to process block: {}", e.getMessage());
      } else {
        LOG.warn("Failed to process block", e);
      }
      throw new BlockProcessingException(e);
    }
  }

  protected <T> T safelyProcessAndReturn(final BlockProcessingSupplier<T> action)
      throws BlockProcessingException {
    try {
      return action.get();
    } catch (ArithmeticException | IllegalArgumentException | IndexOutOfBoundsException e) {
      if (Throwables.getRootCause(e) instanceof IllegalArgumentException) {
        LOG.warn("Failed to process block: {}", e.getMessage());
      } else {
        LOG.warn("Failed to process block", e);
      }
      throw new BlockProcessingException(e);
    }
  }

  public interface IndexedAttestationProvider {

    IndexedAttestationLight getIndexedAttestation(final Attestation attestation);
  }

  protected interface BlockProcessingAction {

    void run() throws BlockProcessingException;
  }

  @FunctionalInterface
  protected interface BlockProcessingSupplier<T> {

    T get() throws BlockProcessingException;
  }
}
