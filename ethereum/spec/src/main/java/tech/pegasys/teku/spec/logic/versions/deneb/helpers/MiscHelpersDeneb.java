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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class MiscHelpersDeneb extends MiscHelpersCapella {
  private final Predicates predicates;
  private final BeaconBlockBodySchemaDeneb<?> beaconBlockBodySchema;
  private final BlobSidecarSchema blobSidecarSchema;
  private final SpecConfigDeneb specConfigDeneb;

  private volatile KZG kzg;

  public static MiscHelpersDeneb required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionDeneb()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Deneb misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  public void setKzg(final KZG kzg) {
    this.kzg = kzg;
  }

  public KZG getKzg() {
    final KZG kzg = this.kzg;
    if (kzg == null) {
      throw new IllegalStateException("KZG not set");
    }
    return kzg;
  }

  public MiscHelpersDeneb(
      final SpecConfigDeneb specConfig,
      final Predicates predicates,
      final SchemaDefinitionsDeneb schemaDefinitions) {
    super(specConfig);
    this.specConfigDeneb = specConfig;
    this.predicates = predicates;
    this.beaconBlockBodySchema =
        (BeaconBlockBodySchemaDeneb<?>) schemaDefinitions.getBeaconBlockBodySchema();
    this.blobSidecarSchema = schemaDefinitions.getBlobSidecarSchema();
  }

  /**
   * Performs <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/deneb/polynomial-commitments.md#verify_blob_kzg_proof">verify_blob_kzg_proof</a>
   * on the given blob sidecar
   *
   * @param blobSidecar blob sidecar to verify
   * @return true if blob sidecar is valid
   */
  @Override
  public boolean verifyBlobKzgProof(final BlobSidecar blobSidecar) {
    if (blobSidecar.isKzgValidated()) {
      return true;
    }
    final boolean result =
        getKzg()
            .verifyBlobKzgProof(
                blobSidecar.getBlob().getBytes(),
                blobSidecar.getKZGCommitment(),
                blobSidecar.getKZGProof());

    if (result) {
      blobSidecar.markKzgAsValidated();
    }

    return result;
  }

  /**
   * Performs <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/deneb/polynomial-commitments.md#verify_blob_kzg_proof_batch">verify_blob_kzg_proof_batch</a>
   * on the given blob sidecars
   *
   * @param blobSidecars blob sidecars to verify, can be a partial set
   * @return true if all blob sidecars are valid
   */
  @Override
  public boolean verifyBlobKzgProofBatch(final List<BlobSidecar> blobSidecars) {
    final List<Bytes> blobs = new ArrayList<>();
    final List<KZGCommitment> kzgCommitments = new ArrayList<>();
    final List<KZGProof> kzgProofs = new ArrayList<>();

    blobSidecars.stream()
        .filter(blobSidecar -> !blobSidecar.isKzgValidated())
        .forEach(
            blobSidecar -> {
              blobs.add(blobSidecar.getBlob().getBytes());
              kzgCommitments.add(blobSidecar.getKZGCommitment());
              kzgProofs.add(blobSidecar.getKZGProof());
            });

    if (blobs.isEmpty()) {
      return true;
    }

    final boolean result = getKzg().verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs);

    if (result) {
      blobSidecars.stream()
          .filter(blobSidecar -> !blobSidecar.isKzgValidated())
          .forEach(BlobSidecar::markKzgAsValidated);
    }

    return result;
  }

  @Override
  public boolean verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
      final BlobSidecar blobSidecar, final SignedBeaconBlock signedBeaconBlock) {
    if (blobSidecar.isSignatureValidated()) {
      return true;
    }

    final boolean result =
        blobSidecar
            .getSignedBeaconBlockHeader()
            .hashTreeRoot()
            .equals(signedBeaconBlock.hashTreeRoot());

    if (result) {
      blobSidecar.markSignatureAsValidated();
    }

    return result;
  }

  /**
   * Verifies that blob sidecars are complete and with expected indices
   *
   * @param completeVerifiedBlobSidecars blob sidecars to verify, It is assumed that it is an
   *     ordered list based on BlobSidecar index
   * @param signedBeaconBlock block with commitments
   */
  @Override
  public void verifyBlobSidecarCompleteness(
      final List<BlobSidecar> completeVerifiedBlobSidecars,
      final SignedBeaconBlock signedBeaconBlock)
      throws IllegalArgumentException {
    int commitmentCount =
        signedBeaconBlock
            .getBeaconBlock()
            .map(BeaconBlock::getBody)
            .flatMap(BeaconBlockBody::getOptionalBlobKzgCommitments)
            .orElseThrow()
            .size();
    checkArgument(
        completeVerifiedBlobSidecars.size() == commitmentCount, "Blob sidecars are not complete");

    IntStream.range(0, completeVerifiedBlobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = completeVerifiedBlobSidecars.get(index);
              final UInt64 blobIndex = blobSidecar.getIndex();

              checkArgument(
                  blobIndex.longValue() == index,
                  "Blob sidecar index mismatch, expected %s, got %s",
                  index,
                  blobIndex);
            });
  }

  /**
   * <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/deneb/beacon-chain.md#kzg_commitment_to_versioned_hash">kzg_commitment_to_versioned_hash</a>
   */
  @Override
  public VersionedHash kzgCommitmentToVersionedHash(final KZGCommitment kzgCommitment) {
    return VersionedHash.create(
        VERSIONED_HASH_VERSION_KZG, Hash.sha256(kzgCommitment.getBytesCompressed()));
  }

  @Override
  public UInt64 getMaxRequestBlocks() {
    return UInt64.valueOf(SpecConfigDeneb.required(specConfig).getMaxRequestBlocksDeneb());
  }

  @Override
  public Optional<MiscHelpersDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  @Override
  public int getBlobKzgCommitmentsCount(final SignedBeaconBlock signedBeaconBlock) {
    return signedBeaconBlock
        .getMessage()
        .getBody()
        .getOptionalBlobKzgCommitments()
        .map(SszList::size)
        .orElse(0);
  }

  public int getBlobSidecarKzgCommitmentGeneralizedIndex(final UInt64 blobSidecarIndex) {
    final long blobKzgCommitmentsGeneralizedIndex =
        beaconBlockBodySchema.getBlobKzgCommitmentsGeneralizedIndex();
    final long commitmentGeneralizedIndex =
        beaconBlockBodySchema
            .getBlobKzgCommitmentsSchema()
            .getChildGeneralizedIndex(blobSidecarIndex.longValue());
    return (int)
        GIndexUtil.gIdxCompose(blobKzgCommitmentsGeneralizedIndex, commitmentGeneralizedIndex);
  }

  public List<Bytes32> computeBlobKzgCommitmentInclusionProof(
      final UInt64 blobSidecarIndex, final BeaconBlockBody beaconBlockBody) {
    return MerkleUtil.constructMerkleProof(
        beaconBlockBody.getBackingNode(),
        getBlobSidecarKzgCommitmentGeneralizedIndex(blobSidecarIndex));
  }

  public BlobSidecar constructBlobSidecar(
      final SignedBeaconBlock signedBeaconBlock,
      final UInt64 index,
      final Blob blob,
      final SszKZGProof proof) {
    final BeaconBlockBody beaconBlockBody = signedBeaconBlock.getMessage().getBody();
    final SszKZGCommitment commitment;
    try {
      commitment =
          beaconBlockBody.getOptionalBlobKzgCommitments().orElseThrow().get(index.intValue());
    } catch (final IndexOutOfBoundsException | NoSuchElementException ex) {
      final int commitmentsCount = getBlobKzgCommitmentsCount(signedBeaconBlock);
      throw new IllegalArgumentException(
          String.format(
              "Can't create blob sidecar with index %s because there are %d commitment(s) in block",
              index, commitmentsCount));
    }
    final List<Bytes32> kzgCommitmentInclusionProof =
        computeBlobKzgCommitmentInclusionProof(index, beaconBlockBody);
    return blobSidecarSchema.create(
        index, blob, commitment, proof, signedBeaconBlock.asHeader(), kzgCommitmentInclusionProof);
  }

  public BlobSidecar constructBlobSidecarFromBlobAndProof(
      final BlobIdentifier blobIdentifier,
      final BlobAndProof blobAndProof,
      final BeaconBlockBodyDeneb beaconBlockBodyDeneb,
      final SignedBeaconBlockHeader signedBeaconBlockHeader) {

    final SszKZGCommitment sszKZGCommitment =
        beaconBlockBodyDeneb.getBlobKzgCommitments().get(blobIdentifier.getIndex().intValue());

    final BlobSidecar blobSidecar =
        blobSidecarSchema.create(
            blobIdentifier.getIndex(),
            blobAndProof.blob(),
            sszKZGCommitment,
            new SszKZGProof(blobAndProof.proof()),
            signedBeaconBlockHeader,
            computeBlobKzgCommitmentInclusionProof(
                blobIdentifier.getIndex(), beaconBlockBodyDeneb));

    blobSidecar.markSignatureAsValidated();
    blobSidecar.markKzgCommitmentInclusionProofAsValidated();
    // assume kzg validation done by local EL
    blobSidecar.markKzgAsValidated();

    return blobSidecar;
  }

  public boolean verifyBlobKzgCommitmentInclusionProof(final BlobSidecar blobSidecar) {
    if (blobSidecar.isKzgCommitmentInclusionProofValidated()) {
      return true;
    }
    final boolean result =
        predicates.isValidMerkleBranch(
            blobSidecar.getSszKZGCommitment().hashTreeRoot(),
            blobSidecar.getKzgCommitmentInclusionProof(),
            SpecConfigDeneb.required(specConfig).getKzgCommitmentInclusionProofDepth(),
            getBlobSidecarKzgCommitmentGeneralizedIndex(blobSidecar.getIndex()),
            blobSidecar.getBlockBodyRoot());

    if (result) {
      blobSidecar.markKzgCommitmentInclusionProofAsValidated();
    }

    return result;
  }

  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return currentEpoch
        .minusMinZero(epoch)
        .isLessThanOrEqualTo(specConfigDeneb.getMinEpochsForBlobSidecarsRequests());
  }
}
