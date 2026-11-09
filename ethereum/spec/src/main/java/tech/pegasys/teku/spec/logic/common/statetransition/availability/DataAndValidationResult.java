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

package tech.pegasys.teku.spec.logic.common.statetransition.availability;

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public record DataAndValidationResult<Data>(
    AvailabilityValidationResult validationResult, List<Data> data, Optional<Throwable> cause) {

  public static <Data> DataAndValidationResult<Data> notAvailable() {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.NOT_AVAILABLE, Collections.emptyList(), Optional.empty());
  }

  public static <Data> DataAndValidationResult<Data> notRequired() {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.NOT_REQUIRED, Collections.emptyList(), Optional.empty());
  }

  public static <Data> SafeFuture<DataAndValidationResult<Data>> notRequiredResultFuture() {
    return SafeFuture.completedFuture(notRequired());
  }

  public static <Data> DataAndValidationResult<Data> validResult(final List<Data> dataList) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.VALID, dataList, Optional.empty());
  }

  public static <Data> DataAndValidationResult<Data> invalidResult(final List<Data> dataList) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.INVALID, dataList, Optional.empty());
  }

  public static <Data> DataAndValidationResult<Data> invalidResult(
      final List<Data> dataList, final Throwable cause) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.INVALID, dataList, Optional.of(cause));
  }

  public static <Data> DataAndValidationResult<Data> notAvailable(final Throwable cause) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.NOT_AVAILABLE, Collections.emptyList(), Optional.of(cause));
  }

  public boolean isValid() {
    return validationResult.equals(AvailabilityValidationResult.VALID);
  }

  public boolean isNotRequired() {
    return validationResult.equals(AvailabilityValidationResult.NOT_REQUIRED);
  }

  public boolean isInvalid() {
    return validationResult.equals(AvailabilityValidationResult.INVALID);
  }

  public boolean isNotAvailable() {
    return validationResult.equals(AvailabilityValidationResult.NOT_AVAILABLE);
  }

  public boolean isFailure() {
    return isInvalid() || isNotAvailable();
  }

  public boolean isSuccess() {
    return isValid() || isNotRequired();
  }

  public Optional<List<BlobSidecar>> getDataAsBlobSidecars() {
    if (data != null && !data.isEmpty() && data.getFirst() instanceof BlobSidecar) {
      @SuppressWarnings("unchecked")
      final List<BlobSidecar> blobSidecars = (List<BlobSidecar>) data;
      return Optional.of(blobSidecars);
    }
    return Optional.empty();
  }

  public String toLogString() {
    return MoreObjects.toStringHelper(this)
        .add("validationResult", validationResult)
        .add("dataSize", data.size())
        .add("cause", cause)
        .toString();
  }
}
