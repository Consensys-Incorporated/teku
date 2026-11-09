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

import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_BLOB;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.kzg.KZG.BLS_MODULUS;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;
import static tech.pegasys.teku.kzg.KZG.FIELD_ELEMENTS_PER_BLOB;

import com.google.common.collect.Streams;
import com.google.common.io.Resources;
import ethereum.ckzg4844.CKZGException;
import ethereum.ckzg4844.CKZGException.CKZGError;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class KZGAbstractTest {

  private static final int RANDOM_SEED = 5566;
  private static final Random RND = new Random(RANDOM_SEED);

  protected final KZG kzg;

  protected KZGAbstractTest(final KZG instance) {
    kzg = instance;
  }

  @BeforeEach
  public void setUp() {
    loadTrustedSetup();
  }

  private void loadTrustedSetup() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  @AfterAll
  public void cleanUp() throws KZGException {
    try {
      kzg.freeTrustedSetup();
    } catch (KZGException ex) {
      // NOOP
    }
  }

  @Test
  public void testKzgLoadSameTrustedSetupTwice_shouldNotThrowException() {
    loadTrustedSetup();
  }

  @Test
  public void testKzgFreeTrustedSetupTwice_shouldThrowException() {
    kzg.freeTrustedSetup();
    assertThrows(KZGException.class, kzg::freeTrustedSetup);
  }

  @Test
  public void testUsageWithoutLoadedTrustedSetup_shouldThrowException() {
    kzg.freeTrustedSetup();
    final List<KZGException> exceptions =
        List.of(
            assertThrows(
                KZGException.class,
                () ->
                    kzg.verifyBlobKzgProofBatch(
                        List.of(Bytes.fromHexString("0x", BYTES_PER_BLOB)),
                        List.of(getSampleCommitment()),
                        List.of(getSampleProof()))),
            assertThrows(KZGException.class, () -> kzg.blobToKzgCommitment(Bytes.EMPTY)),
            assertThrows(
                KZGException.class,
                () -> kzg.computeBlobKzgProof(Bytes.EMPTY, getSampleCommitment())));

    assertThat(exceptions)
        .allSatisfy(
            exception -> assertThat(exception).cause().hasMessage("Trusted Setup is not loaded."));
  }

  @Test
  public void testComputingAndVerifyingBatchProofs() {
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> kzg.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs)).isTrue();

    assertThat(
            kzg.verifyBlobKzgProofBatch(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProofs))
        .isFalse();
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, getSampleCommitments(numberOfBlobs), kzgProofs))
        .isFalse();
    final List<KZGProof> invalidProofs =
        getSampleBlobs(numberOfBlobs).stream()
            .map((Bytes blob) -> kzg.computeBlobKzgProof(blob, kzg.blobToKzgCommitment(blob)))
            .collect(Collectors.toList());
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, invalidProofs)).isFalse();
  }

  @Test
  public void testVerifyingEmptyBatch() {
    assertThat(kzg.verifyBlobKzgProofBatch(List.of(), List.of(), List.of())).isTrue();
  }

  @Test
  public void testComputingAndVerifyingSingleProof() {
    final Bytes blob = getSampleBlob();
    final KZGCommitment kzgCommitment = kzg.blobToKzgCommitment(blob);
    final KZGProof kzgProof = kzg.computeBlobKzgProof(blob, kzgCommitment);

    assertThat(kzg.verifyBlobKzgProof(blob, kzgCommitment, kzgProof)).isTrue();

    assertThat(kzg.verifyBlobKzgProof(getSampleBlob(), kzgCommitment, kzgProof)).isFalse();
    assertThat(kzg.verifyBlobKzgProof(blob, getSampleCommitment(), kzgProof)).isFalse();
    final Bytes randomBlob = getSampleBlob();
    final KZGProof invalidProof =
        kzg.computeBlobKzgProof(randomBlob, kzg.blobToKzgCommitment(randomBlob));
    assertThat(kzg.verifyBlobKzgProof(blob, kzgCommitment, invalidProof)).isFalse();
  }

  @Test
  public void testComputingAndVerifyingBatchSingleProof() {
    final int numberOfBlobs = 1;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> kzg.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    assertThat(kzgProofs.size()).isEqualTo(1);
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs)).isTrue();

    assertThat(
            kzg.verifyBlobKzgProofBatch(getSampleBlobs(numberOfBlobs), kzgCommitments, kzgProofs))
        .isFalse();
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, getSampleCommitments(numberOfBlobs), kzgProofs))
        .isFalse();
    final List<KZGProof> invalidProofs =
        getSampleBlobs(numberOfBlobs).stream()
            .map((Bytes blob) -> kzg.computeBlobKzgProof(blob, kzg.blobToKzgCommitment(blob)))
            .collect(Collectors.toList());
    assertThat(kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, invalidProofs)).isFalse();
  }

  @Test
  public void testVerifyingBatchProofsThrowsIfSizesDoesntMatch() {
    final int numberOfBlobs = 4;
    final List<Bytes> blobs = getSampleBlobs(numberOfBlobs);
    final List<KZGCommitment> kzgCommitments =
        blobs.stream().map(kzg::blobToKzgCommitment).collect(Collectors.toList());
    final List<KZGProof> kzgProofs =
        Streams.zip(
                kzgCommitments.stream(),
                blobs.stream(),
                (kzgCommitment, blob) -> kzg.computeBlobKzgProof(blob, kzgCommitment))
            .collect(Collectors.toList());
    final KZGException kzgException1 =
        assertThrows(
            KZGException.class,
            () -> kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, List.of(kzgProofs.get(0))));
    final KZGException kzgException2 =
        assertThrows(
            KZGException.class,
            () -> kzg.verifyBlobKzgProofBatch(blobs, List.of(kzgCommitments.get(0)), kzgProofs));
    final KZGException kzgException3 =
        assertThrows(
            KZGException.class,
            () -> kzg.verifyBlobKzgProofBatch(List.of(blobs.get(0)), kzgCommitments, kzgProofs));

    Stream.of(kzgException1, kzgException2, kzgException3)
        .forEach(
            ex ->
                assertThat(ex)
                    .cause()
                    .isInstanceOf(CKZGException.class)
                    .hasMessageMatching(
                        "Invalid .+ size. Expected \\d+ bytes but got \\d+. \\(C_KZG_BADARGS\\)"));
  }

  @ParameterizedTest(name = "blob={0}")
  @ValueSource(
      strings = {
        "0x0d2024ece3e004271319699b8b00cc010628b6bc0be5457f031fb1db0afd3ff8",
        "0x",
        "0x925668a49d06f4"
      })
  public void testComputingProofWithIncorrectLengthBlobDoesNotCauseSegfault(final String blobHex) {
    final Bytes blob = Bytes.fromHexString(blobHex);

    final KZGException kzgException =
        assertThrows(
            KZGException.class, () -> kzg.computeBlobKzgProof(blob, kzg.blobToKzgCommitment(blob)));

    assertThat(kzgException)
        .cause()
        .satisfies(
            cause -> {
              // non-canonical blobs
              assertThat(cause).isInstanceOf(CKZGException.class);
              final CKZGException cryptoException = (CKZGException) cause;
              assertThat(cryptoException.getError()).isEqualTo(CKZGError.C_KZG_BADARGS);
              assertThat(cryptoException.getErrorMessage())
                  .contains("Invalid blob size. Expected 131072 bytes but got");
            });
  }

  @ParameterizedTest(name = "trusted_setup={0}")
  @ValueSource(
      strings = {
        "broken/trusted_setup_g1_length.txt",
        "broken/trusted_setup_g2_length.txt",
        "broken/trusted_setup_g2_bytesize.txt"
      })
  public void incorrectTrustedSetupFilesShouldThrow(final String filename) {
    final Throwable cause =
        assertThrows(
                KZGException.class,
                () -> kzg.loadTrustedSetup(TrustedSetupLoader.getTrustedSetupFile(filename), 0))
            .getCause();
    assertThat(cause.getMessage()).contains("Failed to parse trusted setup file");
  }

  @Disabled("das kzg version crashes")
  @Test
  public void monomialTrustedSetupFilesShouldThrow() {
    final KZGException kzgException =
        assertThrows(
            KZGException.class,
            () ->
                kzg.loadTrustedSetup(
                    TrustedSetupLoader.getTrustedSetupFile("trusted_setup_monomial.txt"), 0));
    assertThat(kzgException.getMessage()).contains("Failed to load trusted setup");
    assertThat(kzgException.getCause().getMessage())
        .contains("There was an error while loading the Trusted Setup. (C_KZG_BADARGS)");
  }

  @Test
  public void testInvalidLengthG2PointInNewTrustedSetup() {
    assertThatThrownBy(
            () -> new TrustedSetup(List.of(), List.of(Bytes.fromHexString("")), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected G2 point to be 96 bytes");
  }

  static final int CELLS_PER_ORIG_BLOB = CELLS_PER_EXT_BLOB / 2;

  @Test
  public void testComputeRecoverCellsAndProofs() {
    Bytes blob = getSampleBlob();
    List<KZGCellAndProof> cellAndProofs = kzg.computeCellsAndProofs(blob);
    assertThat(cellAndProofs).hasSize(CELLS_PER_EXT_BLOB);

    List<KZGCellWithColumnId> cellsToRecover =
        IntStream.range(CELLS_PER_ORIG_BLOB, CELLS_PER_EXT_BLOB)
            .mapToObj(
                i ->
                    new KZGCellWithColumnId(
                        cellAndProofs.get(i).cell(), KZGCellID.fromCellColumnIndex(i)))
            .toList();

    List<KZGCellAndProof> recoveredCells = kzg.recoverCellsAndProofs(cellsToRecover);
    assertThat(recoveredCells).isEqualTo(cellAndProofs);
  }

  @Test
  public void testComputeCellsAndProofsEqualsComputeCells() {
    Bytes blob = getSampleBlob();
    List<KZGCellAndProof> cellAndProofs = kzg.computeCellsAndProofs(blob);
    List<KZGCell> cells = kzg.computeCells(blob);
    assertThat(cells).isEqualTo(cellAndProofs.stream().map(KZGCellAndProof::cell).toList());
  }

  List<Bytes> getSampleBlobs(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> getSampleBlob()).collect(Collectors.toList());
  }

  @Test
  public void testComputeAndVerifyCellProof() {
    Bytes blob = getSampleBlob();
    List<KZGCellAndProof> cellAndProofs = kzg.computeCellsAndProofs(blob);
    KZGCommitment kzgCommitment = kzg.blobToKzgCommitment(blob);

    for (int i = 0; i < cellAndProofs.size(); i++) {
      assertThat(
              kzg.verifyCellProofBatch(
                  List.of(kzgCommitment),
                  List.of(KZGCellWithColumnId.fromCellAndColumn(cellAndProofs.get(i).cell(), i)),
                  List.of(cellAndProofs.get(i).proof())))
          .isTrue();
      var invalidProof = cellAndProofs.get((i + 1) % cellAndProofs.size()).proof();
      assertThat(
              kzg.verifyCellProofBatch(
                  List.of(kzgCommitment),
                  List.of(KZGCellWithColumnId.fromCellAndColumn(cellAndProofs.get(i).cell(), i)),
                  List.of(invalidProof)))
          .isFalse();
    }
  }

  @Test
  public void testDataColumnSidecarKzgProofFromDevnet() throws Exception {
    final String sidecarJson =
        Resources.toString(
            Resources.getResource(KZGAbstractTest.class, "good-sidecar.json"),
            StandardCharsets.UTF_8);
    final DeserializableTypeDefinition<DataColumnSidecarJson.CellWithIdJson> cellWithIdJsonType =
        DeserializableTypeDefinition.object(DataColumnSidecarJson.CellWithIdJson.class)
            .name("CellWithIdJson")
            .initializer(DataColumnSidecarJson.CellWithIdJson::new)
            .withField(
                "cell",
                STRING_TYPE,
                DataColumnSidecarJson.CellWithIdJson::getCell,
                DataColumnSidecarJson.CellWithIdJson::setCell)
            .withField(
                "column_id",
                INTEGER_TYPE,
                DataColumnSidecarJson.CellWithIdJson::getColumnId,
                DataColumnSidecarJson.CellWithIdJson::setColumnId)
            .build();
    final DeserializableTypeDefinition<DataColumnSidecarJson> dataColumnSidecarJsonType =
        DeserializableTypeDefinition.object(DataColumnSidecarJson.class)
            .name("DataColumnSidecarJson")
            .initializer(DataColumnSidecarJson::new)
            .withField(
                "commitments",
                DeserializableTypeDefinition.listOf(STRING_TYPE),
                DataColumnSidecarJson::getCommitments,
                DataColumnSidecarJson::setCommitments)
            .withField(
                "cells",
                DeserializableTypeDefinition.listOf(cellWithIdJsonType),
                DataColumnSidecarJson::getCellWithColumnIds,
                DataColumnSidecarJson::setCellWithColumnIds)
            .withField(
                "proofs",
                DeserializableTypeDefinition.listOf(STRING_TYPE),
                DataColumnSidecarJson::getProofs,
                DataColumnSidecarJson::setProofs)
            .build();
    final DataColumnSidecarJson dataColumnSidecarJson =
        JsonUtil.parse(sidecarJson, dataColumnSidecarJsonType);
    assertThat(
            kzg.verifyCellProofBatch(
                dataColumnSidecarJson.getCommitments().stream()
                    .map(str -> new KZGCommitment(Bytes48.fromHexString(str)))
                    .toList(),
                dataColumnSidecarJson.getCellWithColumnIds().stream()
                    .map(
                        cellWithId ->
                            new KZGCellWithColumnId(
                                new KZGCell(Bytes.fromHexString(cellWithId.getCell())),
                                KZGCellID.fromCellColumnIndex(cellWithId.getColumnId())))
                    .toList(),
                dataColumnSidecarJson.getProofs().stream()
                    .map(str -> new KZGProof(Bytes48.fromHexString(str)))
                    .toList()))
        .isTrue();
  }

  Bytes getSampleBlob() {
    return IntStream.range(0, FIELD_ELEMENTS_PER_BLOB)
        .mapToObj(__ -> randomBLSFieldElement())
        .map(fieldElement -> Bytes.wrap(fieldElement.toArray(ByteOrder.BIG_ENDIAN)))
        .reduce(Bytes::wrap)
        .orElse(Bytes.EMPTY);
  }

  private List<KZGCommitment> getSampleCommitments(final int count) {
    return IntStream.range(0, count)
        .mapToObj(__ -> getSampleCommitment())
        .collect(Collectors.toList());
  }

  KZGCommitment getSampleCommitment() {
    return kzg.blobToKzgCommitment(getSampleBlob());
  }

  KZGProof getSampleProof() {
    return kzg.computeBlobKzgProof(getSampleBlob(), getSampleCommitment());
  }

  private UInt256 randomBLSFieldElement() {
    while (true) {
      final BigInteger attempt = new BigInteger(BLS_MODULUS.bitLength(), RND);
      if (attempt.compareTo(BLS_MODULUS) < 0) {
        return UInt256.valueOf(attempt);
      }
    }
  }
}
