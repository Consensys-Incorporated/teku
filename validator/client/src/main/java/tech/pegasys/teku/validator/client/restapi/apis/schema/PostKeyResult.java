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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;

public class PostKeyResult {
  private final ImportStatus importStatus;
  private final Optional<String> message;

  private PostKeyResult(final ImportStatus importStatus, final Optional<String> message) {
    this.importStatus = importStatus;
    this.message = message;
  }

  public ImportStatus getImportStatus() {
    return importStatus;
  }

  public Optional<String> getMessage() {
    return message;
  }

  public static PostKeyResult success() {
    return new PostKeyResult(ImportStatus.IMPORTED, Optional.empty());
  }

  public static PostKeyResult duplicate() {
    return new PostKeyResult(ImportStatus.DUPLICATE, Optional.empty());
  }

  public static PostKeyResult error(final String message) {
    return new PostKeyResult(ImportStatus.ERROR, Optional.of(message));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PostKeyResult that = (PostKeyResult) o;
    return importStatus == that.importStatus && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importStatus, message);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("importStatus", importStatus)
        .add("message", message)
        .toString();
  }
}
