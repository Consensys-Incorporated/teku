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

package tech.pegasys.teku.spec.datastructures.blocks;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatures;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;

/**
 * A signed block header. Up to PQ it is BLS-signed ({@link #SSZ_SCHEMA}); from PQ it is signed with
 * leanXMSS ({@link #SSZ_SCHEMA_PQ}).
 */
public class SignedBeaconBlockHeader
    extends Container2<SignedBeaconBlockHeader, BeaconBlockHeader, SszByteVector> {

  public static final SignedBeaconBlockHeaderSchema SSZ_SCHEMA =
      new SignedBeaconBlockHeaderSchema();

  public static final SignedBeaconBlockHeaderSchema SSZ_SCHEMA_PQ =
      new SignedBeaconBlockHeaderSchema(
          "SignedBeaconBlockHeaderPq", SszXmssSignatureSchema.INSTANCE);

  SignedBeaconBlockHeader(final SignedBeaconBlockHeaderSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBeaconBlockHeader(final BeaconBlockHeader message, final BLSSignature signature) {
    super(SSZ_SCHEMA, message, new SszSignature(signature));
  }

  /** A PQ header, signed with leanXMSS. */
  public SignedBeaconBlockHeader(
      final BeaconBlockHeader message, final SszXmssSignature xmssSignature) {
    super(SSZ_SCHEMA_PQ, message, xmssSignature);
  }

  public BeaconBlockHeader getMessage() {
    return getField0();
  }

  /**
   * @throws UnsupportedOperationException for a PQ header, which is signed with leanXMSS
   */
  public BLSSignature getSignature() {
    return SszSignatures.blsSignature(getField1());
  }

  public boolean isXmssSigned() {
    return SszSignatures.isXmss(getField1());
  }

  /**
   * @throws UnsupportedOperationException for a header signed with BLS
   */
  public Bytes getXmssSignature() {
    return SszSignatures.xmssSignature(getField1());
  }

  @Override
  public SignedBeaconBlockHeaderSchema getSchema() {
    return (SignedBeaconBlockHeaderSchema) super.getSchema();
  }
}
