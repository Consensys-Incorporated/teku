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

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.signatures.SignerNotActiveException;
import tech.pegasys.teku.validator.api.NodeSyncingException;

public class DutyResult {
  public static final DutyResult NOOP = new DutyResult(0, 0, emptySet(), emptyMap(), List.of());
  public static final DutyResult NODE_SYNCING =
      new DutyResult(0, 1, emptySet(), emptyMap(), List.of());

  private final int successCount;
  private final int nodeSyncingCount;
  private final Set<Bytes32> roots;

  /**
   * We combine failures based on the exception type to avoid logging thousands of the same type of
   * exception if a lot of validators fail at the same time.
   */
  private final Map<Class<?>, FailureRecord> failures;

  // list
  private final List<String> context;

  private DutyResult(
      final int successCount,
      final int nodeSyncingCount,
      final Set<Bytes32> roots,
      final Map<Class<?>, FailureRecord> failures,
      final List<String> context) {
    this.successCount = successCount;
    this.nodeSyncingCount = nodeSyncingCount;
    this.roots = roots;
    this.failures = failures;
    this.context = context;
  }

  public static DutyResult success(final Bytes32 result) {
    return success(result, 1);
  }

  public static DutyResult success(final Bytes32 result, final List<String> context) {
    return new DutyResult(1, 0, singleton(result), emptyMap(), context);
  }

  public static DutyResult success(final Bytes32 result, final int count) {
    return new DutyResult(count, 0, singleton(result), emptyMap(), List.of());
  }

  public static DutyResult forError(final Throwable error) {
    return forError(emptySet(), error);
  }

  public static DutyResult forError(final BLSPublicKey validatorKey, final Throwable error) {
    return forError(singleton(validatorKey), error);
  }

  public static DutyResult forError(final Set<BLSPublicKey> validatorKeys, final Throwable error) {
    // Not using getRootCause here because we only want to unwrap CompletionException
    Throwable cause = error;
    while (cause instanceof CompletionException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (cause instanceof NodeSyncingException) {
      return NODE_SYNCING;
    } else {
      return new DutyResult(
          0,
          0,
          emptySet(),
          Map.of(cause.getClass(), new FailureRecord(cause, validatorKeys)),
          List.of());
    }
  }

  public static SafeFuture<DutyResult> combine(final List<SafeFuture<DutyResult>> futures) {
    return SafeFuture.allOf(futures.toArray(SafeFuture[]::new))
        .handle(
            (ok, error) ->
                futures.stream()
                    .map(future -> future.exceptionally(DutyResult::forError).getNow(null))
                    .reduce(DutyResult::combine)
                    .orElse(NOOP));
  }

  public DutyResult combine(final DutyResult other) {
    final int combinedSuccessCount = this.successCount + other.successCount;
    final int combinedSyncingCount = this.nodeSyncingCount + other.nodeSyncingCount;

    final Set<Bytes32> combinedRoots = new HashSet<>(this.roots);
    combinedRoots.addAll(other.roots);

    final Map<Class<?>, FailureRecord> combinedErrors = new HashMap<>(this.failures);
    other.failures.forEach(
        (cause, validators) -> combinedErrors.merge(cause, validators, FailureRecord::merge));

    return new DutyResult(
        combinedSuccessCount,
        combinedSyncingCount,
        combinedRoots,
        combinedErrors,
        Stream.concat(context.stream(), other.context.stream()).toList());
  }

  public int getSuccessCount() {
    return successCount;
  }

  public int getFailureCount() {
    return failures.size() + nodeSyncingCount;
  }

  public void report(final String producedType, final UInt64 slot, final ValidatorLogger logger) {
    if (successCount > 0) {
      logger.dutyCompleted(
          producedType,
          slot,
          successCount,
          roots,
          context.size() > 0 ? Optional.of(String.join(", ", context)) : Optional.empty());
    }
    if (nodeSyncingCount > 0) {
      logger.dutySkippedWhileSyncing(producedType, slot, nodeSyncingCount);
    }
    failures
        .values()
        .forEach(
            failure ->
                reportDutyFailed(
                    logger,
                    producedType,
                    slot,
                    summarizeKeys(failure.validatorKeys),
                    failure.error));
  }

  private void reportDutyFailed(
      final ValidatorLogger logger,
      final String producedType,
      final UInt64 slot,
      final Set<String> summarizeKeys,
      final Throwable error) {
    if (Throwables.getRootCause(error) instanceof SignerNotActiveException) {
      logger.signerNoLongerActive(producedType, slot, summarizeKeys);
    } else {
      logger.dutyFailed(producedType, slot, summarizeKeys, error);
    }
  }

  private Set<String> summarizeKeys(final Set<BLSPublicKey> validatorKeys) {
    return validatorKeys.stream()
        .map(BLSPublicKey::toAbbreviatedString)
        .collect(Collectors.toSet());
  }

  @Override
  public String toString() {
    return "DutyResult{"
        + "successCount="
        + successCount
        + ", nodeSyncingCount="
        + nodeSyncingCount
        + ", roots="
        + roots
        + ", errors="
        + failures
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DutyResult that = (DutyResult) o;
    return successCount == that.successCount
        && nodeSyncingCount == that.nodeSyncingCount
        && Objects.equals(roots, that.roots)
        && Objects.equals(failures, that.failures);
  }

  @Override
  public int hashCode() {
    return Objects.hash(successCount, nodeSyncingCount, roots, failures);
  }

  private static class FailureRecord {
    private final Throwable error;
    private final Set<BLSPublicKey> validatorKeys;

    private FailureRecord(final Throwable error, final Set<BLSPublicKey> validatorKeys) {
      this.error = error;
      this.validatorKeys = validatorKeys;
    }

    public static FailureRecord merge(final FailureRecord a, final FailureRecord b) {
      return new FailureRecord(a.error, Sets.union(a.validatorKeys, b.validatorKeys));
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("error", error)
          .add("validatorKeys", validatorKeys)
          .toString();
    }
  }
}
