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

package tech.pegasys.teku.spec.util;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;

public class BeaconBlockBuilder {

  private final SpecVersion spec;
  private final DataStructureUtil dataStructureUtil;

  private UInt64 slot;
  private SszList<ProposerSlashing> proposerSlashings;
  private SyncAggregate syncAggregate;
  private ExecutionPayload executionPayload;

  private SszList<SignedBlsToExecutionChange> blsToExecutionChanges;
  private SszList<AttesterSlashing> attesterSlashings;

  private SszList<Attestation> attestations;

  public BeaconBlockBuilder(final SpecVersion spec, final DataStructureUtil dataStructureUtil) {
    this.spec = spec;
    this.dataStructureUtil = dataStructureUtil;
    this.syncAggregate = dataStructureUtil.randomSyncAggregate();
  }

  public BeaconBlockBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  public BeaconBlockBuilder syncAggregate(final SyncAggregate syncAggregate) {
    this.syncAggregate = syncAggregate;
    return this;
  }

  public BeaconBlockBuilder blsToExecutionChanges(
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
    this.blsToExecutionChanges = blsToExecutionChanges;
    return this;
  }

  public BeaconBlockBuilder executionPayload(final ExecutionPayload executionPayload) {
    this.executionPayload = executionPayload;
    return this;
  }

  public BeaconBlockBuilder proposerSlashings(final SszList<ProposerSlashing> proposerSlashings) {
    this.proposerSlashings = proposerSlashings;
    return this;
  }

  public BeaconBlockBuilder attesterSlashings(final SszList<AttesterSlashing> attesterSlashings) {
    this.attesterSlashings = attesterSlashings;
    return this;
  }

  public BeaconBlockBuilder attestations(final SszList<Attestation> attestations) {
    this.attestations = attestations;
    return this;
  }

  public SafeFuture<BeaconBlock> build() {
    final BeaconBlockBodySchema<?> bodySchema =
        spec.getSchemaDefinitions().getBeaconBlockBodySchema();

    if (syncAggregate == null) {
      syncAggregate = dataStructureUtil.randomSyncAggregate();
    }
    if (executionPayload == null) {
      executionPayload =
          spec.getSchemaDefinitions()
              .toVersionBellatrix()
              .map(definitions -> definitions.getExecutionPayloadSchema().getDefault())
              .orElse(null);
    }
    if (blsToExecutionChanges == null) {
      blsToExecutionChanges =
          bodySchema
              .toVersionCapella()
              .map(schema -> schema.getBlsToExecutionChangesSchema().getDefault())
              .orElse(null);
    }
    if (proposerSlashings == null) {
      proposerSlashings = bodySchema.getProposerSlashingsSchema().getDefault();
    }
    if (attesterSlashings == null) {
      attesterSlashings = bodySchema.getAttesterSlashingsSchema().getDefault();
    }
    if (attestations == null) {
      attestations = bodySchema.getAttestationsSchema().getDefault();
    }
    final UInt64 blockSlot = slot != null ? slot : dataStructureUtil.randomUInt64();
    return bodySchema
        .createBlockBody(
            builder -> {
              builder
                  .randaoReveal(dataStructureUtil.randomSignature())
                  .eth1Data(dataStructureUtil.randomEth1Data())
                  .graffiti(dataStructureUtil.randomBytes32())
                  .attestations(attestations)
                  .proposerSlashings(proposerSlashings)
                  .attesterSlashings(attesterSlashings)
                  .deposits(bodySchema.getDepositsSchema().getDefault())
                  .voluntaryExits(bodySchema.getVoluntaryExitsSchema().getDefault());
              if (builder.supportsSyncAggregate()) {
                builder.syncAggregate(syncAggregate);
              }
              if (builder.supportsExecutionPayload()) {
                builder.executionPayload(executionPayload);
              }
              if (builder.supportsBlsToExecutionChanges()) {
                builder.blsToExecutionChanges(blsToExecutionChanges);
              }
              if (builder.supportsKzgCommitments()) {
                builder.blobKzgCommitments(dataStructureUtil.randomBlobKzgCommitments());
              }
              if (builder.supportsExecutionRequests()) {
                builder.executionRequests(dataStructureUtil.randomExecutionRequests(blockSlot));
              }
              return SafeFuture.COMPLETE;
            })
        .thenApply(
            blockBody ->
                spec.getSchemaDefinitions()
                    .getBeaconBlockSchema()
                    .create(
                        blockSlot,
                        dataStructureUtil.randomUInt64(),
                        dataStructureUtil.randomBytes32(),
                        dataStructureUtil.randomBytes32(),
                        blockBody));
  }
}
