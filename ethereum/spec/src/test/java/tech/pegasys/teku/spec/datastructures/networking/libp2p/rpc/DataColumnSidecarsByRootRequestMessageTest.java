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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

class DataColumnSidecarsByRootRequestMessageTest {

  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataColumnSidecarsByRootRequestMessageSchema schema =
      SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
          .getDataColumnSidecarsByRootRequestMessageSchema();

  @Test
  public void shouldRoundTripViaSsz() {
    final DataColumnsByRootIdentifierSchema identifierSchema =
        SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
            .getDataColumnsByRootIdentifierSchema();
    final DataColumnSidecarsByRootRequestMessage request =
        schema.of(
            identifierSchema.create(
                Bytes32.fromHexString(
                    "0x1111111111111111111111111111111111111111111111111111111111111111"),
                UInt64.ZERO),
            identifierSchema.create(
                Bytes32.fromHexString(
                    "0x2222222222222222222222222222222222222222222222222222222222222222"),
                UInt64.ONE));
    final Bytes data = request.sszSerialize();
    final DataColumnSidecarsByRootRequestMessage result = schema.sszDeserialize(data);
    assertThatSszData(result).isEqualByAllMeansTo(request);
  }

  @Test
  public void verifyMaxLengthOfContainerIsGreaterOrEqualToMaxRequestDataColumnSidecars() {
    final List<SpecMilestone> peerDasMilestones =
        SpecMilestone.getAllMilestonesFrom(SpecMilestone.FULU);

    peerDasMilestones.forEach(
        milestone -> {
          final Spec spec = TestSpecFactory.createMainnet(milestone);
          final SpecConfig config = spec.forMilestone(milestone).getConfig();
          final SpecConfigFulu specConfigFulu = SpecConfigFulu.required(config);
          final int maxRequestBlocksDeneb = specConfigFulu.getMaxRequestBlocksDeneb();
          final DataColumnSidecarsByRootRequestMessageSchema schema =
              SchemaDefinitionsFulu.required(spec.forMilestone(milestone).getSchemaDefinitions())
                  .getDataColumnSidecarsByRootRequestMessageSchema();
          assertThat(schema.getMaxLength()).isEqualTo(maxRequestBlocksDeneb);
        });
  }

  @Test
  public void verifyImpossibleToOverflowIntegerWithMaxChunksCount() {
    final List<SpecMilestone> peerDasMilestones =
        SpecMilestone.getAllMilestonesFrom(SpecMilestone.FULU);
    final UInt64 maxInteger = UInt64.valueOf(Integer.MAX_VALUE);

    peerDasMilestones.forEach(
        milestone -> {
          final Spec spec = TestSpecFactory.createMainnet(milestone);
          final DataColumnSidecarsByRootRequestMessageSchema schema =
              SchemaDefinitionsFulu.required(spec.forMilestone(milestone).getSchemaDefinitions())
                  .getDataColumnSidecarsByRootRequestMessageSchema();
          final DataColumnsByRootIdentifierSchema identifierSchema =
              SchemaDefinitionsFulu.required(spec.forMilestone(milestone).getSchemaDefinitions())
                  .getDataColumnsByRootIdentifierSchema();

          assertThat(
                  UInt64.valueOf(schema.getMaxLength())
                      .times(UInt64.valueOf(identifierSchema.getColumnsSchema().getMaxLength())))
              .isLessThan(maxInteger);
        });
  }
}
