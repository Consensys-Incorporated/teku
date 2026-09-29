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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.List;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

class ForkChoicePayloadExecutorHeze extends ForkChoicePayloadExecutorGloas {

  private final List<InclusionList> inclusionLists;

  ForkChoicePayloadExecutorHeze(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionLayerChannel executionLayer,
      final List<InclusionList> inclusionLists) {
    super(signedEnvelope, executionLayer);
    this.inclusionLists = inclusionLists;
  }

  public static ForkChoicePayloadExecutorHeze create(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionLayerChannel executionLayer,
      final List<InclusionList> inclusionLists) {
    return new ForkChoicePayloadExecutorHeze(signedEnvelope, executionLayer, inclusionLists);
  }

  @Override
  protected NewPayloadRequest preparePayloadToExecute(final NewPayloadRequest payloadToExecute) {
    if (payloadToExecute.getInclusionList().isPresent()) {
      return payloadToExecute;
    }
    return new NewPayloadRequest(
        payloadToExecute.getExecutionPayload(),
        payloadToExecute.getVersionedHashes().orElseThrow(),
        payloadToExecute.getParentBeaconBlockRoot().orElseThrow(),
        payloadToExecute.getExecutionRequests().orElseThrow(),
        getInclusionListTransactions());
  }

  private List<Transaction> getInclusionListTransactions() {
    return inclusionLists.stream()
        .map(InclusionList::getTransactions)
        .flatMap(transactions -> transactions.stream())
        .distinct()
        .toList();
  }
}
