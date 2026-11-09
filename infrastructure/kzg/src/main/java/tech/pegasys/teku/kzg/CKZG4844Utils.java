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

import static tech.pegasys.teku.kzg.KZG.BYTES_PER_G1;
import static tech.pegasys.teku.kzg.KZG.BYTES_PER_G2;

import com.google.common.base.Preconditions;
import ethereum.ckzg4844.CKZG4844JNI;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;

class CKZG4844Utils {
  private static final int MAX_BYTES_TO_FLATTEN = 100_663_296; // ~100.66 MB or 768 blobs

  public static byte[] flattenBlobs(final List<Bytes> blobs) {
    return flattenBytes(blobs, CKZG4844JNI.BYTES_PER_BLOB * blobs.size());
  }

  public static byte[] flattenCommitments(final List<KZGCommitment> commitments) {
    return flattenBytes(
        commitments,
        KZGCommitment::toArrayUnsafe,
        CKZG4844JNI.BYTES_PER_COMMITMENT * commitments.size());
  }

  public static byte[] flattenProofs(final List<KZGProof> kzgProofs) {
    return flattenBytes(
        kzgProofs, KZGProof::toArrayUnsafe, CKZG4844JNI.BYTES_PER_PROOF * kzgProofs.size());
  }

  public static byte[] flattenG1Points(final List<Bytes> g1Points) {
    return flattenBytes(g1Points, BYTES_PER_G1 * g1Points.size());
  }

  public static byte[] flattenG2Points(final List<Bytes> g2Points) {
    return flattenBytes(g2Points, BYTES_PER_G2 * g2Points.size());
  }

  public static TrustedSetup parseTrustedSetupFile(final String trustedSetupFile)
      throws IOException {
    final String sanitizedTrustedSetup = UrlSanitizer.sanitizePotentialUrl(trustedSetupFile);
    final InputStream resource =
        ResourceLoader.urlOrFile("application/octet-stream")
            .load(trustedSetupFile)
            .orElseThrow(() -> new FileNotFoundException(sanitizedTrustedSetup + " is not found"));
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      // txt format :
      // Number of G1 points
      final int g1Size = Integer.parseInt(reader.readLine());
      // Number of G2 points
      final int g2Size = Integer.parseInt(reader.readLine());
      // List of G1 Lagrange points, one on each new line
      final List<Bytes> g1PointsLagrange = new ArrayList<>();
      for (int i = 0; i < g1Size; i++) {
        final Bytes g1Point = Bytes.fromHexString(reader.readLine(), BYTES_PER_G1);
        g1PointsLagrange.add(g1Point);
      }
      // List of G2 Monomial points, one on each new line
      final List<Bytes> g2PointsMonomial = new ArrayList<>();
      for (int i = 0; i < g2Size; i++) {
        final Bytes g2Point = Bytes.fromHexString(reader.readLine(), BYTES_PER_G2);
        g2PointsMonomial.add(g2Point);
      }
      // List of G1 Monomial points, one on each new line
      final List<Bytes> g1PointsMonomial = new ArrayList<>();
      for (int i = 0; i < g1Size; i++) {
        final Bytes g1Point = Bytes.fromHexString(reader.readLine(), BYTES_PER_G1);
        g1PointsMonomial.add(g1Point);
      }

      return new TrustedSetup(g1PointsLagrange, g2PointsMonomial, g1PointsMonomial);
    } catch (final Exception ex) {
      throw new IOException(
          String.format("Failed to parse trusted setup file\n: %s", trustedSetupFile));
    }
  }

  static List<Bytes> chunkBytes(final Bytes bytes, final int chunkSize) {
    if (bytes.size() % chunkSize != 0) {
      throw new IllegalArgumentException("Invalid bytes size: " + bytes.size());
    }
    return IntStream.range(0, bytes.size() / chunkSize)
        .map(i -> i * chunkSize)
        .mapToObj(startIdx -> bytes.slice(startIdx, chunkSize))
        .toList();
  }

  static byte[] flattenBytes(final List<Bytes> toFlatten, final int expectedSize) {
    return flattenBytes(toFlatten, Bytes::toArrayUnsafe, expectedSize);
  }

  private static <T> byte[] flattenBytes(
      final List<T> toFlatten, final Function<T, byte[]> bytesConverter, final int expectedSize) {
    Preconditions.checkArgument(
        expectedSize <= MAX_BYTES_TO_FLATTEN,
        "Maximum of %s bytes can be flattened, but %s were requested",
        MAX_BYTES_TO_FLATTEN,
        expectedSize);
    final byte[] flattened = new byte[expectedSize];
    int destPos = 0;
    for (final T data : toFlatten) {
      final byte[] bytes = bytesConverter.apply(data);
      System.arraycopy(bytes, 0, flattened, destPos, bytes.length);
      destPos += bytes.length;
    }
    if (destPos != expectedSize) {
      throw new IllegalArgumentException(
          String.format(
              "The actual bytes to flatten (%d) was not the same as the expected size specified (%d)",
              destPos, expectedSize));
    }
    return flattened;
  }
}
