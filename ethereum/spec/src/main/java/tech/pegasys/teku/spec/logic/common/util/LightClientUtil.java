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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_BLOCK_HASH_GINDEX;
import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_BLOCK_HASH_GINDEX_DENEB;
import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_BLOCK_HASH_GINDEX_GLOAS;
import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_PAYLOAD_GINDEX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.versions.capella.LightClientHeaderSchemaCapella;
import tech.pegasys.teku.spec.datastructures.lightclient.versions.gloas.LightClientHeaderSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class LightClientUtil {

  private final BeaconStateAccessorsAltair beaconStateAccessors;
  private final SyncCommitteeUtil syncCommitteeUtil;
  private final SchemaDefinitionsAltair schemaDefinitionsAltair;
  private final MiscHelpers miscHelpers;
  private final SpecConfigAltair specConfig;

  public LightClientUtil(
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final SyncCommitteeUtil syncCommitteeUtil,
      final SchemaDefinitionsAltair schemaDefinitionsAltair,
      final MiscHelpers miscHelpers,
      final SpecConfigAltair specConfig) {
    this.beaconStateAccessors = beaconStateAccessors;
    this.syncCommitteeUtil = syncCommitteeUtil;
    this.schemaDefinitionsAltair = schemaDefinitionsAltair;
    this.miscHelpers = miscHelpers;
    this.specConfig = specConfig;
  }

  /** {@code create_light_client_bootstrap}. */
  public LightClientBootstrap createLightClientBootstrap(
      final BeaconState state, final SignedBeaconBlock block) {
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);

    checkArgument(
        currentEpoch.isGreaterThanOrEqualTo(specConfig.getAltairForkEpoch()),
        "Current epoch must be at or after the Altair fork.");

    checkArgument(
        state.getSlot().equals(state.getLatestBlockHeader().getSlot()),
        "State must be processed upto its latest block header.");

    checkArgument(
        BeaconBlockHeader.fromState(state).hashTreeRoot().equals(block.getRoot()),
        "State must be the post-state of the signature block");

    final BeaconStateAltair stateAltair = BeaconStateAltair.required(state);

    return createLightClientBootstrap(
        block,
        stateAltair.getCurrentSyncCommittee(),
        stateAltair.createCurrentSyncCommitteeProof());
  }

  public LightClientBootstrap createLightClientBootstrap(
      final SignedBeaconBlock block,
      final SyncCommittee currentSyncCommittee,
      final SszBytes32Vector currentSyncCommitteeBranch) {
    return schemaDefinitionsAltair
        .getLightClientBootstrapSchema()
        .create(headerFromBlock(block), currentSyncCommittee, currentSyncCommitteeBranch);
  }

  /**
   * {@code create_light_client_update}. {@code finalizedBlock} is empty when the update carries no
   * finality information.
   */
  public LightClientUpdate createLightClientUpdate(
      final BeaconState state,
      final SignedBeaconBlock block,
      final BeaconState attestedState,
      final SignedBeaconBlock attestedBlock,
      final Optional<SignedBeaconBlock> finalizedBlock) {

    final UInt64 attestedEpoch = miscHelpers.computeEpochAtSlot(attestedBlock.getSlot());
    checkArgument(
        attestedEpoch.isGreaterThanOrEqualTo(specConfig.getAltairForkEpoch()),
        "Attested state must be at or after the Altair fork");

    final SyncAggregate syncAggregate =
        BeaconBlockBodyAltair.required(block.getMessage().getBody()).getSyncAggregate();
    checkArgument(
        syncAggregate.getSyncCommitteeBits().getBitCount()
            >= specConfig.getMinSyncCommitteeParticipants(),
        "Sync aggregate must have at least MIN_SYNC_COMMITTEE_PARTICIPANTS participants");

    // The headers reconstructed from each state must be the blocks those states came from.
    // hashTreeRoot of a BeaconBlockHeader equals that of its BeaconBlock, so the roots compare
    // directly.
    checkArgument(
        state.getSlot().equals(state.getLatestBlockHeader().getSlot()),
        "State must be processed up to its latest block header");
    checkArgument(
        BeaconBlockHeader.fromState(state).hashTreeRoot().equals(block.getRoot()),
        "State must be the post-state of the signature block");

    checkArgument(
        attestedState.getSlot().equals(attestedState.getLatestBlockHeader().getSlot()),
        "Attested state must be processed up to its latest block header");
    final Bytes32 attestedHeaderRoot = BeaconBlockHeader.fromState(attestedState).hashTreeRoot();
    checkArgument(
        attestedHeaderRoot.equals(attestedBlock.getRoot()),
        "Attested state must be the post-state of the attested block");
    checkArgument(
        attestedHeaderRoot.equals(block.getParentRoot()),
        "Attested block must be the parent of the signature block");

    final LightClientUpdateSchema schema = schemaDefinitionsAltair.getLightClientUpdateSchema();
    final BeaconStateAltair attestedStateAltair = BeaconStateAltair.required(attestedState);

    final boolean samePeriod =
        syncCommitteePeriodAtSlot(attestedBlock.getSlot())
            .equals(syncCommitteePeriodAtSlot(block.getSlot()));
    final SyncCommittee nextSyncCommittee =
        samePeriod
            ? attestedStateAltair.getNextSyncCommittee()
            : schema.getNextSyncCommitteeSchema().getDefault();
    final SszBytes32Vector nextSyncCommitteeBranch =
        samePeriod
            ? attestedStateAltair.createNextSyncCommitteeProof()
            : schema.getSyncCommitteeBranchSchema().getDefault();

    final LightClientHeader defaultHeader =
        schemaDefinitionsAltair.getLightClientHeaderSchema().getDefault();
    final LightClientHeader finalizedHeader;
    final SszBytes32Vector finalityBranch;
    if (finalizedBlock.isPresent()) {
      final SignedBeaconBlock finalized = finalizedBlock.get();
      if (finalized.getSlot().isGreaterThan(UInt64.ZERO)) {
        checkArgument(
            finalized.getRoot().equals(attestedState.getFinalizedCheckpoint().getRoot()),
            "Finalized block must match the attested state's finalized checkpoint");
        finalizedHeader = headerFromBlock(finalized);
      } else {
        checkArgument(
            attestedState.getFinalizedCheckpoint().getRoot().isZero(),
            "Genesis finalized block requires a zeroed finalized checkpoint root");
        finalizedHeader = defaultHeader;
      }
      finalityBranch = attestedStateAltair.createFinalityBranchProof();
    } else {
      finalizedHeader = defaultHeader;
      finalityBranch = schema.getFinalityBranchSchema().getDefault();
    }

    return schema.create(
        headerFromBlock(attestedBlock),
        nextSyncCommittee,
        nextSyncCommitteeBranch,
        finalizedHeader,
        finalityBranch,
        syncAggregate,
        SszUInt64.of(block.getSlot()));
  }

  public LightClientUpdate withFinalizedHeader(
      final LightClientUpdate update,
      final SszBytes32Vector finalityBranch,
      final SignedBeaconBlock finalizedBlock) {
    return schemaDefinitionsAltair
        .getLightClientUpdateSchema()
        .create(
            update.getAttestedHeader(),
            update.getNextSyncCommittee(),
            update.getNextSyncCommitteeBranch(),
            headerFromBlock(finalizedBlock),
            finalityBranch,
            update.getSyncAggregate(),
            update.getSignatureSlot());
  }

  /** {@code create_light_client_finality_update}. */
  public LightClientFinalityUpdate createLightClientFinalityUpdate(final LightClientUpdate update) {
    return schemaDefinitionsAltair
        .getLightClientFinalityUpdateSchema()
        .create(
            update.getAttestedHeader(),
            update.getFinalizedHeader(),
            update.getFinalityBranch(),
            update.getSyncAggregate(),
            update.getSignatureSlot());
  }

  /** {@code create_light_client_optimistic_update}. */
  public LightClientOptimisticUpdate createLightClientOptimisticUpdate(
      final LightClientUpdate update) {
    return schemaDefinitionsAltair
        .getLightClientOptimisticUpdateSchema()
        .create(update.getAttestedHeader(), update.getSyncAggregate(), update.getSignatureSlot());
  }

  private UInt64 syncCommitteePeriodAtSlot(final UInt64 slot) {
    return syncCommitteeUtil.computeSyncCommitteePeriod(miscHelpers.computeEpochAtSlot(slot));
  }

  /** {@code block_to_light_client_header}. */
  private LightClientHeader headerFromBlock(final SignedBeaconBlock block) {
    final LightClientHeaderSchema<?> schema = schemaDefinitionsAltair.getLightClientHeaderSchema();
    final BeaconBlock message = block.getMessage();
    final BeaconBlockHeader header = BeaconBlockHeader.fromBlock(message);
    final BeaconBlockBody body = message.getBody();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(block.getSlot());

    if (schema instanceof LightClientHeaderSchemaGloas lightClientHeaderSchemaGloas) {
      final Bytes32 executionBlockHash;
      final long gIndex;
      if (epoch.isGreaterThanOrEqualTo(specConfig.getGloasForkEpoch())) {
        executionBlockHash =
            BeaconBlockBodyGloas.required(body)
                .getSignedExecutionPayloadBid()
                .getMessage()
                .getParentBlockHash();
        gIndex = EXECUTION_BLOCK_HASH_GINDEX_GLOAS;
      } else if (epoch.isGreaterThanOrEqualTo(specConfig.getCapellaForkEpoch())) {
        executionBlockHash =
            BeaconBlockBodyBellatrix.required(body).getExecutionPayload().getBlockHash();
        gIndex =
            epoch.isGreaterThanOrEqualTo(specConfig.getDenebForkEpoch())
                ? EXECUTION_BLOCK_HASH_GINDEX_DENEB
                : EXECUTION_BLOCK_HASH_GINDEX;
      } else {
        return lightClientHeaderSchemaGloas.create(header);
      }
      return lightClientHeaderSchemaGloas.create(
          header,
          SszBytes32.of(executionBlockHash),
          createProof(lightClientHeaderSchemaGloas.getExecutionBranchSchema(), body, gIndex));
    }

    if (schema instanceof LightClientHeaderSchemaCapella lightClientHeaderSchemaCapella) {
      if (epoch.isLessThan(specConfig.getCapellaForkEpoch())) {
        return lightClientHeaderSchemaCapella.create(header);
      }
      final ExecutionPayloadHeader executionPayloadHeader =
          executionHeaderFromPayload(body.getOptionalExecutionPayload().orElseThrow());
      return lightClientHeaderSchemaCapella.create(
          header,
          executionPayloadHeader,
          createProof(
              lightClientHeaderSchemaCapella.getExecutionBranchSchema(),
              body,
              EXECUTION_PAYLOAD_GINDEX));
    }

    return schema.create(header);
  }

  /** {@code compute_merkle_proof}. */
  private SszBytes32Vector createProof(
      final SszBytes32VectorSchema<SszBytes32Vector> branchSchema,
      final BeaconBlockBody body,
      final long gIndex) {
    final List<Bytes32> proof = MerkleUtil.constructMerkleProof(body.getBackingNode(), gIndex);
    final List<SszBytes32> branch =
        new ArrayList<>(
            Collections.nCopies(
                branchSchema.getLength() - proof.size(), SszBytes32.of(Bytes32.ZERO)));
    proof.forEach(node -> branch.add(SszBytes32.of(node)));
    return branchSchema.createFromElements(branch);
  }

  private ExecutionPayloadHeader executionHeaderFromPayload(final ExecutionPayload payload) {
    final Optional<ExecutionPayloadDeneb> denebPayload = payload.toVersionDeneb();
    final UInt64 blobGasUsed =
        denebPayload.map(ExecutionPayloadDeneb::getBlobGasUsed).orElse(UInt64.ZERO);
    final UInt64 excessBlobGas =
        denebPayload.map(ExecutionPayloadDeneb::getExcessBlobGas).orElse(UInt64.ZERO);
    return schemaDefinitionsAltair
        .toVersionCapella()
        .orElseThrow()
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            builder ->
                builder
                    .parentHash(payload.getParentHash())
                    .feeRecipient(payload.getFeeRecipient())
                    .stateRoot(payload.getStateRoot())
                    .receiptsRoot(payload.getReceiptsRoot())
                    .logsBloom(payload.getLogsBloom())
                    .prevRandao(payload.getPrevRandao())
                    .blockNumber(payload.getBlockNumber())
                    .gasLimit(payload.getGasLimit())
                    .gasUsed(payload.getGasUsed())
                    .timestamp(payload.getTimestamp())
                    .extraData(payload.getExtraData())
                    .baseFeePerGas(payload.getBaseFeePerGas())
                    .blockHash(payload.getBlockHash())
                    .transactionsRoot(payload.getTransactions().hashTreeRoot())
                    .withdrawalsRoot(() -> payload.getOptionalWithdrawalsRoot().orElseThrow())
                    .blobGasUsed(() -> blobGasUsed)
                    .excessBlobGas(() -> excessBlobGas));
  }
}
