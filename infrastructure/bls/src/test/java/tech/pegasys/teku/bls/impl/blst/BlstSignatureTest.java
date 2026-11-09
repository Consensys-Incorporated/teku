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

package tech.pegasys.teku.bls.impl.blst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.bls.impl.blst.BlstSignature.INFINITY;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.AbstractSignatureTest;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.BlsException;

public class BlstSignatureTest extends AbstractSignatureTest {
  private static BLS12381 bls;

  @BeforeAll
  static void setup() {
    bls = BlstLoader.INSTANCE.orElseThrow();
  }

  @Override
  protected BLS12381 getBls() {
    return bls;
  }

  private static final Bytes INFINITY_BYTES =
      Bytes.fromHexString(
          "0x"
              + "c000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000");

  @Test
  void infinityBytesDeserialisesToValidInfinity() {
    BlstSignature signature = BlstSignature.fromBytes(INFINITY_BYTES);
    assertThat(signature.isInfinity()).isTrue();
    assertThat(signature).isEqualTo(INFINITY);
  }

  @Test
  void badSignatureThrowsException() {
    assertThrows(BlsException.class, () -> BlstSignature.fromBytes(Bytes.wrap(new byte[96])));
  }

  @Test
  void infinitySignatureIsInfinity() {
    assertThat(INFINITY.isInfinity()).isTrue();
  }
}
