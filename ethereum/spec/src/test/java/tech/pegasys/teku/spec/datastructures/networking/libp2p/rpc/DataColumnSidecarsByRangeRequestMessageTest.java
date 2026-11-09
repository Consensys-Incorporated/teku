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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class DataColumnSidecarsByRangeRequestMessageTest {

  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema
      schema =
          SchemaDefinitionsFulu.required(
                  spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
              .getDataColumnSidecarsByRangeRequestMessageSchema();
  final List<UInt64> columnIndices = List.of(UInt64.ZERO, UInt64.ONE);

  @Test
  public void shouldRoundTripViaSsz() {
    final DataColumnSidecarsByRangeRequestMessage request =
        schema.create(UInt64.valueOf(2), UInt64.valueOf(3), columnIndices);
    final Bytes data = request.sszSerialize();
    final DataColumnSidecarsByRangeRequestMessage result = schema.sszDeserialize(data);

    assertThatSszData(result).isEqualByAllMeansTo(request);
  }

  @ParameterizedTest(name = "startSlot={0}, count={1}")
  @MethodSource("getMaxSlotParams")
  public void getMaxSlot(final long startSlot, final long count, final long expected) {
    final DataColumnSidecarsByRangeRequestMessage request =
        schema.create(UInt64.valueOf(startSlot), UInt64.valueOf(count), columnIndices);

    assertThat(request.getMaxSlot()).isEqualTo(UInt64.valueOf(expected));
  }

  @Test
  public void getMaximumResponseChunks() {
    final DataColumnSidecarsByRangeRequestMessage request =
        schema.create(UInt64.valueOf(19), UInt64.valueOf(23), columnIndices);

    assertThat(request.getMaximumResponseChunks()).isEqualTo(23 * columnIndices.size());
  }

  public static Stream<Arguments> getMaxSlotParams() {
    return Stream.of(
        Arguments.of(0, 1, 0),
        Arguments.of(111, 1, 111),
        Arguments.of(0, 2, 1),
        Arguments.of(10, 2, 11),
        Arguments.of(0, 5, 4),
        Arguments.of(10, 5, 14),
        Arguments.of(1, 0, 0),
        Arguments.of(0, 0, 0));
  }
}
