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
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;

public class ExecutionPayloadBodyV2 {

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> transactions;

  public final List<WithdrawalV1> withdrawals;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  public final Bytes blockAccessList;

  public ExecutionPayloadBodyV2(
      @JsonProperty("transactions") final List<Bytes> transactions,
      @JsonProperty("withdrawals") final List<WithdrawalV1> withdrawals,
      @JsonProperty("blockAccessList") final Bytes blockAccessList) {
    checkNotNull(transactions, "transactions");
    this.transactions = transactions;
    this.withdrawals = withdrawals;
    this.blockAccessList = blockAccessList;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadBodyV2 that = (ExecutionPayloadBodyV2) o;
    return Objects.equals(transactions, that.transactions)
        && Objects.equals(withdrawals, that.withdrawals)
        && Objects.equals(blockAccessList, that.blockAccessList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactions, withdrawals, blockAccessList);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("transactions", transactions.size())
        .add("withdrawals", withdrawals != null ? withdrawals.size() : null)
        .add("blockAccessList", blockAccessList != null ? blockAccessList.size() : null)
        .toString();
  }
}
