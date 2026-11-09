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

package tech.pegasys.teku.validator.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class GraffitiParserTest {
  @Test
  void testGraffitiParserLoadsAFileSuccessfully() throws Exception {
    assertThat(GraffitiParser.loadFromFile(Path.of("src/test/resources/graffitiSample.txt")))
        .isEqualTo(Bytes32.rightPad(Bytes.wrap("123456789".getBytes(UTF_8))));
    assertThat(GraffitiParser.loadFromFile(Path.of("src/test/resources/32NullBytes.txt")))
        .isEqualTo(Bytes32.fromHexString("00".repeat(32)));
  }

  @Test
  void testGraffitiParserWithMoreThanMaximumAllowableBytesInTheFile() throws Exception {
    assertThatThrownBy(
            () ->
                GraffitiParser.loadFromFile(
                    Path.of("src/test/resources/graffitiSample41Bytes.txt")))
        .isInstanceOf(GraffitiLoaderException.class);
  }

  @Test
  void testGraffitiParserLoadsEmptyFileSuccessfully() throws Exception {
    assertThat(GraffitiParser.loadFromFile(Path.of("src/test/resources/emptyGraffitiSample.txt")))
        .isEqualTo(Bytes32.ZERO);
  }

  @Test
  void testGraffitiParserDoesTrim() throws Exception {
    assertThat(
            GraffitiParser.loadFromFile(Path.of("src/test/resources/graffitiSampleWithSpaces.txt")))
        .isEqualTo(Bytes32.rightPad(Bytes.wrap("T E K U".getBytes(UTF_8))));
  }

  @Test
  void testStrip() throws Exception {
    assertThat(GraffitiParser.strip(" \n  123  \n\n  ".getBytes(UTF_8))).isEqualTo("123");
  }
}
