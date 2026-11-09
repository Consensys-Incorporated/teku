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

package tech.pegasys.teku.ethereum.executionclient.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Abstract test base for all {@link AbstractBytesDeserializer} subclasses. Verifies the shared
 * hex-parsing behaviour: prefix handling, case-insensitivity, and error cases.
 *
 * <p>Concrete subclasses implement {@link #createDeserializer()} and {@link #validHex()} to supply
 * the type-specific configuration.
 */
abstract class AbstractBytesDeserializerTest<T> {

  /** Returns a fresh instance of the deserializer under test. */
  abstract AbstractBytesDeserializer<T> createDeserializer();

  /**
   * Returns a valid, lowercase hex string prefixed with {@code "0x"} whose byte length is correct
   * for the target type.
   */
  abstract String validHex();

  T deserialize(final String hexValue) throws IOException {
    JsonParser p = new JsonFactory().createParser("\"" + hexValue + "\"");
    p.nextToken();
    return createDeserializer().deserialize(p, null);
  }

  @Test
  void shouldDeserializeWithLowercasePrefix() throws IOException {
    assertThat(deserialize(validHex())).isNotNull();
  }

  @Test
  void shouldDeserializeWithUppercaseHex() throws IOException {
    String upper = "0x" + validHex().substring(2).toUpperCase(Locale.ROOT);
    assertThat(deserialize(upper)).isNotNull();
  }

  @Test
  void shouldDeserializeWithUppercaseXPrefix() throws IOException {
    String upperX = "0X" + validHex().substring(2);
    assertThat(deserialize(upperX)).isNotNull();
  }

  @Test
  void shouldDeserializeWithoutPrefix() throws IOException {
    String noPrefix = validHex().substring(2);
    assertThat(deserialize(noPrefix)).isNotNull();
  }

  @Test
  void shouldThrowOnOddNibbles() {
    String odd = validHex() + "a";
    assertThatThrownBy(() -> deserialize(odd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("odd number of hex characters");
  }

  @Test
  void shouldThrowOnInvalidHexCharacter() {
    // Replace first hex digit (position 2, after "0x") with 'g'
    String withInvalid = validHex().substring(0, 2) + "g" + validHex().substring(3);
    assertThatThrownBy(() -> deserialize(withInvalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid hex character");
  }
}
