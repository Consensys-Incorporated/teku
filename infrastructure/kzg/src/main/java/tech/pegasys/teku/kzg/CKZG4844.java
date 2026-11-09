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

package tech.pegasys.teku.kzg;

import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;
import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_COMMITMENT;

import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CellsAndProofs;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Wrapper around jc-kzg-4844
 *
 * <p>This class should be a singleton
 */
final class CKZG4844 implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  @SuppressWarnings("NonFinalStaticField")
  private static CKZG4844 instance;

  static synchronized CKZG4844 getInstance() {
    if (instance == null) {
      instance = new CKZG4844();
    }
    return instance;
  }

  private Optional<String> loadedTrustedSetupFile = Optional.empty();

  private CKZG4844() {
    try {
      CKZG4844JNI.loadNativeLibrary();
      LOG.info("Loaded C-KZG-4844 library");
    } catch (final Exception ex) {
      throw new KZGException("Failed to load C-KZG-4844 library", ex);
    }
  }

  /** Only one trusted setup at a time can be loaded. */
  @Override
  public synchronized void loadTrustedSetup(final String trustedSetupFile, final int kzgPrecompute)
      throws KZGException {
    if (loadedTrustedSetupFile.isPresent()
        && loadedTrustedSetupFile.get().equals(trustedSetupFile)) {
      LOG.trace("Trusted setup from {} is already loaded", trustedSetupFile);
      return;
    }
    try {
      loadedTrustedSetupFile.ifPresent(
          currentTrustedSetupFile -> {
            LOG.debug(
                "Freeing current trusted setup {} in order to load trusted setup from {}",
                currentTrustedSetupFile,
                trustedSetupFile);
            freeTrustedSetup();
          });
      final TrustedSetup trustedSetup = CKZG4844Utils.parseTrustedSetupFile(trustedSetupFile);
      final List<Bytes> g1PointsLagrange = trustedSetup.g1Lagrange();
      final List<Bytes> g2PointsMonomial = trustedSetup.g2Monomial();
      final List<Bytes> g1PointsMonomial = trustedSetup.g1Monomial();
      CKZG4844JNI.loadTrustedSetup(
          CKZG4844Utils.flattenG1Points(g1PointsMonomial),
          CKZG4844Utils.flattenG1Points(g1PointsLagrange),
          CKZG4844Utils.flattenG2Points(g2PointsMonomial),
          kzgPrecompute);
      LOG.debug("Loaded trusted setup from {}", trustedSetupFile);
      loadedTrustedSetupFile = Optional.of(trustedSetupFile);
    } catch (final Exception ex) {
      throw new KZGException("Failed to load trusted setup from " + trustedSetupFile, ex);
    }
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    try {
      CKZG4844JNI.freeTrustedSetup();
      loadedTrustedSetupFile = Optional.empty();
      LOG.debug("Trusted setup was freed");
    } catch (final Exception ex) {
      throw new KZGException("Failed to free trusted setup", ex);
    }
  }

  @Override
  public boolean verifyBlobKzgProof(
      final Bytes blob, final KZGCommitment kzgCommitment, final KZGProof kzgProof)
      throws KZGException {
    try {
      return CKZG4844JNI.verifyBlobKzgProof(
          blob.toArrayUnsafe(), kzgCommitment.toArrayUnsafe(), kzgProof.toArrayUnsafe());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blob and commitment against KZG proof " + kzgProof, ex);
    }
  }

  @Override
  public boolean verifyBlobKzgProofBatch(
      final List<Bytes> blobs,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs)
      throws KZGException {
    try {
      final byte[] blobsBytes = CKZG4844Utils.flattenBlobs(blobs);
      final byte[] commitmentsBytes = CKZG4844Utils.flattenCommitments(kzgCommitments);
      final byte[] proofsBytes = CKZG4844Utils.flattenProofs(kzgProofs);
      return CKZG4844JNI.verifyBlobKzgProofBatch(
          blobsBytes, commitmentsBytes, proofsBytes, blobs.size());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blobs and commitments against KZG proofs " + kzgProofs, ex);
    }
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    try {
      final byte[] commitmentBytes = CKZG4844JNI.blobToKzgCommitment(blob.toArrayUnsafe());
      return KZGCommitment.fromArray(commitmentBytes);
    } catch (final Exception ex) {
      throw new KZGException("Failed to produce KZG commitment from blob", ex);
    }
  }

  @Override
  public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
      throws KZGException {
    try {
      final byte[] proof =
          CKZG4844JNI.computeBlobKzgProof(blob.toArrayUnsafe(), kzgCommitment.toArrayUnsafe());
      return KZGProof.fromArray(proof);
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to compute KZG proof for blob with commitment " + kzgCommitment, ex);
    }
  }

  @Override
  public List<KZGCell> computeCells(final Bytes blob) throws KZGException {
    try {
      final byte[] cells = CKZG4844JNI.computeCells(blob.toArrayUnsafe());
      return KZGCell.splitBytes(Bytes.wrap(cells));
    } catch (final Exception ex) {
      throw new KZGException("Failed to compute KZG cells for blob " + blob.toShortHexString(), ex);
    }
  }

  @Override
  public List<KZGCellAndProof> computeCellsAndProofs(final Bytes blob) {
    final CellsAndProofs cellsAndProofs;
    try {
      cellsAndProofs = CKZG4844JNI.computeCellsAndKzgProofs(blob.toArrayUnsafe());
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to compute KZG cells and proofs for blob " + blob.toShortHexString(), ex);
    }
    final List<KZGCell> cells = KZGCell.splitBytes(Bytes.wrap(cellsAndProofs.getCells()));
    final List<KZGProof> proofs = KZGProof.splitBytes(Bytes.wrap(cellsAndProofs.getProofs()));
    if (cells.size() != proofs.size()) {
      throw new KZGException("Cells and proofs size differ");
    }
    return IntStream.range(0, cells.size())
        .mapToObj(i -> new KZGCellAndProof(cells.get(i), proofs.get(i)))
        .toList();
  }

  @Override
  public boolean verifyCellProofBatch(
      final List<KZGCommitment> commitments,
      final List<KZGCellWithColumnId> cellWithIdList,
      final List<KZGProof> proofs) {
    if (commitments.size() != cellWithIdList.size() || cellWithIdList.size() != proofs.size()) {
      throw new KZGException("Cells, proofs and commitments sizes should match");
    }
    try {
      return CKZG4844JNI.verifyCellKzgProofBatch(
          CKZG4844Utils.flattenBytes(
              commitments.stream()
                  .map(kzgCommitment -> (Bytes) kzgCommitment.getBytesCompressed())
                  .toList(),
              commitments.size() * BYTES_PER_COMMITMENT),
          cellWithIdList.stream()
              .mapToLong(cellWithIds -> cellWithIds.columnId().id().longValue())
              .toArray(),
          CKZG4844Utils.flattenBytes(
              cellWithIdList.stream().map(cellWithIds -> cellWithIds.cell().bytes()).toList(),
              cellWithIdList.size() * BYTES_PER_CELL),
          CKZG4844Utils.flattenProofs(proofs));
    } catch (final Exception ex) {
      throw new KZGException("Failed to verify cells with proofs, incorrect input", ex);
    }
  }

  @Override
  public List<KZGCellAndProof> recoverCellsAndProofs(final List<KZGCellWithColumnId> cells) {
    final List<KZGCell> fullCells;
    final List<KZGProof> fullProofs;
    try {
      final long[] cellIds = cells.stream().mapToLong(c -> c.columnId().id().longValue()).toArray();
      final byte[] cellBytes =
          CKZG4844Utils.flattenBytes(
              cells.stream().map(c -> c.cell().bytes()).toList(), cells.size() * BYTES_PER_CELL);
      final CellsAndProofs cellsAndProofs =
          CKZG4844JNI.recoverCellsAndKzgProofs(cellIds, cellBytes);
      fullCells = KZGCell.splitBytes(Bytes.wrap(cellsAndProofs.getCells()));
      fullProofs = KZGProof.splitBytes(Bytes.wrap(cellsAndProofs.getProofs()));
    } catch (final Exception ex) {
      throw new KZGException("Failed to recover all KZG cells and proofs from cells", ex);
    }

    if (fullCells.size() != fullProofs.size()) {
      throw new KZGException("Cells and proofs size differ");
    }
    return IntStream.range(0, fullCells.size())
        .mapToObj(i -> new KZGCellAndProof(fullCells.get(i), fullProofs.get(i)))
        .toList();
  }
}
