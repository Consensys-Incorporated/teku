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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;

public class CompletionAwareResponseCallback<T> implements ResponseCallback<T> {
  private static final Logger LOG = LogManager.getLogger();

  private final ResponseCallback<T> delegate;
  private final List<Runnable> completionActions = new ArrayList<>();
  private final AtomicBoolean completionActionsRan = new AtomicBoolean(false);

  public CompletionAwareResponseCallback(final ResponseCallback<T> delegate) {
    this.delegate = delegate;
  }

  public void onCompletion(final Runnable action) {
    completionActions.add(action);
  }

  @Override
  public SafeFuture<Void> respond(final T data) {
    return delegate.respond(data);
  }

  @Override
  public void respondAndCompleteSuccessfully(final T data) {
    try {
      delegate.respondAndCompleteSuccessfully(data);
    } finally {
      runCompletionActions();
    }
  }

  @Override
  public void completeSuccessfully() {
    try {
      delegate.completeSuccessfully();
    } finally {
      runCompletionActions();
    }
  }

  @Override
  public void completeWithErrorResponse(final RpcException error) {
    try {
      delegate.completeWithErrorResponse(error);
    } finally {
      runCompletionActions();
    }
  }

  @Override
  public void completeWithUnexpectedError(final Throwable error) {
    try {
      delegate.completeWithUnexpectedError(error);
    } finally {
      runCompletionActions();
    }
  }

  private void runCompletionActions() {
    if (!completionActionsRan.compareAndSet(false, true)) {
      return;
    }
    for (final Runnable action : completionActions) {
      try {
        action.run();
      } catch (final Throwable t) {
        LOG.debug("Failed to run completion action {}", action, t);
      }
    }
  }
}
