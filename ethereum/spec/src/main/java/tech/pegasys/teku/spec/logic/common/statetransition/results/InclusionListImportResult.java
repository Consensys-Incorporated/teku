/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.common.statetransition.results;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;

public interface InclusionListImportResult {

  static InclusionListImportResult failedSlotOutsideRetentionWindow() {
    return new FailedInclusionListImportResult(
        FailureReason.SLOT_OUTSIDE_RETENTION_WINDOW, Optional.empty());
  }

  static InclusionListImportResult failedNoTransactions() {
    return new FailedInclusionListImportResult(FailureReason.NO_TRANSACTIONS, Optional.empty());
  }

  static InclusionListImportResult failedEmptyTransaction() {
    return new FailedInclusionListImportResult(FailureReason.EMPTY_TRANSACTION, Optional.empty());
  }

  static InclusionListImportResult failedTransactionsSizeExceedsLimit() {
    return new FailedInclusionListImportResult(
        FailureReason.TRANSACTIONS_SIZE_EXCEEDS_LIMIT, Optional.empty());
  }

  static InclusionListImportResult success(final SignedInclusionList signedInclusionList) {
    return new SuccessfulInclusionListImport(signedInclusionList);
  }

  enum FailureReason {
    SLOT_OUTSIDE_RETENTION_WINDOW,
    NO_TRANSACTIONS,
    EMPTY_TRANSACTION,
    TRANSACTIONS_SIZE_EXCEEDS_LIMIT,
    INTERNAL_ERROR // A catch-all category for unexpected errors (bugs)
  }

  boolean isSuccessful();

  default Optional<SignedInclusionList> getSignedInclusionList() {
    return Optional.empty();
  }

  default Optional<FailureReason> getFailureReason() {
    return Optional.empty();
  }

  default Optional<Throwable> getFailureCause() {
    return Optional.empty();
  }
}
