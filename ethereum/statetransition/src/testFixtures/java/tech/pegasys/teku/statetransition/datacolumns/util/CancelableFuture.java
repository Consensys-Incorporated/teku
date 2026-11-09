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

package tech.pegasys.teku.statetransition.datacolumns.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * {@link SafeFuture} subclass which is capable of propagating cancel() calls to upstream future(s)
 */
public class CancelableFuture<U> extends SafeFuture<U> {

  public static <U> CancelableFuture<U> of(final CompletionStage<U> stage) {
    if (stage instanceof CancelableFuture) {
      return (CancelableFuture<U>) stage;
    }
    final CancelableFuture<U> cancelableFuture = new CancelableFuture<>();
    propagateResult(stage, cancelableFuture);
    return cancelableFuture;
  }

  @Override
  public <T> CancelableFuture<T> newIncompleteFuture() {
    return new CancelableFuture<>();
  }

  private void propagateCancelToThis(final SafeFuture<?> downstreamFuture) {
    downstreamFuture.finish(
        __ -> {
          if (downstreamFuture.isCancelled()) {
            this.cancel(true);
          }
        });
  }

  public <R> CancelableFuture<R> thenComposeCancelable(
      final boolean propagateCancelToThis,
      final boolean propagateCancelToComposable,
      final Function<? super U, ? extends CompletableFuture<R>> fn) {

    final Function<? super U, ? extends CompletionStage<R>> fn1;

    AtomicReference<CompletableFuture<?>> suppliedFuture = new AtomicReference<>();
    if (propagateCancelToComposable) {
      fn1 =
          s -> {
            CompletableFuture<R> composeFuture = fn.apply(s);
            suppliedFuture.set(composeFuture);
            return composeFuture;
          };
    } else {
      fn1 = fn;
    }
    CancelableFuture<R> ret = (CancelableFuture<R>) super.thenCompose(fn1);

    if (propagateCancelToComposable) {
      ret.finish(
          __ -> {
            CompletableFuture<?> composeFuture = suppliedFuture.get();
            if (ret.isCancelled() && composeFuture != null) {
              composeFuture.cancel(true);
            }
          });
    }

    if (propagateCancelToThis) {
      propagateCancelToThis(ret);
    }

    return ret;
  }
}
