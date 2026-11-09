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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceStateProvider {
  private final EventThread forkChoiceExecutor;
  private final RecentChainData recentChainData;

  /**
   * Supplies the fast confirmation {@code confirmed_root} to use as the FCU {@code safe_block_hash}
   * source ({@code get_safe_execution_block_hash}), or empty when fast confirmation is disabled (in
   * which case the safe hash defaults to the justified block, as before).
   *
   * <p>Thread-safety: {@code get()} is invoked on the fork-choice event thread, whereas the
   * confirmed root it returns is produced on the fast confirmation runner thread. The supplier
   * implementation is therefore responsible for publishing that value safely across threads (e.g.
   * via a volatile/atomic holder); this class only reads the latest published value and never
   * mutates it.
   */
  private final Supplier<Optional<Bytes32>> fastConfirmationSafeBlockRootSupplier;

  public ForkChoiceStateProvider(
      final EventThread forkChoiceExecutor, final RecentChainData recentChainData) {
    this(forkChoiceExecutor, recentChainData, Optional::empty);
  }

  public ForkChoiceStateProvider(
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final Supplier<Optional<Bytes32>> fastConfirmationSafeBlockRootSupplier) {
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.recentChainData = recentChainData;
    this.fastConfirmationSafeBlockRootSupplier = fastConfirmationSafeBlockRootSupplier;
  }

  public SafeFuture<ForkChoiceState> getForkChoiceStateAsync() {
    return forkChoiceExecutor.execute(() -> internalGetForkChoiceState(Optional.empty()));
  }

  public ForkChoiceState getForkChoiceStateSync(final Optional<ChainHead> proposingOnHead) {
    forkChoiceExecutor.checkOnEventThread();
    return internalGetForkChoiceState(proposingOnHead);
  }

  private ForkChoiceState internalGetForkChoiceState(final Optional<ChainHead> proposingOnHead) {
    return recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .getForkChoiceState(
            proposingOnHead,
            recentChainData.getCurrentEpoch().orElseThrow(),
            recentChainData.getJustifiedCheckpoint().orElseThrow(),
            recentChainData.getFinalizedCheckpoint().orElseThrow(),
            fastConfirmationSafeBlockRootSupplier.get());
  }
}
