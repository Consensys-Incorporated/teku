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

package tech.pegasys.teku.networking.eth2.gossip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.gossip.subnets.ExecutionProofSubnetSubscriptions;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;

public class ExecutionProofGossipManager implements GossipManager {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionProofSubnetSubscriptions executionProofSubnetSubscriptions;

  public ExecutionProofGossipManager(
      final ExecutionProofSubnetSubscriptions executionProofSubnetSubscriptions) {
    this.executionProofSubnetSubscriptions = executionProofSubnetSubscriptions;

    for (int i = 0; i < Constants.MAX_EXECUTION_PROOF_SUBNETS; i++) {
      executionProofSubnetSubscriptions.subscribeToSubnetId(i);
    }
  }

  @Override
  public void subscribe() {
    executionProofSubnetSubscriptions.subscribe();
  }

  @Override
  public void unsubscribe() {
    executionProofSubnetSubscriptions.unsubscribe();
  }

  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return true;
  }

  public void subscribeToSubnetId(final int subnetId) {
    executionProofSubnetSubscriptions.subscribeToSubnetId(subnetId);
  }

  public void unsubscribeFromSubnetId(final int subnetId) {
    executionProofSubnetSubscriptions.unsubscribeFromSubnetId(subnetId);
  }

  public void publish(final ExecutionProof executionProof) {
    executionProofSubnetSubscriptions
        .gossip(executionProof)
        .finish(
            __ -> LOG.trace("{} published successfully", executionProof),
            error ->
                LOG.trace("Failed to publish {}, error: {}", executionProof, error.getMessage()));
  }
}
