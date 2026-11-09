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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedContributionAndProof
    extends Container2<SignedContributionAndProof, ContributionAndProof, SszSignature> {

  protected SignedContributionAndProof(
      final SignedContributionAndProofSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected SignedContributionAndProof(
      final SignedContributionAndProofSchema schema,
      final ContributionAndProof message,
      final SszSignature signature) {
    super(schema, message, signature);
  }

  public ContributionAndProof getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  @Override
  public SignedContributionAndProofSchema getSchema() {
    return (SignedContributionAndProofSchema) super.getSchema();
  }
}
