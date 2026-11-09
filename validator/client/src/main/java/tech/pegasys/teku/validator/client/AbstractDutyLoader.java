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

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public abstract class AbstractDutyLoader<D, S extends ScheduledDuties> implements DutyLoader<S> {

  private static final Logger LOG = LogManager.getLogger();
  protected final OwnedValidators validators;
  private final ValidatorIndexProvider validatorIndexProvider;

  protected AbstractDutyLoader(
      final OwnedValidators validators, final ValidatorIndexProvider validatorIndexProvider) {
    this.validators = validators;
    this.validatorIndexProvider = validatorIndexProvider;
  }

  @Override
  public SafeFuture<Optional<S>> loadDutiesForEpoch(final UInt64 epoch) {
    LOG.trace("Requesting duties for epoch {}", epoch);
    return validatorIndexProvider
        .getValidatorIndices()
        .thenCompose(
            validatorIndices -> {
              if (validatorIndices.isEmpty()) {
                LOG.trace("No duties because no validator indices are known");
                return SafeFuture.completedFuture(Optional.empty());
              }
              return requestDuties(epoch, validatorIndices)
                  .thenApply(
                      maybeDuties ->
                          maybeDuties.orElseThrow(
                              () ->
                                  new NodeDataUnavailableException(
                                      "Duties could not be calculated because chain data was not yet available")))
                  .thenCompose(duties -> scheduleAllDuties(epoch, duties))
                  .thenApply(Optional::of);
            });
  }

  protected abstract SafeFuture<Optional<D>> requestDuties(
      UInt64 epoch, IntCollection validatorIndices);

  protected abstract SafeFuture<S> scheduleAllDuties(UInt64 epoch, D duties);
}
