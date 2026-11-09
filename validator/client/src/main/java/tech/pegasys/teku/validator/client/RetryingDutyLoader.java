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

package tech.pegasys.teku.validator.client;

import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.Throttler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class RetryingDutyLoader<S extends ScheduledDuties> implements DutyLoader<S> {
  private static final Logger LOG = LogManager.getLogger();

  private final Throttler<Logger> limitedLogger = new Throttler<>(LOG, UInt64.valueOf(60));

  private final DutyLoader<S> delegate;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;

  public RetryingDutyLoader(
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final DutyLoader<S> delegate) {
    this.delegate = delegate;
    this.timeProvider = timeProvider;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<Optional<S>> loadDutiesForEpoch(final UInt64 epoch) {
    final SafeFuture<Optional<S>> duties = new SafeFuture<>();
    requestDuties(epoch, duties).propagateTo(duties);
    return duties;
  }

  @Override
  public String getDutyType() {
    return delegate.getDutyType();
  }

  private SafeFuture<Optional<S>> requestDuties(
      final UInt64 epoch, final SafeFuture<Optional<S>> cancellable) {
    LOG.trace("Request duties for epoch {}", epoch);
    return delegate
        .loadDutiesForEpoch(epoch)
        .exceptionallyCompose(
            error -> {
              if (cancellable.isCancelled()) {
                LOG.trace("Request for duties for epoch {} cancelled.", epoch);
                return SafeFuture.failedFuture(error);
              }
              final Throwable rootCause = Throwables.getRootCause(error);
              switch (rootCause) {
                case NodeSyncingException ignored ->
                    LOG.trace(
                        "Unable to schedule duties for epoch {} because node was syncing. Retrying.",
                        epoch);
                case NodeDataUnavailableException ignored ->
                    LOG.trace(
                        "Unable to schedule duties for epoch {} because required state was not yet available. Retrying.",
                        epoch);
                case RemoteServiceNotAvailableException ignored ->
                    LOG.trace(
                        "Unable to schedule duties for epoch {} - service not available. Retrying.",
                        epoch);
                default ->
                    limitedLogger.invoke(
                        timeProvider.getTimeInSeconds(),
                        (logger) ->
                            logger.error(
                                String.format(
                                    "Failed to request validator duties for epoch %s. Retrying after delay.",
                                    epoch),
                                rootCause));
              }
              // Short delay before retrying as loading duties is very time sensitive
              return asyncRunner.runAfterDelay(
                  () -> requestDuties(epoch, cancellable), Duration.ofSeconds(1));
            });
  }
}
