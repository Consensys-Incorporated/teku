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

import tech.pegasys.teku.networking.eth2.peers.SyncSource;

public interface ConflictResolutionStrategy {

  /**
   * Verify the contents of the specified batch and apply any reputation changes required.
   *
   * <p>If the batch is found to be invalid, call {@link Batch#markAsInvalid()}, otherwise call
   * {@link Batch#markFirstBlockConfirmed()} and/or {@link Batch#markLastBlockConfirmed()} as
   * appropriate.
   *
   * @param batch the batch to verify
   * @param originalSource the {@link SyncSource} that original provided the data for the batch
   */
  void verifyBatch(Batch batch, SyncSource originalSource);

  /**
   * Report that a batch was detected as invalid so that any reputation changes can be applied.
   *
   * <p>Note that this is not called when {@link #verifyBatch(Batch, SyncSource)} marks the batch as
   * invalid.
   *
   * @param batch the invalid batch
   * @param source the source that provided the batch data
   */
  void reportInvalidBatch(Batch batch, SyncSource source);

  /**
   * Report that a batch is inconsistent so that any reputation changes can be applied. An
   * inconsistent batch is still valid, so penalisation shouldn't be very harsh. For example, a peer
   * could send blocks for a certain range, but then provide more blob sidecars than expected for
   * the same range.
   *
   * @param batch the inconsistent batch
   * @param source the source that provided the batch data
   */
  void reportInconsistentBatch(Batch batch, SyncSource source);

  /**
   * Report that a batch was confirmed as part of the target chain. This is called before the blocks
   * are actually imported and only indicates that the first and last block matches the
   *
   * @param batch the confirmed batch
   * @param source the
   */
  void reportConfirmedBatch(Batch batch, SyncSource source);
}
