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

package tech.pegasys.teku.validator.client.duties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class ProductionResult<T> {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<BLSPublicKey> validatorPublicKeys;
  private final DutyResult result;
  private final Optional<T> message;

  private ProductionResult(
      final Set<BLSPublicKey> validatorPublicKeys, final Bytes32 blockRoot, final T message) {
    this.validatorPublicKeys = validatorPublicKeys;
    this.result = DutyResult.success(blockRoot);
    this.message = Optional.of(message);
  }

  private ProductionResult(final Set<BLSPublicKey> validatorPublicKeys, final DutyResult result) {
    this.validatorPublicKeys = validatorPublicKeys;
    this.result = result;
    this.message = Optional.empty();
  }

  public static <T> ProductionResult<T> success(
      final BLSPublicKey validatorPublicKey, final Bytes32 blockRoot, final T message) {
    return new ProductionResult<>(Set.of(validatorPublicKey), blockRoot, message);
  }

  public static <T> ProductionResult<T> failure(
      final BLSPublicKey validatorPublicKey, final Throwable error) {
    return failure(Set.of(validatorPublicKey), error);
  }

  public static <T> ProductionResult<T> failure(
      final Set<BLSPublicKey> validatorPublicKeys, final Throwable error) {
    return failure(validatorPublicKeys, DutyResult.forError(validatorPublicKeys, error));
  }

  public static <T> ProductionResult<T> failure(
      final Set<BLSPublicKey> validatorPublicKeys, final DutyResult result) {
    return new ProductionResult<>(validatorPublicKeys, result);
  }

  public static <T> ProductionResult<T> noop(final BLSPublicKey validatorPublicKey) {
    return new ProductionResult<>(Set.of(validatorPublicKey), DutyResult.NOOP);
  }

  public static <T> ProductionResult<T> noop(final Set<BLSPublicKey> validatorPublicKeys) {
    return new ProductionResult<>(validatorPublicKeys, DutyResult.NOOP);
  }

  public static <T> SafeFuture<DutyResult> send(
      final List<ProductionResult<T>> results,
      final Function<List<T>, SafeFuture<List<SubmitDataError>>> submitFunction) {
    // Split into results that produced a message to send vs those that failed already
    final List<ProductionResult<T>> messageCreated =
        results.stream()
            .filter(ProductionResult::producedMessage)
            .collect(Collectors.toCollection(ArrayList::new)); // mutable List is required
    final DutyResult combinedFailures =
        combineResults(results.stream().filter(ProductionResult::failedToProduceMessage).toList());

    if (messageCreated.isEmpty()) {
      return SafeFuture.completedFuture(combinedFailures);
    }

    return submitFunction
        .apply(messageCreated.stream().map(result -> result.getMessage().orElseThrow()).toList())
        .thenApply(
            errors -> {
              errors.forEach(error -> replaceResult(messageCreated, error));
              return combineResults(messageCreated).combine(combinedFailures);
            });
  }

  private static <T> void replaceResult(
      final List<ProductionResult<T>> sentResults, final SubmitDataError error) {
    if (error.index().isGreaterThanOrEqualTo(sentResults.size())) {
      LOG.error(
          "Beacon node reported an error at index {} with message '{}' but only {} messages were sent",
          error.index(),
          error.message(),
          sentResults.size());
      return;
    }
    final int index = error.index().intValue();
    final ProductionResult<T> originalResult = sentResults.get(index);
    sentResults.set(
        index,
        ProductionResult.failure(
            originalResult.getValidatorPublicKeys(),
            new RestApiReportedException(error.message())));
  }

  private static <T> DutyResult combineResults(final List<ProductionResult<T>> results) {
    return results.stream()
        .map(ProductionResult::getResult)
        .reduce(DutyResult::combine)
        .orElse(DutyResult.NOOP);
  }

  public Set<BLSPublicKey> getValidatorPublicKeys() {
    return validatorPublicKeys;
  }

  public DutyResult getResult() {
    return result;
  }

  public boolean producedMessage() {
    return message.isPresent();
  }

  public boolean failedToProduceMessage() {
    return message.isEmpty();
  }

  public Optional<T> getMessage() {
    return message;
  }
}
