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

package tech.pegasys.teku.statetransition;

import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.MessageWithValidatorId;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class MappedOperationPool<T extends MessageWithValidatorId> implements OperationPool<T> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_OPERATION_POOL_SIZE = 10_000;
  private final Map<UInt64, OperationPoolEntry<T>> operations;
  private final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier;
  private final OperationValidator<T> operationValidator;
  private final Subscribers<OperationAddedSubscriber<T>> subscribers = Subscribers.create(true);
  private final LabelledMetric<Counter> validationReasonCounter;

  private final String metricType;

  private final TimeProvider timeProvider;

  public MappedOperationPool(
      final String metricType,
      final MetricsSystem metricsSystem,
      final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier,
      final OperationValidator<T> operationValidator,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider) {
    this(
        metricType,
        metricsSystem,
        slotToSszListSchemaSupplier,
        operationValidator,
        asyncRunner,
        timeProvider,
        DEFAULT_OPERATION_POOL_SIZE);
  }

  public MappedOperationPool(
      final String metricType,
      final MetricsSystem metricsSystem,
      final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier,
      final OperationValidator<T> operationValidator,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final int operationPoolSize) {

    this.slotToSszListSchemaSupplier = slotToSszListSchemaSupplier;
    this.operations = LimitedMap.createSynchronizedLRU(operationPoolSize);
    this.operationValidator = operationValidator;
    this.metricType = metricType;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        OPERATION_POOL_SIZE_METRIC + metricType,
        "Current number of operations in the pool",
        this::size);
    validationReasonCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            OPERATION_POOL_SIZE_VALIDATION_REASON + metricType + "_total",
            "Total number of attempts to add an operation to the pool, broken down by validation result",
            "result");

    this.timeProvider = timeProvider;
    asyncRunner.runWithFixedDelay(
        this::updateLocalSubmissions,
        Duration.ofMinutes(10),
        this::updateLocalSubmissionsErrorHandler);
  }

  private void updateLocalSubmissionsErrorHandler(final Throwable throwable) {
    LOG.debug("Failed to update " + metricType, throwable);
  }

  private void updateLocalSubmissions() {
    final UInt64 staleTime = timeProvider.getTimeInSeconds().minus(Duration.ofHours(2).toSeconds());
    final List<OperationPoolEntry<T>> staleLocalOperations =
        operations.values().stream()
            .filter(OperationPoolEntry::isLocal)
            .filter(entry -> entry.getTimeSubmitted().isLessThanOrEqualTo(staleTime))
            .toList();
    if (!staleLocalOperations.isEmpty()) {
      LOG.info(
          "Re-publishing {} operations that are still in the local {} operation pool",
          staleLocalOperations.size(),
          metricType);
    }
    int i = 0;
    for (OperationPoolEntry<T> entry : staleLocalOperations) {
      if (++i % 200 == 0) {
        try {
          // if we're processing large numbers, don't flood the network...
          Thread.sleep(100);
        } catch (InterruptedException e) {
          LOG.debug(e);
        }
      }
      operationValidator
          .validateForGossip(entry.getMessage())
          .thenAcceptChecked(
              result -> {
                validationReasonCounter.labels(result.code().toString()).inc();
                if (result.code().equals(ValidationResultCode.ACCEPT)) {
                  entry.setTimeSubmitted(timeProvider.getTimeInSeconds());
                  subscribers.forEach(s -> s.onOperationAdded(entry.getMessage(), result, false));
                }
              })
          .finish(err -> LOG.debug("Failed to resubmit local operation", err));
    }
  }

  private static InternalValidationResult rejectForDuplicatedMessage(
      final String metricType, final UInt64 validatorIndex) {
    final String logMessage =
        String.format(
            "Cannot add to %s as validator %s is already in this pool.",
            metricType, validatorIndex);
    LOG.trace(logMessage);
    return InternalValidationResult.create(IGNORE, logMessage);
  }

  @Override
  public void subscribeOperationAdded(final OperationAddedSubscriber<T> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  @Override
  public SszList<T> getItemsForBlock(
      final BeaconState stateAtBlockSlot, final int maxItemsForBlock) {
    return getItemsForBlock(stateAtBlockSlot, maxItemsForBlock, operation -> true, operation -> {});
  }

  @Override
  public SszList<T> getItemsForBlock(
      final BeaconState stateAtBlockSlot,
      final int maxItemsForBlock,
      final Predicate<T> filter,
      final Consumer<T> includedItemConsumer) {
    final SszListSchema<T, ?> schema =
        slotToSszListSchemaSupplier.apply(stateAtBlockSlot.getSlot());
    final long maxItemsToSelect = Math.min(maxItemsForBlock, schema.getMaxLength());

    // Note that iterating through all items does not affect their access time so we are effectively
    // evicting the oldest entries when the size is exceeded as we only ever access via iteration.
    final Collection<T> sortedViableOperations =
        operations.values().stream().sorted().map(OperationPoolEntry::getMessage).toList();
    final List<T> selected = new ArrayList<>();
    for (final T item : sortedViableOperations) {
      if (selected.size() >= maxItemsToSelect) {
        break;
      }
      if (!filter.test(item)) {
        continue;
      }
      final UInt64 validatorIndex = item.getValidatorId();
      if (operationValidator.validateForBlockInclusion(stateAtBlockSlot, item).isEmpty()) {
        selected.add(item);
        includedItemConsumer.accept(item);
      } else {
        // The item is no longer valid to be included in a block so remove it from the pool.
        operations.remove(validatorIndex);
      }
    }
    return schema.createFromElements(selected);
  }

  @Override
  public SafeFuture<InternalValidationResult> addLocal(final T item) {
    final UInt64 validatorIndex = item.getValidatorId();
    LOG.debug("Adding local validator id {} to {}", validatorIndex, metricType);
    if (operations.containsKey(validatorIndex)) {
      return SafeFuture.completedFuture(rejectForDuplicatedMessage(metricType, validatorIndex))
          .thenPeek(result -> validationReasonCounter.labels(result.code().toString()).inc());
    }
    return add(item, false);
  }

  @Override
  public SafeFuture<InternalValidationResult> addRemote(
      final T item, final Optional<UInt64> arrivalTimestamp) {
    final UInt64 validatorIndex = item.getValidatorId();
    LOG.debug("Adding remote validator id {} to {}", validatorIndex, metricType);
    if (operations.containsKey(validatorIndex)) {
      return SafeFuture.completedFuture(rejectForDuplicatedMessage(metricType, validatorIndex))
          .thenPeek(result -> validationReasonCounter.labels(result.code().toString()).inc());
    }
    return add(item, true);
  }

  @Override
  public void addAll(final SszCollection<T> items) {
    items.forEach(
        item -> {
          final UInt64 validatorIndex = item.getValidatorId();
          operations.putIfAbsent(
              validatorIndex,
              new OperationPoolEntry<>(item, false, timeProvider.getTimeInSeconds()));
        });
  }

  @Override
  public void removeAll(final SszCollection<T> items) {
    items.forEach(
        item -> {
          final UInt64 validatorIndex = item.getValidatorId();
          operations.remove(validatorIndex);
        });
  }

  @Override
  public Set<T> getAll() {
    return operations.values().stream()
        .map(OperationPoolEntry::getMessage)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<T> getLocallySubmitted() {
    return operations.values().stream()
        .filter(OperationPoolEntry::isLocal)
        .map(OperationPoolEntry::getMessage)
        .collect(Collectors.toSet());
  }

  @Override
  public int size() {
    return operations.size();
  }

  private SafeFuture<InternalValidationResult> add(final T item, final boolean fromNetwork) {
    final UInt64 validatorIndex = item.getValidatorId();
    return operationValidator
        .validateForGossip(item)
        .thenApply(
            result -> {
              validationReasonCounter.labels(result.code().toString()).inc();
              if (result.code().equals(ValidationResultCode.ACCEPT)) {
                operations.put(
                    validatorIndex,
                    new OperationPoolEntry<>(item, !fromNetwork, timeProvider.getTimeInSeconds()));
                subscribers.forEach(s -> s.onOperationAdded(item, result, fromNetwork));
              }
              return result;
            });
  }
}
