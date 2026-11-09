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

package tech.pegasys.teku.spec.datastructures.execution;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;

/**
 * Either {@link #builderPayload} or {@link #fallbackData} would be present, depending on if builder
 * has been used or there has been a local fallback
 */
public class BuilderPayloadOrFallbackData {
  private final Optional<BuilderPayload> builderPayload;
  private final Optional<FallbackData> fallbackData;

  private BuilderPayloadOrFallbackData(
      final Optional<BuilderPayload> builderPayload, final Optional<FallbackData> fallbackData) {
    this.builderPayload = builderPayload;
    this.fallbackData = fallbackData;
  }

  public static BuilderPayloadOrFallbackData create(final BuilderPayload builderPayload) {
    return new BuilderPayloadOrFallbackData(Optional.ofNullable(builderPayload), Optional.empty());
  }

  public static BuilderPayloadOrFallbackData create(final FallbackData fallbackData) {
    return new BuilderPayloadOrFallbackData(Optional.empty(), Optional.ofNullable(fallbackData));
  }

  public static BuilderPayloadOrFallbackData createSuccessful() {
    return new BuilderPayloadOrFallbackData(Optional.empty(), Optional.empty());
  }

  public Optional<BuilderPayload> getBuilderPayload() {
    return builderPayload;
  }

  public Optional<FallbackData> getFallbackData() {
    return fallbackData;
  }

  public boolean isEmptySuccessful() {
    return builderPayload.isEmpty() && fallbackData.isEmpty();
  }

  public FallbackData getFallbackDataRequired() {
    return fallbackData.orElseThrow(
        () -> new IllegalStateException("FallbackData is not available in " + this));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BuilderPayloadOrFallbackData that = (BuilderPayloadOrFallbackData) o;
    return Objects.equals(builderPayload, that.builderPayload)
        && Objects.equals(fallbackData, that.fallbackData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(builderPayload, fallbackData);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("builderPayload", builderPayload)
        .add("fallbackData", fallbackData)
        .toString();
  }
}
