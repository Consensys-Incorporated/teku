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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatures;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;

public class SignedProposerPreferences
    extends Container2<SignedProposerPreferences, ProposerPreferences, SszByteVector> {

  SignedProposerPreferences(
      final SignedProposerPreferencesSchema schema,
      final ProposerPreferences message,
      final BLSSignature signature) {
    super(schema, message, new SszSignature(signature));
  }

  SignedProposerPreferences(
      final SignedProposerPreferencesSchema schema,
      final ProposerPreferences message,
      final SszXmssSignature signature) {
    super(schema, message, signature);
  }

  SignedProposerPreferences(
      final SignedProposerPreferencesSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public ProposerPreferences getMessage() {
    return getField0();
  }

  /**
   * @throws UnsupportedOperationException if signed with leanXMSS (PQ)
   */
  public BLSSignature getSignature() {
    return SszSignatures.blsSignature(getField1());
  }

  public boolean isXmssSigned() {
    return SszSignatures.isXmss(getField1());
  }

  /**
   * @throws UnsupportedOperationException if signed with BLS
   */
  public Bytes getXmssSignature() {
    return SszSignatures.xmssSignature(getField1());
  }

  @Override
  public SignedProposerPreferencesSchema getSchema() {
    return (SignedProposerPreferencesSchema) super.getSchema();
  }
}
