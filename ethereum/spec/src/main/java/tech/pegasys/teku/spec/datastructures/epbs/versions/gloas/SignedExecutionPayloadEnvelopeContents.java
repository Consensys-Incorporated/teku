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

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class SignedExecutionPayloadEnvelopeContents
    extends Container3<
        SignedExecutionPayloadEnvelopeContents,
        SignedExecutionPayloadEnvelope,
        SszList<SszKZGProof>,
        SszList<Blob>> {

  SignedExecutionPayloadEnvelopeContents(
      final SignedExecutionPayloadEnvelopeContentsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedExecutionPayloadEnvelopeContents(
      final SignedExecutionPayloadEnvelopeContentsSchema schema,
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope,
      final List<KZGProof> kzgProofs,
      final List<Blob> blobs) {
    super(
        schema,
        signedExecutionPayloadEnvelope,
        schema
            .getKzgProofsSchema()
            .createFromElements(kzgProofs.stream().map(SszKZGProof::new).toList()),
        schema.getBlobsSchema().createFromElements(blobs));
  }

  public SignedExecutionPayloadEnvelope getSignedExecutionPayloadEnvelope() {
    return getField0();
  }

  public SszList<SszKZGProof> getKzgProofs() {
    return getField1();
  }

  public SszList<Blob> getBlobs() {
    return getField2();
  }

  public UInt64 getSlot() {
    return getSignedExecutionPayloadEnvelope().getSlot();
  }

  public Bytes32 getBeaconBlockRoot() {
    return getSignedExecutionPayloadEnvelope().getBeaconBlockRoot();
  }

  @Override
  public SignedExecutionPayloadEnvelopeContentsSchema getSchema() {
    return (SignedExecutionPayloadEnvelopeContentsSchema) super.getSchema();
  }
}
