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

package tech.pegasys.teku.spec.logic.versions.pq.block;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_BEACON_PROPOSER;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq.BeaconBlockBodyPq;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.gloas.block.BlockProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.logic.versions.pq.util.AttestationUtilPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/**
 * PQ block processing. The block, the proposer slashing headers, the bid and the payload envelope
 * are signed with XMSS; the RANDAO reveal is a VRF proof; the attestations carry no signatures and
 * the block's STARK proof proves them all. Blocks carry no sync aggregate, voluntary exits, BLS
 * changes or payload attestations.
 *
 * <p>As in earlier forks, signatures are checked in {@link #verifyBlockSignatures} rather than
 * while processing: the block signature, the proposer slashing headers and the STARK proof ({@code
 * process_stark}). The VRF proof and the bid signature are checked while processing, as the
 * specification does.
 */
public class BlockProcessorPq extends BlockProcessorGloas {

  private final SpecConfigPq specConfigPq;
  private final PredicatesPq predicatesPq;
  private final MiscHelpersPq miscHelpersPq;
  private final BeaconStateAccessorsPq beaconStateAccessorsPq;
  private final XmssSignatureVerifier xmssSignatureVerifier;
  private final StarkProofVerifier starkProofVerifier;

  public BlockProcessorPq(
      final SpecConfigPq specConfig,
      final PredicatesPq predicates,
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final BeaconStateMutatorsPq beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilPq attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsPq schemaDefinitions,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final ExecutionRequestsProcessorGloas executionRequestsProcessor,
      final XmssSignatureVerifier xmssSignatureVerifier,
      final StarkProofVerifier starkProofVerifier) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        // PQ has no sync committees, and process_sync_aggregate is never called
        null,
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
    this.specConfigPq = specConfig;
    this.predicatesPq = predicates;
    this.miscHelpersPq = miscHelpers;
    this.beaconStateAccessorsPq = beaconStateAccessors;
    this.xmssSignatureVerifier = xmssSignatureVerifier;
    this.starkProofVerifier = starkProofVerifier;
  }

  // process_block, modified in PQ: no sync aggregate
  @Override
  public void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        getValidatorExitContextSupplier(state);
    final UInt64 parentSlot = state.getLatestBlockHeader().getSlot();

    processParentExecutionPayload(state, block, validatorExitContextSupplier);
    processBlockHeader(state, block);
    executionProcessing(state, block, payloadExecutor);
    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(
        state, block.getBody(), indexedAttestationCache, validatorExitContextSupplier, parentSlot);
  }

  /** PQ blocks carry no BLS to execution changes, which Capella validates up front. */
  @Override
  protected BlockValidationResult validateBlockPreProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier) {
    return BlockValidationResult.SUCCESSFUL;
  }

  @Override
  protected BlockValidationResult verifyBlockSignatures(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier) {
    final BeaconBlockBody body = block.getMessage().getBody();
    return BlockValidationResult.allOf(
        () -> verifyBlockSignature(preState, block),
        () -> verifyProposerSlashings(preState, body.getProposerSlashings(), signatureVerifier),
        () -> verifyStark(preState, BeaconBlockBodyPq.required(body)));
  }

  // verify_block_signature, modified in PQ
  private BlockValidationResult verifyBlockSignature(
      final BeaconState state, final SignedBeaconBlock block) {
    if (!block.isXmssSigned()) {
      return BlockValidationResult.failed("Block is not signed with XMSS: " + block);
    }
    // this fetches the block proposer index hence we pass the block slot and not only the state
    final int proposerIndex = beaconStateAccessorsPq.getBeaconProposerIndex(state, block.getSlot());
    final Bytes32 domain =
        beaconStateAccessorsPq.getDomain(
            state.getForkInfo(),
            Domain.BEACON_PROPOSER,
            beaconStateAccessorsPq.getCurrentEpoch(state));
    final Bytes32 signingRoot =
        Bytes32.wrap(miscHelpersPq.computeSigningRoot(block.getMessage(), domain));
    final UInt64 leafIndex =
        miscHelpersPq.computeXmssLeafIndex(block.getSlot(), LEAF_OFFSET_BEACON_PROPOSER);
    if (!xmssSignatureVerifier.verify(
        getValidatorXmssPublicKey(state, proposerIndex),
        leafIndex,
        signingRoot,
        block.getXmssSignature())) {
      return BlockValidationResult.failed("Invalid block signature: " + block);
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  // The signature checks of process_proposer_slashing, modified in PQ
  @Override
  protected BlockValidationResult verifyProposerSlashings(
      final BeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final BLSSignatureVerifier signatureVerifier) {
    for (final ProposerSlashing proposerSlashing : proposerSlashings) {
      final Bytes32 pubkey =
          getValidatorXmssPublicKey(
              state, proposerSlashing.getHeader1().getMessage().getProposerIndex().intValue());
      for (final SignedBeaconBlockHeader signedHeader :
          List.of(proposerSlashing.getHeader1(), proposerSlashing.getHeader2())) {
        if (!isValidProposerSlashingHeaderSignature(state, pubkey, signedHeader)) {
          return BlockValidationResult.failed(
              "Proposer slashing signature is invalid " + proposerSlashing);
        }
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  private boolean isValidProposerSlashingHeaderSignature(
      final BeaconState state, final Bytes32 pubkey, final SignedBeaconBlockHeader signedHeader) {
    if (!signedHeader.isXmssSigned()) {
      return false;
    }
    final UInt64 slot = signedHeader.getMessage().getSlot();
    final Bytes32 domain =
        beaconStateAccessorsPq.getDomain(
            state.getForkInfo(), Domain.BEACON_PROPOSER, miscHelpersPq.computeEpochAtSlot(slot));
    final Bytes32 signingRoot =
        Bytes32.wrap(miscHelpersPq.computeSigningRoot(signedHeader.getMessage(), domain));
    return xmssSignatureVerifier.verify(
        pubkey,
        miscHelpersPq.computeXmssLeafIndex(slot, LEAF_OFFSET_BEACON_PROPOSER),
        signingRoot,
        signedHeader.getXmssSignature());
  }

  // process_stark
  private BlockValidationResult verifyStark(final BeaconState state, final BeaconBlockBodyPq body) {
    final List<XmssAggregateStatement> statements =
        body.getAttestations().stream()
            .flatMap(
                attestation ->
                    beaconStateAccessorsPq.getAttestationStatements(state, attestation).stream())
            .toList();
    if (!starkProofVerifier.verifyAggregates(statements, body.getProof())) {
      return BlockValidationResult.failed("The block's attestation proof is invalid");
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  /** The VRF proof is checked in {@link #processRandaoNoValidation}, as process_randao does. */
  @Override
  protected BlockValidationResult verifyRandao(
      final BeaconState state, final BeaconBlock block, final BLSSignatureVerifier bls) {
    return BlockValidationResult.SUCCESSFUL;
  }

  // process_randao, modified in PQ
  @Override
  protected void processRandaoNoValidation(
      final MutableBeaconState state, final BeaconBlockBody body) throws BlockProcessingException {
    safelyProcess(
        () -> {
          final List<Bytes32> vrfProof = BeaconBlockBodyPq.required(body).getVrfProof();
          checkArgument(
              predicatesPq.isValidVrfProof(
                  state,
                  beaconStateAccessorsPq.getBeaconProposerIndex(state),
                  state.getSlot(),
                  vrfProof),
              "process_randao: Verify the VRF proof");
          final UInt64 epoch = beaconStateAccessorsPq.getCurrentEpoch(state);
          final Bytes32 mix =
              Hash.sha256(
                  Bytes.concatenate(
                      beaconStateAccessorsPq.getRandaoMix(state, epoch),
                      vrfProof.getFirst(),
                      Domain.RANDAO.getWrappedBytes()));
          state
              .getRandaoMixes()
              .setElement(epoch.mod(specConfig.getEpochsPerHistoricalVector()).intValue(), mix);
        });
  }

  // process_operations, modified in PQ. The operation count limits are enforced by the list
  // schemas, and process_stark is in verifyBlockSignatures.
  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier,
      final UInt64 parentSlot)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          checkArgument(
              body.getDeposits().isEmpty(), "process_operations: PQ blocks carry no deposits");
          processProposerSlashingsNoValidation(
              state, body.getProposerSlashings(), validatorExitContextSupplier);
          processAttesterSlashings(
              state, body.getAttesterSlashings(), validatorExitContextSupplier);
          processAttestationsNoVerification(
              state, body.getAttestations(), indexedAttestationCache, parentSlot);
        });
  }

  // process_attestation, modified in PQ: the minimum inclusion delay
  @Override
  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    super.assertAttestationValid(state, attestation);
    checkArgument(
        attestation
            .getData()
            .getSlot()
            .plus(specConfigPq.getMinAttestationInclusionDelayPq())
            .isLessThanOrEqualTo(state.getSlot()),
        "process_attestations: Attestation is included before the minimum inclusion delay");
  }

  /** PQ attestations carry no signatures: the block's STARK proof proves them. */
  @Override
  protected BlockValidationResult verifyAttestationSignatures(
      final BeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier,
      final IndexedAttestationProvider indexedAttestationProvider) {
    return BlockValidationResult.SUCCESSFUL;
  }

  @Override
  public void processSyncAggregate(
      final MutableBeaconState state,
      final SyncAggregate syncAggregate,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    throw new BlockProcessingException("PQ blocks carry no sync aggregate");
  }

  // process_execution_payload_bid, modified in PQ: a self-build carries the zero signature
  @Override
  protected boolean isSelfBuildBidSignature(final SignedExecutionPayloadBid signedBid) {
    return signedBid.isXmssSigned() && signedBid.getXmssSignature().isZero();
  }

  // verify_execution_payload_bid_signature, modified in PQ
  @Override
  protected boolean verifyExecutionPayloadBidSignature(
      final BeaconState state, final SignedExecutionPayloadBid signedBid) {
    if (!signedBid.isXmssSigned()) {
      return false;
    }
    final Bytes signature = signedBid.getXmssSignature();
    final UInt64 leafIndex = XmssSignatureVerifier.getLeafIndex(signature);
    if (!predicatesPq.isBuilderBidLeafIndex(leafIndex)) {
      return false;
    }
    final BuilderPq builder =
        (BuilderPq)
            BeaconStateGloas.required(state)
                .getBuilders()
                .get(signedBid.getMessage().getBuilderIndex().intValue());
    final Bytes32 domain =
        beaconStateAccessorsPq.getDomain(
            state.getForkInfo(),
            Domain.BEACON_BUILDER,
            beaconStateAccessorsPq.getCurrentEpoch(state));
    final Bytes32 signingRoot =
        Bytes32.wrap(miscHelpersPq.computeSigningRoot(signedBid.getMessage(), domain));
    return xmssSignatureVerifier.verify(
        builder.getXmssPublicKey(), leafIndex, signingRoot, signature);
  }

  private Bytes32 getValidatorXmssPublicKey(final BeaconState state, final int validatorIndex) {
    return ((ValidatorPq) state.getValidators().get(validatorIndex)).getXmssPublicKey();
  }
}
