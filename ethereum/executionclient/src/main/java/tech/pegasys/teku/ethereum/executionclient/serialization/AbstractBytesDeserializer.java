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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Arrays;

/**
 * Abstract base for hex-string Jackson deserializers. Subclasses implement {@link
 * #fromRawBytes(byte[])} to wrap the decoded byte array into the target type.
 *
 * <p>Uses {@link JsonParser#getTextCharacters()} to avoid allocating an intermediate {@link String}
 * for the token value, and a lookup table to convert hex nibbles without conditional branching.
 */
public abstract class AbstractBytesDeserializer<T> extends JsonDeserializer<T> {

  // ASCII char → nibble value; -1 for invalid characters
  private static final int[] HEX_VALUES = new int[128];

  static {
    Arrays.fill(HEX_VALUES, -1);
    for (int i = 0; i <= 9; i++) {
      HEX_VALUES['0' + i] = i;
    }
    for (int i = 0; i < 6; i++) {
      HEX_VALUES['a' + i] = 10 + i;
      HEX_VALUES['A' + i] = 10 + i;
    }
  }

  @Override
  public final T deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    final char[] chars = p.getTextCharacters();
    if (chars != null) {
      return fromRawBytes(parseHex(chars, p.getTextOffset(), p.getTextLength()));
    }
    // Fallback for parser implementations that don't expose the internal char buffer
    final String hex = p.getValueAsString();
    return fromRawBytes(parseHex(hex.toCharArray(), 0, hex.length()));
  }

  /**
   * Wraps the decoded byte array into the target type. Implementations should validate the length
   * and throw {@link IllegalArgumentException} if it is incorrect.
   */
  protected abstract T fromRawBytes(byte[] bytes);

  static byte[] parseHex(final char[] chars, final int startOffset, final int length) {
    int start = startOffset;
    final int end = startOffset + length;
    if (length >= 2
        && chars[start] == '0'
        && (chars[start + 1] == 'x' || chars[start + 1] == 'X')) {
      start += 2;
    }
    final int hexLen = end - start;
    if (hexLen % 2 != 0) {
      throw new IllegalArgumentException(
          "Invalid hex string: odd number of hex characters after 0x prefix");
    }
    final byte[] result = new byte[hexLen / 2];
    int pos = start;
    for (int i = 0; i < result.length; i++) {
      final int hi = hexValue(chars[pos++]);
      if (hi < 0) {
        throwValidationException(pos - 1, chars[pos - 1]);
      }
      final int lo = hexValue(chars[pos++]);
      if (lo < 0) {
        throwValidationException(pos - 1, chars[pos - 1]);
      }
      result[i] = (byte) ((hi << 4) | lo);
    }
    return result;
  }

  private static void throwValidationException(final int pos, final char c) {
    throw new IllegalArgumentException(
        "Invalid hex character at position " + pos + ": '" + c + "'");
  }

  private static int hexValue(final char c) {
    return c < HEX_VALUES.length ? HEX_VALUES[c] : -1;
  }
}
