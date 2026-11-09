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
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes48Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WithdrawalRequestV1 {
  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  public final Bytes20 sourceAddress;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes48Deserializer.class)
  public final Bytes48 validatorPubkey;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 amount;

  public WithdrawalRequestV1(
      @JsonProperty("sourceAddress") final Bytes20 sourceAddress,
      @JsonProperty("validatorPubkey") final Bytes48 validatorPubkey,
      @JsonProperty("amount") final UInt64 amount) {
    checkNotNull(sourceAddress, "sourceAddress");
    checkNotNull(validatorPubkey, "validatorPubkey");
    checkNotNull(amount, "amount");
    this.sourceAddress = sourceAddress;
    this.validatorPubkey = validatorPubkey;
    this.amount = amount;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WithdrawalRequestV1 that = (WithdrawalRequestV1) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(validatorPubkey, that.validatorPubkey)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, validatorPubkey, amount);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("sourceAddress", sourceAddress)
        .add("validatorPubkey", validatorPubkey)
        .add("amount", amount)
        .toString();
  }
}
