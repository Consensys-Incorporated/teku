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

package tech.pegasys.teku.spec.datastructures.operations.versions.pq;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;

/** A PQ indexed attestation: the attesters' XMSS signatures are proven by {@code proof}. */
public class IndexedAttestationPq
    extends Container3<IndexedAttestationPq, SszUInt64List, AttestationData, SszByteList>
    implements IndexedAttestation {

  IndexedAttestationPq(final IndexedAttestationPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  IndexedAttestationPq(
      final IndexedAttestationPqSchema schema,
      final SszUInt64List attestingIndices,
      final AttestationData data,
      final SszByteList proof) {
    super(schema, attestingIndices, data, proof);
  }

  @Override
  public IndexedAttestationPqSchema getSchema() {
    return (IndexedAttestationPqSchema) super.getSchema();
  }

  @Override
  public SszUInt64List getAttestingIndices() {
    return getField0();
  }

  @Override
  public AttestationData getData() {
    return getField1();
  }

  /** The {@code AttestationProof} of the attesters' XMSS signatures. */
  public Bytes getProof() {
    return getField2().getBytes();
  }

  /** A PQ indexed attestation has a proof instead of a signature. */
  @Override
  public BLSSignature getSignature() {
    throw new UnsupportedOperationException(
        "A PQ indexed attestation has a proof, not a signature");
  }
}
