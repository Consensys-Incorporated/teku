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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class BlobParametersTest {

  @Test
  void shouldGenerateHash() {
    final BlobParameters blobParameters = new BlobParameters(UInt64.ZERO, 0);
    assertThat(blobParameters.hash().toHexString())
        .isEqualTo("0x374708fff7719dd5979ec875d56cd2286f6d3cf7ec317a3b25632aab28ec37bb");
  }

  @Test
  void shouldGenerateHashWithMax() {
    final BlobParameters blobParameters = new BlobParameters(UInt64.MAX_VALUE, Integer.MAX_VALUE);
    assertThat(blobParameters.hash().toHexString())
        .isEqualTo("0xa2ce7dca614bc2af3d7cfe9f14c8e7d4380a48716763dafdf02af7f7cd418354");
  }

  @Test
  void shouldGenerateHashWithSaneValues() {
    final BlobParameters blobParameters = new BlobParameters(UInt64.valueOf(65535), 4095);
    assertThat(blobParameters.hash().toHexString())
        .isEqualTo("0x6e5a7df2b9832bd601e9a31bd425d534149ff2b1c08f5a56c6dc8505c47ec6ef");
  }
}
