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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes4Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes4Serializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class ClientVersionV1 {

  public final String code;
  public final String name;
  public final String version;

  @JsonSerialize(using = Bytes4Serializer.class)
  @JsonDeserialize(using = Bytes4Deserializer.class)
  public final Bytes4 commit;

  public ClientVersionV1(
      final @JsonProperty("code") String code,
      final @JsonProperty("name") String name,
      final @JsonProperty("version") String version,
      final @JsonProperty("commit") Bytes4 commit) {
    checkNotNull(code, "code");
    checkNotNull(name, "name");
    checkNotNull(version, "version");
    checkNotNull(commit, "commit");
    this.code = code;
    this.name = name;
    this.version = version;
    this.commit = commit;
  }

  public static ClientVersionV1 fromInternalClientVersion(final ClientVersion clientVersion) {
    return new ClientVersionV1(
        clientVersion.code(),
        clientVersion.name(),
        clientVersion.version(),
        clientVersion.commit());
  }

  public static ClientVersion asInternalClientVersion(final ClientVersionV1 clientVersionV1) {
    return new ClientVersion(
        clientVersionV1.code,
        clientVersionV1.name,
        clientVersionV1.version,
        clientVersionV1.commit);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClientVersionV1 that = (ClientVersionV1) o;
    return Objects.equals(code, that.code)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(commit, that.commit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, name, version, commit);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("code", code)
        .add("name", name)
        .add("version", version)
        .add("commit", commit)
        .toString();
  }
}
