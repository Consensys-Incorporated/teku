/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfigSchema.MAX_BUILDER_ENTRIES;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Unlike {@link tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig}, all
 * fields are optional and are resolved by the validator client.
 */
public record BuilderConfig(
    Optional<UInt64> minBid,
    Optional<UInt64> builderBoostFactor,
    Optional<List<BuilderEntry>> builders) {

  @SuppressWarnings("MethodInputParametersMustBeFinal")
  public BuilderConfig {
    builders.ifPresent(BuilderConfig::validateBuilders);
  }

  private static void validateBuilders(final List<BuilderEntry> builders) {
    checkArgument(
        builders.size() <= MAX_BUILDER_ENTRIES,
        "builders must contain at most %s entries",
        MAX_BUILDER_ENTRIES);
    final Set<String> urls = new HashSet<>();
    for (final BuilderEntry builder : builders) {
      checkArgument(urls.add(builder.url()), "builders contain duplicate url %s", builder.url());
    }
  }

  public static class Builder {
    private Optional<UInt64> minBid = Optional.empty();
    private Optional<UInt64> builderBoostFactor = Optional.empty();
    private Optional<List<BuilderEntry>> builders = Optional.empty();

    public Builder minBid(final Optional<UInt64> minBid) {
      this.minBid = minBid;
      return this;
    }

    public Builder builderBoostFactor(final Optional<UInt64> builderBoostFactor) {
      this.builderBoostFactor = builderBoostFactor;
      return this;
    }

    public Builder builders(final Optional<List<BuilderEntry>> builders) {
      this.builders = builders;
      return this;
    }

    public BuilderConfig build() {
      return new BuilderConfig(minBid, builderBoostFactor, builders);
    }
  }
}
