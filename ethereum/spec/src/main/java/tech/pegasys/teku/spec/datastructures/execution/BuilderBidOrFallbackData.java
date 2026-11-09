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
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;

/**
 * Either {@link #builderBid} or {@link #fallbackData} would be present, depending on if builder has
 * been used or there has been a local fallback
 */
public class BuilderBidOrFallbackData {

  private final Optional<BuilderBid> builderBid;
  private final Optional<FallbackData> fallbackData;

  private BuilderBidOrFallbackData(
      final Optional<BuilderBid> builderBid, final Optional<FallbackData> fallbackData) {
    this.builderBid = builderBid;
    this.fallbackData = fallbackData;
  }

  public static BuilderBidOrFallbackData create(final BuilderBid builderBid) {
    return new BuilderBidOrFallbackData(Optional.of(builderBid), Optional.empty());
  }

  public static BuilderBidOrFallbackData create(final FallbackData fallbackData) {
    return new BuilderBidOrFallbackData(Optional.empty(), Optional.of(fallbackData));
  }

  public Optional<BuilderBid> getBuilderBid() {
    return builderBid;
  }

  public Optional<FallbackData> getFallbackData() {
    return fallbackData;
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
    final BuilderBidOrFallbackData that = (BuilderBidOrFallbackData) o;
    return Objects.equals(builderBid, that.builderBid)
        && Objects.equals(fallbackData, that.fallbackData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(builderBid, fallbackData);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("builderBid", builderBid)
        .add("fallbackData", fallbackData)
        .toString();
  }
}
