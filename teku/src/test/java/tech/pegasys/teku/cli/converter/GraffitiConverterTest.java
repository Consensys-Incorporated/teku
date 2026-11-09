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

package tech.pegasys.teku.cli.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class GraffitiConverterTest {
  private final String data = "The quick brown fox jumps over the lazy dog";

  final GraffitiConverter converter = new GraffitiConverter();

  @Test
  public void shouldRejectStringsThatAreTooLong() {
    assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert(data));
  }

  @Test
  public void shouldAcceptShortStrings() {
    final Bytes32 bytes =
        Bytes32.fromHexString("0x5468650000000000000000000000000000000000000000000000000000000000");
    assertThat(converter.convert(data.substring(0, 3))).isEqualTo(bytes);
  }

  @Test
  public void shouldAcceptMaxLengthStrings() {
    final Bytes32 bytes =
        Bytes32.fromHexString("0x54686520717569636b2062726f776e20666f78206a756d7073206f7665722074");
    assertThat(converter.convert(data.substring(0, 32))).isEqualTo(bytes);
  }

  @Test
  public void shouldAcceptEmptyStrings() {
    assertThat(converter.convert("")).isEqualTo(Bytes32.ZERO);
  }
}
