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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PayloadAttributesEventTest {

  @ParameterizedTest
  @EnumSource(
      value = SpecMilestone.class,
      names = {"DENEB", "GLOAS", "HEZE"})
  void shouldSerializeForkSpecificPayloadAttributes(final SpecMilestone milestone)
      throws IOException {
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalHeze());
    final PayloadBuildingAttributes base = data.randomPayloadBuildingAttributes(true);
    final PayloadBuildingAttributes attributes =
        new PayloadBuildingAttributes(
            base.proposerIndex(),
            UInt64.valueOf(32),
            base.timestamp(),
            base.prevRandao(),
            base.feeRecipient(),
            UInt64.valueOf(60_000_000),
            base.validatorRegistration(),
            base.withdrawals(),
            base.parentBeaconBlock(),
            List.of(Bytes.fromHexString("0x1234"), Bytes.fromHexString("0xabcd")));
    final ForkChoiceState forkChoiceState =
        new ForkChoiceState(
            base.parentBeaconBlock(),
            UInt64.valueOf(31),
            UInt64.valueOf(100),
            data.randomBytes32(),
            data.randomBytes32(),
            data.randomBytes32(),
            false);
    final SafeFuture<Optional<PayloadAttributesEvent>> eventFuture =
        PayloadAttributesEvent.create(
            TestSpecFactory.createMinimal(milestone),
            attributes,
            forkChoiceState,
            (proposalSlot, parentBlockRoot) -> {
              assertThat(milestone).isEqualTo(SpecMilestone.HEZE);
              assertThat(proposalSlot).isEqualTo(UInt64.valueOf(32));
              assertThat(parentBlockRoot).isEqualTo(attributes.parentBeaconBlock().blockRoot());
              return SafeFuture.completedFuture(
                  Optional.of(SszBitvectorSchema.create(16).ofBits(0, 9)));
            });
    assertThat(eventFuture).isCompleted();
    final PayloadAttributesEvent event = eventFuture.getNow(Optional.empty()).orElseThrow();
    final JsonNode eventData =
        new ObjectMapper()
            .readTree(JsonUtil.serialize(event.getData(), event.getJsonTypeDefinition()))
            .get("data");
    final JsonNode payloadAttributes = eventData.get("payload_attributes");

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      assertThat(payloadAttributes.path("slot_number").asText()).isEqualTo("32");
      assertThat(payloadAttributes.path("target_gas_limit").asText()).isEqualTo("60000000");
      assertThat(eventData.has("parent_block_number")).isFalse();
    } else {
      assertThat(payloadAttributes.has("slot_number")).isFalse();
      assertThat(payloadAttributes.has("target_gas_limit")).isFalse();
      assertThat(eventData.path("parent_block_number").asText()).isEqualTo("100");
    }
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE)) {
      assertThat(eventData.path("inclusion_list_bits").asText()).isEqualTo("0x0102");
      assertThat(payloadAttributes.path("inclusion_list_transactions"))
          .isEqualTo(new ObjectMapper().readTree("[\"0x1234\",\"0xabcd\"]"));
    } else {
      assertThat(payloadAttributes.has("inclusion_list_transactions")).isFalse();
      assertThat(eventData.has("inclusion_list_bits")).isFalse();
    }
  }
}
