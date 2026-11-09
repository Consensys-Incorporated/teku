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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

public class SyncCommitteeProductionDuty {
  private final ForkProvider forkProvider;
  private final Collection<ValidatorAndCommitteeIndices> assignments;

  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;

  public SyncCommitteeProductionDuty(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final Collection<ValidatorAndCommitteeIndices> assignments) {
    this.forkProvider = forkProvider;
    this.assignments = assignments;
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
  }

  public SafeFuture<DutyResult> produceMessages(final UInt64 slot, final Bytes32 blockRoot) {
    if (assignments.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> produceMessages(forkInfo, slot, blockRoot))
        .exceptionally(
            error ->
                DutyResult.forError(
                    assignments.stream()
                        .map(assignment -> assignment.getValidator().getPublicKey())
                        .collect(Collectors.toSet()),
                    error));
  }

  private SafeFuture<DutyResult> produceMessages(
      final ForkInfo forkInfo, final UInt64 slot, final Bytes32 blockRoot) {
    return SafeFuture.collectAll(
            assignments.stream()
                .map(assignment -> produceMessage(forkInfo, slot, blockRoot, assignment)))
        .thenCompose(this::sendSignatures);
  }

  private SafeFuture<DutyResult> sendSignatures(
      final List<ProductionResult<SyncCommitteeMessage>> results) {
    return ProductionResult.send(results, validatorApiChannel::sendSyncCommitteeMessages);
  }

  private SafeFuture<ProductionResult<SyncCommitteeMessage>> produceMessage(
      final ForkInfo forkInfo,
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment) {
    final BLSPublicKey validatorPublicKey = assignment.getValidator().getPublicKey();
    return assignment
        .getValidator()
        .getSigner()
        .signSyncCommitteeMessage(slot, blockRoot, forkInfo)
        .thenApply(
            signature ->
                ProductionResult.success(
                    validatorPublicKey,
                    blockRoot,
                    createSyncCommitteeMessage(slot, blockRoot, assignment, signature)))
        .exceptionally(error -> ProductionResult.failure(validatorPublicKey, error));
  }

  private SyncCommitteeMessage createSyncCommitteeMessage(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment,
      final BLSSignature signature) {
    return SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions())
        .getSyncCommitteeMessageSchema()
        .create(slot, blockRoot, UInt64.valueOf(assignment.getValidatorIndex()), signature);
  }
}
