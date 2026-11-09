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

package tech.pegasys.teku.data.slashinginterchange;

import com.google.common.base.MoreObjects;
import java.util.List;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public record SlashingProtectionInterchangeFormat(Metadata metadata, List<SigningHistory> data) {

  public static DeserializableTypeDefinition<SlashingProtectionInterchangeFormat>
      getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(
            SlashingProtectionInterchangeFormat.class,
            SlashingProtectionInterchangeFormatBuilder.class)
        .initializer(SlashingProtectionInterchangeFormatBuilder::new)
        .finisher(SlashingProtectionInterchangeFormatBuilder::build)
        .withField(
            "metadata",
            Metadata.getJsonTypeDefinition(),
            SlashingProtectionInterchangeFormat::metadata,
            SlashingProtectionInterchangeFormatBuilder::metadata)
        .withField(
            "data",
            DeserializableTypeDefinition.listOf(SigningHistory.getJsonTypeDefinition()),
            SlashingProtectionInterchangeFormat::data,
            SlashingProtectionInterchangeFormatBuilder::data)
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("metadata", metadata).add("data", data).toString();
  }

  static class SlashingProtectionInterchangeFormatBuilder {
    Metadata metadata;
    List<SigningHistory> data;

    SlashingProtectionInterchangeFormatBuilder metadata(final Metadata metadata) {
      this.metadata = metadata;
      return this;
    }

    SlashingProtectionInterchangeFormatBuilder data(final List<SigningHistory> data) {
      this.data = data;
      return this;
    }

    SlashingProtectionInterchangeFormat build() {
      return new SlashingProtectionInterchangeFormat(metadata, data);
    }
  }
}
