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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;

public record BuilderApiResponse<T>(SpecMilestone version, T data) {

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("version", version).add("data", data).toString();
  }

  public static class Builder<T> {
    private SpecMilestone version;
    private T data;

    private Builder() {}

    public static <T> Builder<T> builder() {
      return new Builder<>();
    }

    public Builder<T> version(final SpecMilestone version) {
      this.version = version;
      return this;
    }

    public Builder<T> data(final T data) {
      this.data = data;
      return this;
    }

    public BuilderApiResponse<T> build() {
      return new BuilderApiResponse<>(version, data);
    }
  }

  public static <T> DeserializableTypeDefinition<BuilderApiResponse<T>> createTypeDefinition(
      final DeserializableTypeDefinition<T> dataTypeDefinition) {
    return DeserializableTypeDefinition.<BuilderApiResponse<T>, Builder<T>>object()
        .initializer(BuilderApiResponse.Builder::builder)
        .withField(
            "version",
            EthereumTypes.MILESTONE_TYPE,
            BuilderApiResponse::version,
            BuilderApiResponse.Builder::version)
        .withField(
            "data", dataTypeDefinition, BuilderApiResponse::data, BuilderApiResponse.Builder::data)
        .finisher(BuilderApiResponse.Builder::build)
        .build();
  }
}
