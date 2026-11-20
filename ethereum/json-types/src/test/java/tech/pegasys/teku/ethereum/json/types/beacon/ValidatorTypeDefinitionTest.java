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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ethereum.json.types.beacon.ValidatorTypeDefinition.VALIDATOR_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.ValidatorPhase0;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ValidatorTypeDefinitionTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void blsValidatorJsonIsUnchanged() throws JsonProcessingException {
    final Validator validator = dataStructureUtil.randomValidator();
    @SuppressWarnings("unchecked")
    final DeserializableTypeDefinition<ValidatorPhase0> sszJsonType =
        (DeserializableTypeDefinition<ValidatorPhase0>)
            (DeserializableTypeDefinition<?>) ValidatorPhase0.SSZ_SCHEMA.getJsonTypeDefinition();

    final String json = JsonUtil.serialize(validator, VALIDATOR_TYPE);

    assertThat(json).isEqualTo(JsonUtil.serialize((ValidatorPhase0) validator, sszJsonType));
    assertThat(JsonUtil.parse(json, VALIDATOR_TYPE)).isEqualTo(validator);
  }

  @Test
  void pqValidatorRoundTripsWithItsXmssKeyAndVrfRoot() throws JsonProcessingException {
    final Validator validator =
        new ValidatorPq(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            true,
            UInt64.ONE,
            UInt64.valueOf(2),
            UInt64.valueOf(3),
            UInt64.valueOf(4));

    final String json = JsonUtil.serialize(validator, VALIDATOR_TYPE);

    assertThat(json)
        .contains("\"pubkey\":\"" + ((ValidatorPq) validator).getXmssPublicKey().toHexString())
        .contains("\"vrf_root\":\"" + ((ValidatorPq) validator).getVrfRoot().toHexString());
    assertThat(JsonUtil.parse(json, VALIDATOR_TYPE)).isEqualTo(validator);
  }

  @Test
  void rejectsAVrfRootWithABlsKey() throws JsonProcessingException {
    final String json =
        JsonUtil.serialize(dataStructureUtil.randomValidator(), VALIDATOR_TYPE)
            .replace(
                "\"withdrawal_credentials\"",
                "\"vrf_root\":\""
                    + dataStructureUtil.randomBytes32()
                    + "\",\"withdrawal_credentials\"");

    assertThatThrownBy(() -> JsonUtil.parse(json, VALIDATOR_TYPE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }
}
