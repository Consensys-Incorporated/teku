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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.LARGE_PENALTY;
import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.SMALL_PENALTY;
import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.SMALL_REWARD;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;

public class PeerScoringConflictResolutionStrategy implements ConflictResolutionStrategy {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void verifyBatch(final Batch batch, final SyncSource originalSource) {
    LOG.debug(
        "Applying minor penalty to peer because of contested batch {} and redownloading", batch);
    // We aren't sure if this peer is providing bad blocks or some other peer, so apply a small
    // penalty to the peer and re-download the data for the batch. If the peer is honest, it should
    // have more confirmed batches than contested and the small penalty to reputation won't matter.
    // If it's dishonest the penalties will add up until it is disconnected.
    originalSource.adjustReputation(SMALL_PENALTY);
    batch.markAsInvalid();
  }

  @Override
  public void reportInvalidBatch(final Batch batch, final SyncSource source) {
    LOG.debug("Penalising peer {} for providing invalid batch data {}", source, batch);
    source.adjustReputation(LARGE_PENALTY);
  }

  @Override
  public void reportInconsistentBatch(final Batch batch, final SyncSource source) {
    LOG.debug("Penalising peer {} for providing inconsistent batch data {}", source, batch);
    source.adjustReputation(SMALL_PENALTY);
  }

  @Override
  public void reportConfirmedBatch(final Batch batch, final SyncSource source) {
    source.adjustReputation(SMALL_REWARD);
  }
}
