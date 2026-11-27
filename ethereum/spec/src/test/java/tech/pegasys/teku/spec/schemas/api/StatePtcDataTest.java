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

package tech.pegasys.teku.spec.schemas.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

class StatePtcDataTest {

  private final StatePtcDataSchema schema = new StatePtcDataSchema(SszUInt64VectorSchema.create(3));

  @Test
  void shouldSerializeAsSszContainer() {
    final StatePtcData data =
        schema.create(
            UInt64.valueOf(5), List.of(UInt64.valueOf(9), UInt64.valueOf(2), UInt64.valueOf(7)));

    assertThat(schema.isFixedSize()).isTrue();
    assertThat(data.sszSerialize())
        .isEqualTo(
            Bytes.fromHexString(
                "0x0500000000000000"
                    + "0900000000000000"
                    + "0200000000000000"
                    + "0700000000000000"));
  }

  @Test
  void shouldRoundTrip() {
    final StatePtcData data =
        schema.create(
            UInt64.valueOf(42), List.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));

    final StatePtcData decoded = schema.sszDeserialize(data.sszSerialize());

    assertThat(decoded).isEqualTo(data);
    assertThat(decoded.getSlot()).isEqualTo(UInt64.valueOf(42));
    assertThat(decoded.getValidators().asListUnboxed())
        .containsExactly(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3));
  }

  @Test
  void shouldSizeValidatorsByPayloadTimelinessCommitteeSize() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final int ptcSize =
        spec.getGenesisSpecConfig()
            .toVersionGloas()
            .orElseThrow()
            .getPayloadTimelinessCommitteeSize();

    final StatePtcDataSchema gloasSchema =
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions()).getStatePtcDataSchema();

    assertThat(gloasSchema.getValidatorsSchema().getLength()).isEqualTo(ptcSize);
    assertThat(gloasSchema.getSszFixedPartSize()).isEqualTo(8 + 8L * ptcSize);
  }
}
