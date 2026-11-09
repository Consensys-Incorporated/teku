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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedProgressiveByteListsNode;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/// Covers the {@code ExecutionPayload.transactions} hinted packed-byte-lists backing across every
/// fork that carries a transactions list. See {@code InclusionListPackedTransactionsTest} for the
/// equivalent heze {@code InclusionList} coverage.
@TestSpecContext(milestone = {BELLATRIX, CAPELLA, DENEB, GLOAS})
public class ExecutionPayloadPackedTransactionsTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone milestone;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    milestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  public void executionPayloadTransactions_shouldRoundTripWithPackedBacking() {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    assertThat(payload.getTransactions()).isNotEmpty();

    final Bytes serialized = payload.sszSerialize();
    final ExecutionPayload deserialized = payload.getSchema().sszDeserialize(serialized);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(payload.hashTreeRoot());
    assertThat(deserialized.sszSerialize()).isEqualTo(serialized);
    assertThat(deserialized.getTransactions().getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX))
        .isInstanceOf(
            milestone.isGreaterThanOrEqualTo(GLOAS)
                ? SszPackedProgressiveByteListsNode.class
                : SszPackedByteListsNode.class);
  }
}
