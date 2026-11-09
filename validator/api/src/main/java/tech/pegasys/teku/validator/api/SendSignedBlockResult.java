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

package tech.pegasys.teku.validator.api;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;

public class SendSignedBlockResult {
  private final Optional<Bytes32> blockRoot;
  private final Optional<String> rejectionReason;
  private final boolean published;

  private SendSignedBlockResult(
      final Optional<Bytes32> blockRoot,
      final Optional<String> rejectionReason,
      final boolean published) {
    this.blockRoot = blockRoot;
    this.rejectionReason = rejectionReason;
    this.published = published;
  }

  public static SendSignedBlockResult success(final Bytes32 blockRoot) {
    return new SendSignedBlockResult(Optional.of(blockRoot), Optional.empty(), true);
  }

  public static SendSignedBlockResult notImported(final String reason) {
    return new SendSignedBlockResult(Optional.empty(), Optional.of(reason), true);
  }

  public static SendSignedBlockResult rejected(final String reason) {
    return new SendSignedBlockResult(Optional.empty(), Optional.of(reason), false);
  }

  public Optional<Bytes32> getBlockRoot() {
    return blockRoot;
  }

  public Optional<String> getRejectionReason() {
    return rejectionReason;
  }

  public boolean isPublished() {
    return published;
  }

  public boolean isRejectedDueToBroadcastValidationFailure() {
    return !published
        && rejectionReason.isPresent()
        && rejectionReason.get().startsWith(FailureReason.FAILED_BROADCAST_VALIDATION.name());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendSignedBlockResult that = (SendSignedBlockResult) o;
    return published == that.published
        && Objects.equals(blockRoot, that.blockRoot)
        && Objects.equals(rejectionReason, that.rejectionReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockRoot, rejectionReason, published);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockRoot", blockRoot)
        .add("rejectionReason", rejectionReason)
        .add("published", published)
        .toString();
  }
}
