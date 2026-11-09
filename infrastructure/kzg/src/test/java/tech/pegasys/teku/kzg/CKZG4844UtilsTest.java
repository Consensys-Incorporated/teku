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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ethereum.ckzg4844.CKZG4844JNI;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CKZG4844UtilsTest {

  @BeforeAll
  public static void setUp() {
    CKZG4844JNI.loadNativeLibrary();
  }

  @Test
  public void testFlattenBlobsWithUnexpectedSizeThrows() {
    final int blobCount = 42;
    final List<Bytes> blobs = IntStream.range(0, blobCount).mapToObj(__ -> Bytes.of()).toList();
    final IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> CKZG4844Utils.flattenBlobs(blobs));
    assertThat(exception)
        .hasMessage(
            "The actual bytes to flatten (0) was not the same as the expected size specified (5505024)");
  }

  @Test
  public void testFlattenBlobsWithBigBoySizeThrows() {
    final int blobCount = Integer.MAX_VALUE / CKZG4844JNI.BYTES_PER_BLOB;
    final List<Bytes> blobs = IntStream.range(0, blobCount).mapToObj(__ -> Bytes.of()).toList();
    final IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> CKZG4844Utils.flattenBlobs(blobs));
    assertThat(exception)
        .hasMessage("Maximum of 100663296 bytes can be flattened, but 2147352576 were requested");
  }
}
