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

package tech.pegasys.teku.weaksubjectivity.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;

public class WeakSubjectivityConfig {

  private final Spec spec;
  private final Optional<Checkpoint> weakSubjectivityCheckpoint;
  private final Optional<UInt64> suppressWSPeriodChecksUntilEpoch;

  private WeakSubjectivityConfig(
      final Spec spec,
      final Optional<Checkpoint> weakSubjectivityCheckpoint,
      final Optional<UInt64> suppressWSPeriodChecksUntilEpoch) {
    this.spec = spec;
    this.suppressWSPeriodChecksUntilEpoch = suppressWSPeriodChecksUntilEpoch;
    checkNotNull(weakSubjectivityCheckpoint);
    this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
  }

  public static WeakSubjectivityConfig.Builder builder(final WeakSubjectivityState state) {
    return builder().weakSubjectivityCheckpoint(state.getCheckpoint());
  }

  public static Builder builder() {
    return new Builder();
  }

  public WeakSubjectivityConfig updated(final Consumer<Builder> updater) {
    Builder builder = copy();
    updater.accept(builder);
    return builder.build();
  }

  private Builder copy() {
    return WeakSubjectivityConfig.builder()
        .specProvider(spec)
        .weakSubjectivityCheckpoint(weakSubjectivityCheckpoint)
        .suppressWSPeriodChecksUntilEpoch(suppressWSPeriodChecksUntilEpoch);
  }

  public Spec getSpec() {
    return spec;
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return weakSubjectivityCheckpoint;
  }

  public Optional<UInt64> getSuppressWSPeriodChecksUntilEpoch() {
    return suppressWSPeriodChecksUntilEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WeakSubjectivityConfig that = (WeakSubjectivityConfig) o;
    return Objects.equals(weakSubjectivityCheckpoint, that.weakSubjectivityCheckpoint)
        && Objects.equals(suppressWSPeriodChecksUntilEpoch, that.suppressWSPeriodChecksUntilEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(weakSubjectivityCheckpoint, suppressWSPeriodChecksUntilEpoch);
  }

  @Override
  public String toString() {
    return "WeakSubjectivityConfig{"
        + "weakSubjectivityCheckpoint="
        + weakSubjectivityCheckpoint
        + ", suppressWSPeriodChecksUntilEpoch="
        + suppressWSPeriodChecksUntilEpoch
        + '}';
  }

  public static class Builder {
    private Spec spec;
    private Optional<Checkpoint> weakSubjectivityCheckpoint = Optional.empty();
    private Optional<UInt64> suppressWSPeriodChecksUntilEpoch = Optional.empty();

    private Builder() {}

    public WeakSubjectivityConfig build() {
      validate();
      return new WeakSubjectivityConfig(
          spec, weakSubjectivityCheckpoint, suppressWSPeriodChecksUntilEpoch);
    }

    private void validate() {
      checkNotNull(spec, "Must provide specProvider");
    }

    public Builder specProvider(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
    }

    public Builder weakSubjectivityCheckpoint(final Checkpoint weakSubjectivityCheckpoint) {
      return weakSubjectivityCheckpoint(Optional.of(weakSubjectivityCheckpoint));
    }

    public Builder weakSubjectivityCheckpoint(
        final Optional<Checkpoint> weakSubjectivityCheckpoint) {
      checkNotNull(weakSubjectivityCheckpoint);
      this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
      return this;
    }

    public Builder suppressWSPeriodChecksUntilEpoch(final UInt64 suppressWSPeriodChecksUntilEpoch) {
      checkNotNull(weakSubjectivityCheckpoint);
      return suppressWSPeriodChecksUntilEpoch(Optional.of(suppressWSPeriodChecksUntilEpoch));
    }

    public Builder suppressWSPeriodChecksUntilEpoch(
        final Optional<UInt64> suppressWSPeriodChecksUntilEpoch) {
      checkNotNull(weakSubjectivityCheckpoint);
      this.suppressWSPeriodChecksUntilEpoch = suppressWSPeriodChecksUntilEpoch;
      return this;
    }
  }
}
