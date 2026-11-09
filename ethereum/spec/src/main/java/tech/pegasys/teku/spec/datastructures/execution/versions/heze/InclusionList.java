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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;

public class InclusionList
    extends Container4<InclusionList, SszUInt64, SszUInt64, SszBytes32, SszList<Transaction>> {

  InclusionList(
      final InclusionListSchema schema,
      final UInt64 slot,
      final UInt64 validatorIndex,
      final Bytes32 inclusionListCommitteeRoot,
      final List<Bytes> transactions) {
    super(
        schema,
        SszUInt64.of(slot),
        SszUInt64.of(validatorIndex),
        SszBytes32.of(inclusionListCommitteeRoot),
        transactions.stream()
            .map(schema.getTransactionSchema()::fromBytes)
            .collect(schema.getTransactionsSchema().collector()));
  }

  InclusionList(final InclusionListSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public UInt64 getValidatorIndex() {
    return getField1().get();
  }

  public Bytes32 getInclusionListCommitteeRoot() {
    return getField2().get();
  }

  public SszList<Transaction> getTransactions() {
    return getField3();
  }
}
