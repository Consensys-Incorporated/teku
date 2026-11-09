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

package tech.pegasys.teku.bls.keystore.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;

class CipherTest {
  @ParameterizedTest
  @ValueSource(ints = {7, 17})
  void cipherWithInvalidIvLengthThrowsException(final int bytesSize) {
    assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(new Cipher(Bytes.random(bytesSize))::validate)
        .withMessage("Initialization Vector parameter iv size must be >= 8 and <= 16");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 16})
  void cipherWithValidIvLengthValidateDoesNotThrowException(final int bytesSize) {
    assertThatCode(new Cipher(Bytes.random(bytesSize))::validate).doesNotThrowAnyException();
  }
}
