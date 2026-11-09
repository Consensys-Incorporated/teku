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

package tech.pegasys.teku.statetransition.datacolumns;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.statetransition.datacolumns.util.CancelableFuture;

public class DelayedCanonicalBlockResolver implements CanonicalBlockResolver {

  private final CanonicalBlockResolver delegate;
  private final AsyncRunner asyncRunner;
  private Duration delay;

  public DelayedCanonicalBlockResolver(
      final CanonicalBlockResolver delegate, final AsyncRunner asyncRunner, final Duration delay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.delay = delay;
  }

  public void setDelay(final Duration delay) {
    this.delay = delay;
  }

  private <T> SafeFuture<T> delay(final Supplier<SafeFuture<T>> futSupplier) {
    return CancelableFuture.of(asyncRunner.getDelayedFuture(delay))
        .thenComposeCancelable(true, true, __ -> futSupplier.get());
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> getBlockAtSlot(final UInt64 slot) {
    return delay(() -> delegate.getBlockAtSlot(slot));
  }
}
