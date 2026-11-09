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

package tech.pegasys.teku.spec.datastructures.execution.versions.heze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedProgressiveByteListsNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

/// Companion to {@code ExecutionPayloadPackedTransactionsTest}: the heze {@code InclusionList}
/// transactions list is the identical {@code List[Transaction, MAX_TRANSACTIONS_PER_PAYLOAD]}
/// shape and also carries the progressive {@code SszPackedByteListsHint}.
public class InclusionListPackedTransactionsTest {

  @Test
  public void inclusionListTransactions_shouldRoundTripWithPackedBacking() {
    final Spec hezeSpec = TestSpecFactory.createMinimalHeze();
    final SchemaDefinitionsHeze schemaDefinitionsHeze =
        SchemaDefinitionsHeze.required(hezeSpec.getGenesisSchemaDefinitions());
    final InclusionListSchema inclusionListSchema = schemaDefinitionsHeze.getInclusionListSchema();

    // deterministic, non-empty transaction set: three transactions of different sizes, including
    // one zero-length one, so the packed offset table has more than a single entry
    final List<Bytes> transactions = List.of(Bytes.of(1), Bytes.EMPTY, Bytes.wrap(new byte[33]));

    final InclusionList inclusionList =
        inclusionListSchema.create(
            UInt64.valueOf(1), UInt64.valueOf(2), Bytes32.ZERO, transactions);
    assertThat(inclusionList.getTransactions()).isNotEmpty();
    assertThat(inclusionList.getTransactions().getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX))
        .isInstanceOf(SszPackedProgressiveByteListsNode.class);

    final Bytes serialized = inclusionList.sszSerialize();
    final InclusionList deserialized = inclusionListSchema.sszDeserialize(serialized);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(inclusionList.hashTreeRoot());
    assertThat(deserialized.sszSerialize()).isEqualTo(serialized);
    assertThat(deserialized.getTransactions().getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX))
        .isInstanceOf(SszPackedProgressiveByteListsNode.class);
    for (int i = 0; i < transactions.size(); i++) {
      assertThat(deserialized.getTransactions().get(i).getBytes()).isEqualTo(transactions.get(i));
    }
  }
}
