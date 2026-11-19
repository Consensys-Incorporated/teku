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

package tech.pegasys.teku.xmss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Runs EthLib's leanXMSS vectors (see {@code ethlib/README.md}) through {@link Xmss}. */
class XmssEthLibVectorsTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  static Stream<Arguments> keyGenCases() throws Exception {
    return cases("key_gen");
  }

  static Stream<Arguments> signCases() throws Exception {
    return cases("sign");
  }

  static Stream<Arguments> signatureIndexCases() throws Exception {
    return cases("signature_index");
  }

  static Stream<Arguments> verifyCases() throws Exception {
    return cases("verify");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("keyGenCases")
  void keyGen(final String name, final JsonNode input, final JsonNode output) {
    final Bytes seed = bytes(input, "seed");
    if (output.isNull()) {
      assertThatThrownBy(() -> XmssSecretKey.fromBytes(seed))
          .isInstanceOf(IllegalArgumentException.class);
      return;
    }
    assertThat(Xmss.derivePublicKey(XmssSecretKey.fromBytes(seed)))
        .isEqualTo(XmssPublicKey.fromHexString(output.asText()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("signCases")
  void sign(final String name, final JsonNode input, final JsonNode output) {
    final Bytes seed = bytes(input, "seed");
    final Bytes message = bytes(input, "message");
    final UInt64 leafIndex = UInt64.valueOf(input.get("leaf_index").asLong());
    if (output.isNull()) {
      assertThatThrownBy(
              () -> {
                XmssSecretKey.fromBytes(seed);
                Bytes32.wrap(message);
              })
          .isInstanceOf(IllegalArgumentException.class);
      return;
    }
    try (XmssSigningKey key = Xmss.loadSigningKey(XmssSecretKey.fromBytes(seed))) {
      assertThat(key.sign(leafIndex, Bytes32.wrap(message)))
          .isEqualTo(XmssSignature.fromHexString(output.asText()));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("signatureIndexCases")
  void signatureIndex(final String name, final JsonNode input, final JsonNode output) {
    final Bytes signature = bytes(input, "signature");
    if (output.isNull()) {
      assertThatThrownBy(() -> XmssSignature.fromBytes(signature))
          .isInstanceOf(IllegalArgumentException.class);
      return;
    }
    assertThat(XmssSignature.fromBytes(signature).getLeafIndex())
        .isEqualTo(UInt64.valueOf(output.asLong()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("verifyCases")
  void verify(final String name, final JsonNode input, final JsonNode output) {
    final Bytes publicKey = bytes(input, "public_key");
    final Bytes message = bytes(input, "message");
    final Bytes signature = bytes(input, "signature");
    if (output.isNull()) {
      assertThatThrownBy(
              () -> {
                XmssPublicKey.fromBytes(publicKey);
                Bytes32.wrap(message);
                XmssSignature.fromBytes(signature);
              })
          .isInstanceOf(IllegalArgumentException.class);
      return;
    }
    final XmssSignature xmssSignature = XmssSignature.fromBytes(signature);
    assertThat(
            Xmss.verify(
                XmssPublicKey.fromBytes(publicKey),
                xmssSignature.getLeafIndex(),
                Bytes32.wrap(message),
                xmssSignature))
        .isEqualTo(output.asBoolean());
  }

  private static Bytes bytes(final JsonNode input, final String field) {
    return Bytes.fromHexString(input.get(field).asText());
  }

  private static Stream<Arguments> cases(final String operation)
      throws IOException, URISyntaxException {
    final Path dir =
        Path.of(XmssEthLibVectorsTest.class.getResource("ethlib/" + operation).toURI());
    final List<Path> caseDirs;
    try (Stream<Path> paths = Files.list(dir)) {
      caseDirs = paths.filter(Files::isDirectory).sorted().toList();
    }
    assertThat(caseDirs).isNotEmpty();
    return caseDirs.stream()
        .map(
            caseDir -> {
              try {
                final JsonNode data = YAML.readTree(caseDir.resolve("data.yaml").toFile());
                return Arguments.of(
                    caseDir.getFileName().toString(), data.get("input"), data.get("output"));
              } catch (final IOException e) {
                throw new IllegalStateException(e);
              }
            });
  }
}
