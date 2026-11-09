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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;

public class ForkChoiceStateV1 {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 headBlockHash;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 safeBlockHash;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 finalizedBlockHash;

  public ForkChoiceStateV1(
      final @JsonProperty("headBlockHash") Bytes32 headBlockHash,
      final @JsonProperty("safeBlockHash") Bytes32 safeBlockHash,
      final @JsonProperty("finalizedBlockHash") Bytes32 finalizedBlockHash) {
    checkNotNull(headBlockHash, "headBlockHash");
    checkNotNull(safeBlockHash, "safeBlockHash");
    checkNotNull(finalizedBlockHash, "finalizedBlockHash");
    this.headBlockHash = headBlockHash;
    this.safeBlockHash = safeBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
  }

  public static ForkChoiceStateV1 fromInternalForkChoiceState(
      final ForkChoiceState forkChoiceState) {
    return new ForkChoiceStateV1(
        forkChoiceState.headExecutionBlockHash(),
        forkChoiceState.safeExecutionBlockHash(),
        forkChoiceState.finalizedExecutionBlockHash());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkChoiceStateV1 that = (ForkChoiceStateV1) o;
    return Objects.equals(headBlockHash, that.headBlockHash)
        && Objects.equals(safeBlockHash, that.safeBlockHash)
        && Objects.equals(finalizedBlockHash, that.finalizedBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headBlockHash, safeBlockHash, finalizedBlockHash);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("headBlockHash", headBlockHash)
        .add("safeBlockHash", safeBlockHash)
        .add("finalizedBlockHash", finalizedBlockHash)
        .toString();
  }
}
