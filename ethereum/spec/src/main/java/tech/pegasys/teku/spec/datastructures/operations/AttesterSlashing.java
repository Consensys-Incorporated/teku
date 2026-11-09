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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttesterSlashing
    extends Container2<AttesterSlashing, IndexedAttestation, IndexedAttestation> {
  private final IntersectingIndicesCalculator intersectingIndicesCalculator;

  AttesterSlashing(final AttesterSlashingSchema type, final TreeNode backingNode) {
    super(type, backingNode);
    this.intersectingIndicesCalculator = new IntersectingIndicesCalculator(this);
  }

  AttesterSlashing(
      final AttesterSlashingSchema schema,
      final IndexedAttestation attestation1,
      final IndexedAttestation attestation2) {
    super(schema, attestation1, attestation2);
    this.intersectingIndicesCalculator = new IntersectingIndicesCalculator(this);
  }

  @Override
  public AttesterSlashingSchema getSchema() {
    return (AttesterSlashingSchema) super.getSchema();
  }

  public Set<UInt64> getIntersectingValidatorIndices() {
    return intersectingIndicesCalculator.getIntersectingValidatorIndices();
  }

  public IndexedAttestation getAttestation1() {
    return getField0();
  }

  public IndexedAttestation getAttestation2() {
    return getField1();
  }
}
