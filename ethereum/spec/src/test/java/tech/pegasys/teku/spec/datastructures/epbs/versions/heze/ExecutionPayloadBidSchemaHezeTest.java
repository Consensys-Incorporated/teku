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

package tech.pegasys.teku.spec.datastructures.epbs.versions.heze;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.INCLUSION_LIST_BITS;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionPayloadBidSchemaHezeTest {

  @Test
  void gloasSchemaDoesNotIncludeInclusionListBits() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final ExecutionPayloadBidSchema<? extends ExecutionPayloadBid> schema =
        SchemaDefinitionsGloas.required(spec.atSlot(ZERO).getSchemaDefinitions())
            .getExecutionPayloadBidSchema();

    assertThat(schema).isNotInstanceOf(ExecutionPayloadBidSchemaHeze.class);
    assertThat(schema.getFieldIndex(INCLUSION_LIST_BITS)).isEqualTo(-1);
  }

  @Test
  void hezeSchemaIncludesInclusionListBits() {
    final Spec spec = TestSpecFactory.createMinimalHeze();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(spec.atSlot(ZERO).getSchemaDefinitions());
    final ExecutionPayloadBidSchemaHeze schema = schemaDefinitions.getExecutionPayloadBidSchema();
    final SpecConfigHeze specConfigHeze = SpecConfigHeze.required(spec.atSlot(ZERO).getConfig());

    final ExecutionPayloadBid bid =
        schema.createLocalSelfBuiltBid(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomSlot(),
            dataStructureUtil.randomExecutionPayload(),
            dataStructureUtil.randomBlobKzgCommitments(schema.getBlobKzgCommitmentsSchema()),
            dataStructureUtil.randomBytes32(),
            schema.getInclusionListBitsSchema().ofBits(0, 2));

    assertThat(schema).isInstanceOf(ExecutionPayloadBidSchemaHeze.class);
    assertThat(schema.getFieldIndex(INCLUSION_LIST_BITS)).isEqualTo(12);
    assertThat(bid).isInstanceOf(ExecutionPayloadBidHeze.class);
    assertThat(((ExecutionPayloadBidHeze) bid).getInclusionListBits())
        .hasSize(specConfigHeze.getInclusionListCommitteeSize());
    assertThat(((ExecutionPayloadBidHeze) bid).getInclusionListBits().streamAllSetBits().toArray())
        .containsExactly(0, 2);
  }
}
