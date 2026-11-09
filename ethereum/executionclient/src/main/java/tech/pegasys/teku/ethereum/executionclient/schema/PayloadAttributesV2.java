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
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesV2 extends PayloadAttributesV1 {

  public final List<WithdrawalV1> withdrawals;

  public PayloadAttributesV2(
      final @JsonProperty("timestamp") UInt64 timestamp,
      final @JsonProperty("prevRandao") Bytes32 prevRandao,
      final @JsonProperty("suggestedFeeRecipient") Bytes20 suggestedFeeRecipient,
      final @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals) {
    super(timestamp, prevRandao, suggestedFeeRecipient);
    checkNotNull(withdrawals, "withdrawals");
    this.withdrawals = withdrawals;
  }

  public static Optional<PayloadAttributesV2> fromInternalPayloadBuildingAttributesV2(
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    return payloadBuildingAttributes.map(
        payloadAttributes ->
            new PayloadAttributesV2(
                payloadAttributes.timestamp(),
                payloadAttributes.prevRandao(),
                payloadAttributes.feeRecipient(),
                getWithdrawals(payloadAttributes)));
  }

  public static List<WithdrawalV1> getWithdrawals(
      final PayloadBuildingAttributes payloadAttributes) {
    return payloadAttributes
        .withdrawals()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Withdrawals were expected to be part of the payload attributes for slot "
                        + payloadAttributes.proposalSlot()))
        .stream()
        .map(
            withdrawal ->
                new WithdrawalV1(
                    withdrawal.getIndex(),
                    withdrawal.getValidatorIndex(),
                    withdrawal.getAddress(),
                    withdrawal.getAmount()))
        .toList();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final PayloadAttributesV2 that = (PayloadAttributesV2) o;
    return Objects.equals(withdrawals, that.withdrawals);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), withdrawals);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("prevRandao", prevRandao)
        .add("suggestedFeeRecipient", suggestedFeeRecipient)
        .add("withdrawals", withdrawals)
        .toString();
  }
}
