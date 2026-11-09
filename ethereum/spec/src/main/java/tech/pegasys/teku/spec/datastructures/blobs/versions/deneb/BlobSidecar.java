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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public class BlobSidecar
    extends Container6<
        BlobSidecar,
        SszUInt64,
        Blob,
        SszKZGCommitment,
        SszKZGProof,
        SignedBeaconBlockHeader,
        SszBytes32Vector> {

  private volatile boolean kzgValidated = false;
  private volatile boolean kzgCommitmentInclusionProofValidated = false;
  private volatile boolean signatureValidated = false;

  BlobSidecar(final BlobSidecarSchema blobSidecarSchema, final TreeNode backingTreeNode) {
    super(blobSidecarSchema, backingTreeNode);
  }

  public BlobSidecar(
      final BlobSidecarSchema schema,
      final UInt64 index,
      final Blob blob,
      final SszKZGCommitment sszKzgCommitment,
      final SszKZGProof sszKzgProof,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentInclusionProof) {
    super(
        schema,
        SszUInt64.of(index),
        schema.getBlobSchema().create(blob.getBytes()),
        sszKzgCommitment,
        sszKzgProof,
        signedBeaconBlockHeader,
        schema
            .getKzgCommitmentInclusionProofSchema()
            .createFromElements(kzgCommitmentInclusionProof.stream().map(SszBytes32::of).toList()));
  }

  public BlobSidecar(
      final BlobSidecarSchema schema,
      final UInt64 index,
      final Blob blob,
      final KZGCommitment kzgCommitment,
      final KZGProof kzgProof,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentInclusionProof) {
    this(
        schema,
        index,
        blob,
        new SszKZGCommitment(kzgCommitment),
        new SszKZGProof(kzgProof),
        signedBeaconBlockHeader,
        kzgCommitmentInclusionProof);
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public Blob getBlob() {
    return getField1();
  }

  public SszKZGCommitment getSszKZGCommitment() {
    return getField2();
  }

  public KZGCommitment getKZGCommitment() {
    return getField2().getKZGCommitment();
  }

  public SszKZGProof getSszKZGProof() {
    return getField3();
  }

  public KZGProof getKZGProof() {
    return getField3().getKZGProof();
  }

  public SignedBeaconBlockHeader getSignedBeaconBlockHeader() {
    return getField4();
  }

  public SszBytes32Vector getKzgCommitmentInclusionProof() {
    return getField5();
  }

  public UInt64 getSlot() {
    return getSignedBeaconBlockHeader().getMessage().getSlot();
  }

  public Bytes32 getBlockBodyRoot() {
    return getSignedBeaconBlockHeader().getMessage().getBodyRoot();
  }

  public Bytes32 getBlockRoot() {
    return getSignedBeaconBlockHeader().getMessage().getRoot();
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return new SlotAndBlockRoot(getSlot(), getBlockRoot());
  }

  public SlotAndBlockRootAndBlobIndex getSlotAndBlockRootAndBlobIndex() {
    return new SlotAndBlockRootAndBlobIndex(getSlot(), getBlockRoot(), getIndex());
  }

  public String toLogString() {
    return LogFormatter.formatBlobSidecar(
        getSlot(),
        getBlockRoot(),
        getIndex(),
        getBlob().toBriefString(),
        getKZGCommitment().toAbbreviatedString(),
        getKZGProof().toAbbreviatedString());
  }

  public boolean isKzgValidated() {
    return kzgValidated;
  }

  public boolean isKzgCommitmentInclusionProofValidated() {
    return kzgCommitmentInclusionProofValidated;
  }

  public boolean isSignatureValidated() {
    return signatureValidated;
  }

  public void markKzgAsValidated() {
    kzgValidated = true;
  }

  public void markKzgCommitmentInclusionProofAsValidated() {
    kzgCommitmentInclusionProofValidated = true;
  }

  public void markSignatureAsValidated() {
    signatureValidated = true;
  }

  @Override
  public BlobSidecarSchema getSchema() {
    return (BlobSidecarSchema) super.getSchema();
  }
}
