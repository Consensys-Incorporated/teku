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

package tech.pegasys.teku.ethereum.json.types.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * From PQ, duties name validators by their 32-byte XMSS key, and attester duties say who
 * aggregates.
 */
class PqDutiesJsonTest {

  private final Bytes32 xmssKey = Bytes32.repeat((byte) 7);

  @Test
  void attesterDutyShouldRoundTripWithXmssKeyAndAggregatorFlag() throws Exception {
    final AttesterDuty duty =
        new AttesterDuty(xmssKey, 3, 16, 1, 2, 5, UInt64.valueOf(42), Optional.of(true));

    final String json = JsonUtil.serialize(duty, AttesterDutyBuilder.ATTESTER_DUTY_TYPE);

    assertThat(json).contains("\"pubkey\":\"" + xmssKey.toHexString() + "\"");
    assertThat(json).contains("\"is_aggregator\":true");
    assertThat(JsonUtil.parse(json, AttesterDutyBuilder.ATTESTER_DUTY_TYPE)).isEqualTo(duty);
    assertThatThrownBy(duty::getPublicKey).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void attesterDutyShouldRoundTripWithAggregatorPosition() throws Exception {
    final AttesterDuty duty =
        new AttesterDuty(
            xmssKey, 3, 16, 1, 2, 5, UInt64.valueOf(42), Optional.of(true), Optional.of(2));

    final String json = JsonUtil.serialize(duty, AttesterDutyBuilder.ATTESTER_DUTY_TYPE);

    assertThat(json).contains("\"aggregator_position\":\"2\"");
    final AttesterDuty parsed = JsonUtil.parse(json, AttesterDutyBuilder.ATTESTER_DUTY_TYPE);
    assertThat(parsed).isEqualTo(duty);
    assertThat(parsed.getAggregatorPosition()).contains(2);
  }

  @Test
  void attesterDutyWithoutAggregatorPositionShouldParseAsUnknown() throws Exception {
    // From a beacon node that doesn't say, which the validator client takes as the first position
    final AttesterDuty duty =
        new AttesterDuty(xmssKey, 3, 16, 1, 2, 5, UInt64.valueOf(42), Optional.of(true));

    final String json = JsonUtil.serialize(duty, AttesterDutyBuilder.ATTESTER_DUTY_TYPE);

    assertThat(json).doesNotContain("aggregator_position");
    assertThat(JsonUtil.parse(json, AttesterDutyBuilder.ATTESTER_DUTY_TYPE).getAggregatorPosition())
        .isEmpty();
  }

  @Test
  void attesterDutyBeforePqShouldOmitAggregatorFlag() throws Exception {
    final AttesterDuty duty =
        new AttesterDuty(
            Bytes.wrap(new byte[48]), 3, 16, 1, 2, 5, UInt64.valueOf(42), Optional.empty());

    assertThat(JsonUtil.serialize(duty, AttesterDutyBuilder.ATTESTER_DUTY_TYPE))
        .doesNotContain("is_aggregator");
  }

  @Test
  void proposerAndPtcDutiesShouldRoundTripWithXmssKey() throws Exception {
    final ProposerDuty proposerDuty = new ProposerDuty(xmssKey, 3, UInt64.valueOf(42));
    final PtcDuty ptcDuty = new PtcDuty(xmssKey, UInt64.valueOf(3), UInt64.valueOf(42));

    assertThat(
            JsonUtil.parse(
                JsonUtil.serialize(proposerDuty, ProposerDutyBuilder.PROPOSER_DUTY_TYPE),
                ProposerDutyBuilder.PROPOSER_DUTY_TYPE))
        .isEqualTo(proposerDuty);
    assertThat(
            JsonUtil.parse(
                JsonUtil.serialize(ptcDuty, PtcDuty.PTC_DUTY_TYPE_DEFINITION),
                PtcDuty.PTC_DUTY_TYPE_DEFINITION))
        .isEqualTo(ptcDuty);
  }
}
