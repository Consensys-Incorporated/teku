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

package tech.pegasys.teku.bls.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Collections;
import org.junit.jupiter.api.Test;

public abstract class AbstractSignatureTest {

  protected abstract BLS12381 getBls();

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    Signature signature = getBls().randomSignature(42);
    assertEquals(signature, signature);
    assertEquals(signature.hashCode(), signature.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalSignatures() {
    Signature signature = getBls().randomSignature(117);
    Signature copyOfSignature = getBls().randomSignature(117);
    assertEquals(signature, copyOfSignature);
    assertEquals(signature.hashCode(), copyOfSignature.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    Signature signature1 = getBls().randomSignature(1);
    Signature signature2 = getBls().randomSignature(2);
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenPassingEmptyListToAggregateSignaturesDoesNotThrowException() {
    assertDoesNotThrow(() -> getBls().aggregateSignatures(Collections.emptyList()));
  }

  @Test
  void roundtripEncodeDecodeCompressed() {
    Signature signature = getBls().randomSignature(513);
    final Signature result = getBls().signatureFromCompressed(signature.toBytesCompressed());
    assertEquals(signature, result);
    assertEquals(signature.hashCode(), result.hashCode());
  }
}
