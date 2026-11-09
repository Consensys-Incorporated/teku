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

package tech.pegasys.teku.spec.logic.common.statetransition.results;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

class FailedBlockImportResult implements BlockImportResult {
  private final FailureReason failureReason;
  private final Optional<Throwable> cause;

  FailedBlockImportResult(final FailureReason failureReason, final Optional<Throwable> cause) {
    this.failureReason = failureReason;
    this.cause = cause;
  }

  @Override
  public boolean isSuccessful() {
    return false;
  }

  @Override
  public SignedBeaconBlock getBlock() {
    return null;
  }

  @Override
  public FailureReason getFailureReason() {
    return failureReason;
  }

  @Override
  public Optional<Throwable> getFailureCause() {
    return cause;
  }

  @Override
  public boolean hasFailedExecutingExecutionPayload() {
    return failureReason == FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION
        || failureReason == FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;
  }

  @Override
  public boolean isDataNotAvailable() {
    return failureReason == FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("failureReason", failureReason)
        .add("cause", cause)
        .toString();
  }
}
