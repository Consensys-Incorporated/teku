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

import com.google.common.collect.Streams;
import ethereum.cryptography.Cells;
import ethereum.cryptography.CellsAndProofs;
import ethereum.cryptography.LibEthKZG;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

/**
 * Wrapper around LibPeerDASKZG Rust PeerDAS library
 *
 * <p>This class should be a singleton
 */
final class RustKZG implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  @SuppressWarnings("NonFinalStaticField")
  private static RustKZG instance;

  private LibEthKZG library;
  private boolean initialized;

  static synchronized RustKZG getInstance() {
    if (instance == null) {
      instance = new RustKZG();
    }
    return instance;
  }

  private RustKZG() {}

  @Override
  public synchronized void loadTrustedSetup(final String trustedSetupFile, final int kzgPrecompute)
      throws KZGException {
    if (!initialized) {
      try {
        final boolean usePrecompute = kzgPrecompute != 0;
        this.library = new LibEthKZG(usePrecompute);
        this.initialized = true;
        LOG.info("Loaded LibPeerDASKZG library with precompute={}", usePrecompute);
      } catch (final Exception ex) {
        throw new KZGException("Failed to load LibPeerDASKZG Rust library", ex);
      }
    }
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    if (!initialized) {
      throw new KZGException("Trusted setup already freed");
    }
    try {
      library.close();
      this.initialized = false;
    } catch (final Exception ex) {
      throw new KZGException("Failed to free trusted setup", ex);
    }
  }

  @Override
  public boolean verifyBlobKzgProof(
      final Bytes blob, final KZGCommitment kzgCommitment, final KZGProof kzgProof)
      throws KZGException {
    try {
      return library.verifyBlobKzgProof(
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
      if (blobs.size() != kzgCommitments.size() || kzgProofs.size() != kzgCommitments.size()) {
        throw new IllegalArgumentException(
            String.format(
                "Number of blobs(%d), commitments(%d) and proofs(%d) do not match",
                blobs.size(), kzgCommitments.size(), kzgProofs.size()));
      }
      final byte[][] blobsBytes = new byte[blobs.size()][];
      final byte[][] commitmentsBytes = new byte[kzgCommitments.size()][];
      final byte[][] proofsBytes = new byte[kzgProofs.size()][];
      for (int i = 0; i < blobs.size(); i++) {
        blobsBytes[i] = blobs.get(i).toArrayUnsafe();
        commitmentsBytes[i] = kzgCommitments.get(i).toArrayUnsafe();
        proofsBytes[i] = kzgProofs.get(i).toArrayUnsafe();
      }
      return library.verifyBlobKzgProofBatch(blobsBytes, commitmentsBytes, proofsBytes);
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to verify blobs and commitments against KZG proofs " + kzgProofs, ex);
    }
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    try {
      final byte[] commitmentBytes = library.blobToKZGCommitment(blob.toArrayUnsafe());
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
          library.computeBlobKzgProof(blob.toArrayUnsafe(), kzgCommitment.toArrayUnsafe());
      return KZGProof.fromArray(proof);
    } catch (final Exception ex) {
      throw new KZGException(
          "Failed to compute KZG proof for blob with commitment " + kzgCommitment, ex);
    }
  }

  @Override
  public List<KZGCell> computeCells(final Bytes blob) {
    final Cells cells = library.computeCells(blob.toArrayUnsafe());
    return KZGCell.splitBytes(Bytes.wrap(cells.toBytes()));
  }

  @Override
  public List<KZGCellAndProof> computeCellsAndProofs(final Bytes blob) {
    final CellsAndProofs cellsAndProofs = library.computeCellsAndKZGProofs(blob.toArrayUnsafe());
    final Stream<KZGCell> kzgCellStream =
        Arrays.stream(cellsAndProofs.getCells()).map(Bytes::wrap).map(KZGCell::new);

    final Stream<KZGProof> kzgProofStream =
        Arrays.stream(cellsAndProofs.getProofs()).map(Bytes48::wrap).map(KZGProof::new);

    return Streams.zip(kzgCellStream, kzgProofStream, KZGCellAndProof::new).toList();
  }

  @Override
  public boolean verifyCellProofBatch(
      final List<KZGCommitment> commitments,
      final List<KZGCellWithColumnId> cellWithIdList,
      final List<KZGProof> proofs) {
    return library.verifyCellKZGProofBatch(
        commitments.stream().map(KZGCommitment::toArrayUnsafe).toArray(byte[][]::new),
        cellWithIdList.stream()
            .mapToLong(cellWithIds -> cellWithIds.columnId().id().longValue())
            .toArray(),
        cellWithIdList.stream()
            .map(cellWithIds -> cellWithIds.cell().bytes().toArrayUnsafe())
            .toArray(byte[][]::new),
        proofs.stream().map(KZGProof::toArrayUnsafe).toArray(byte[][]::new));
  }

  @Override
  public List<KZGCellAndProof> recoverCellsAndProofs(final List<KZGCellWithColumnId> cells) {
    final long[] cellIds = cells.stream().mapToLong(c -> c.columnId().id().longValue()).toArray();
    final byte[][] cellBytes =
        cells.stream().map(c -> c.cell().bytes().toArrayUnsafe()).toArray(byte[][]::new);
    final CellsAndProofs cellsAndProofs = library.recoverCellsAndKZGProofs(cellIds, cellBytes);
    final byte[][] recoveredCells = cellsAndProofs.getCells();
    final Stream<KZGCell> kzgCellStream =
        Arrays.stream(recoveredCells).map(Bytes::wrap).map(KZGCell::new);
    final byte[][] recoveredProofs = cellsAndProofs.getProofs();
    final Stream<KZGProof> kzgProofStream =
        Arrays.stream(recoveredProofs).map(Bytes48::wrap).map(KZGProof::new);
    return Streams.zip(kzgCellStream, kzgProofStream, KZGCellAndProof::new).toList();
  }
}
