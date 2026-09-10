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

package tech.pegasys.teku.spec.logic.common.util;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionPayloadProposalUtil {

  private final SchemaDefinitionsGloas schemaDefinitions;

  public ExecutionPayloadProposalUtil(final SchemaDefinitionsGloas schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
  }

  public record ExecutionPayloadProposalData(
      ExecutionPayload executionPayload,
      ExecutionRequests executionRequests,
      SszList<SszKZGCommitment> kzgCommitments) {}

  public SafeFuture<ExecutionPayloadEnvelope> createNewUnsignedExecutionPayload(
      final BeaconBlockAndState blockAndState,
      final SafeFuture<ExecutionPayloadProposalData> executionPayloadProposalDataFuture) {
    return executionPayloadProposalDataFuture.thenApply(
        executionPayloadProposalData ->
            schemaDefinitions
                .getExecutionPayloadEnvelopeSchema()
                .create(
                    executionPayloadProposalData.executionPayload,
                    executionPayloadProposalData.executionRequests,
                    blockAndState
                        .getBlock()
                        .getBody()
                        .getOptionalSignedExecutionPayloadBid()
                        .orElseThrow()
                        .getMessage()
                        .getBuilderIndex(),
                    blockAndState.getRoot(),
                    blockAndState.getBlock().getParentRoot()));
  }
}
