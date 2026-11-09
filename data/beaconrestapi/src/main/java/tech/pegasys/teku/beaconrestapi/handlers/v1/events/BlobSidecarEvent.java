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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.KZG_COMMITMENT_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.VERSIONED_HASH_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.BlobSidecarEvent.BlobSidecarData;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class BlobSidecarEvent extends Event<BlobSidecarData> {

  public static final SerializableTypeDefinition<BlobSidecarData> BLOB_SIDECAR_EVENT_TYPE =
      SerializableTypeDefinition.object(BlobSidecarData.class)
          .name("BlobSidecarEvent")
          .withField("block_root", BYTES32_TYPE, BlobSidecarData::getBlockRoot)
          .withField("index", UINT64_TYPE, BlobSidecarData::getIndex)
          .withField("slot", UINT64_TYPE, BlobSidecarData::getSlot)
          .withField("kzg_commitment", KZG_COMMITMENT_TYPE, BlobSidecarData::getKzgCommitment)
          .withField("versioned_hash", VERSIONED_HASH_TYPE, BlobSidecarData::getVersionedHash)
          .build();

  private BlobSidecarEvent(
      final Bytes32 blockRoot,
      final UInt64 index,
      final UInt64 slot,
      final KZGCommitment kzgCommitment,
      final VersionedHash versionedHash) {
    super(
        BLOB_SIDECAR_EVENT_TYPE,
        new BlobSidecarData(blockRoot, index, slot, kzgCommitment, versionedHash));
  }

  public static BlobSidecarEvent create(final Spec spec, final BlobSidecar blobSidecar) {
    return new BlobSidecarEvent(
        blobSidecar.getBlockRoot(),
        blobSidecar.getIndex(),
        blobSidecar.getSlot(),
        blobSidecar.getKZGCommitment(),
        spec.atSlot(blobSidecar.getSlot())
            .miscHelpers()
            .kzgCommitmentToVersionedHash(blobSidecar.getKZGCommitment()));
  }

  public static class BlobSidecarData {
    private final Bytes32 blockRoot;
    private final UInt64 index;
    private final UInt64 slot;
    private final KZGCommitment kzgCommitment;
    private final VersionedHash versionedHash;

    BlobSidecarData(
        final Bytes32 blockRoot,
        final UInt64 index,
        final UInt64 slot,
        final KZGCommitment kzgCommitment,
        final VersionedHash versionedHash) {
      this.blockRoot = blockRoot;
      this.index = index;
      this.slot = slot;
      this.kzgCommitment = kzgCommitment;
      this.versionedHash = versionedHash;
    }

    public Bytes32 getBlockRoot() {
      return blockRoot;
    }

    public UInt64 getIndex() {
      return index;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public KZGCommitment getKzgCommitment() {
      return kzgCommitment;
    }

    public VersionedHash getVersionedHash() {
      return versionedHash;
    }
  }
}
