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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record Metadata(
    Optional<String> interchangeFormat,
    UInt64 interchangeFormatVersion,
    Optional<Bytes32> genesisValidatorsRoot) {

  public static final UInt64 INTERCHANGE_VERSION = UInt64.valueOf(5);

  public static DeserializableTypeDefinition<Metadata> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(Metadata.class, MetadataBuilder.class)
        .initializer(MetadataBuilder::new)
        .finisher(MetadataBuilder::build)
        .withOptionalField(
            "interchange_format",
            STRING_TYPE,
            Metadata::interchangeFormat,
            MetadataBuilder::interchangeFormat)
        .withField(
            "interchange_format_version",
            UINT64_TYPE,
            Metadata::interchangeFormatVersion,
            MetadataBuilder::interchangeFormatVersion)
        .withOptionalField(
            "genesis_validators_root",
            BYTES32_TYPE,
            Metadata::genesisValidatorsRoot,
            MetadataBuilder::genesisValidatorsRoot)
        .build();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Metadata metadata = (Metadata) o;
    return Objects.equals(interchangeFormatVersion, metadata.interchangeFormatVersion)
        && Objects.equals(genesisValidatorsRoot, metadata.genesisValidatorsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(interchangeFormatVersion, genesisValidatorsRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("interchangeFormat", interchangeFormat)
        .add("interchangeFormatVersion", interchangeFormatVersion)
        .add("genesisValidatorsRoot", genesisValidatorsRoot)
        .toString();
  }

  static class MetadataBuilder {
    Optional<String> interchangeFormat = Optional.empty();
    UInt64 interchangeFormatVersion;
    Optional<Bytes32> genesisValidatorsRoot = Optional.empty();

    MetadataBuilder interchangeFormat(final Optional<String> interchangeFormat) {
      this.interchangeFormat = interchangeFormat;
      return this;
    }

    MetadataBuilder interchangeFormatVersion(final UInt64 interchangeFormatVersion) {
      this.interchangeFormatVersion = interchangeFormatVersion;
      return this;
    }

    MetadataBuilder genesisValidatorsRoot(final Optional<Bytes32> genesisValidatorsRoot) {
      this.genesisValidatorsRoot = genesisValidatorsRoot;
      return this;
    }

    Metadata build() {
      return new Metadata(interchangeFormat, interchangeFormatVersion, genesisValidatorsRoot);
    }
  }
}
