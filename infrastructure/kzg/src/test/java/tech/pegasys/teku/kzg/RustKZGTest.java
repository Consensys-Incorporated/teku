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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public final class RustKZGTest extends KZGAbstractTest {
  public RustKZGTest() {
    super(RustKZG.getInstance());
  }

  @ParameterizedTest(name = "blob={0}")
  @ValueSource(
      strings = {
        "0x0d2024ece3e004271319699b8b00cc010628b6bc0be5457f031fb1db0afd3ff8",
        "0x",
        "0x925668a49d06f4"
      })
  @Override
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
              assertThat(cause).isInstanceOf(IllegalArgumentException.class);
              final IllegalArgumentException rootException = (IllegalArgumentException) cause;
              assertThat(rootException.getMessage())
                  .contains("blob is not the correct size. expected: 131072");
            });
  }

  @Test
  @Override
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
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageMatching(
                        "Number of blobs\\(\\d+\\), commitments\\(\\d+\\) and proofs\\(\\d+\\) do not match"));
  }

  @SuppressWarnings("JavaCase")
  @Test
  @Override
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

    AssertionsForInterfaceTypes.assertThat(exceptions)
        .allSatisfy(
            exception ->
                assertThat(exception).cause().hasMessage("KZG context context has been destroyed"));
  }

  @Override
  public void incorrectTrustedSetupFilesShouldThrow(final String filename) {
    // Skip, built-in trusted setup only
  }
}
