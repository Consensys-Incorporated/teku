/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.phase0.ssz_generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;

public abstract class AbstractSszGenericTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final Bytes inputData = TestDataUtils.readSszData(testDefinition, "serialized.ssz_snappy");
    final Path path = testDefinition.getTestDirectory().resolve("value.yaml");
    if (Files.exists(path)) {
      final SszSchema<?> schema = getSchema(testDefinition);
      final Meta meta = TestDataUtils.loadYaml(testDefinition, "meta.yaml", Meta.class);
      final SszData result = schema.sszDeserialize(inputData);
      assertThat(result.hashTreeRoot()).isEqualTo(Bytes32.fromHexString(meta.root));
      assertValueCorrect(testDefinition, result);
    } else {
      try {
        final SszSchema<?> schema = getSchema(testDefinition);
        assertThatThrownBy(() -> schema.sszDeserialize(inputData))
            .isInstanceOf(IllegalArgumentException.class);
      } catch (final IllegalArgumentException e) {
        // The schema itself was invalid so this counts as success
        // Can't just put getSchema in the assertThatThrownBy because we need to let
        // TestAbortedException actually throw so that the test is ignored if we don't support the
        // type
      }
    }
  }

  protected void assertValueCorrect(final TestDefinition testDefinition, final SszData result)
      throws IOException {
    final String stringValue = TestDataUtils.loadYaml(testDefinition, "value.yaml", String.class);
    assertStringValueMatches(testDefinition, stringValue, result);
  }

  protected void assertStringValueMatches(
      final TestDefinition testDefinition, final String stringValue, final SszData result) {
    assertThat(result).isEqualTo(parseString(testDefinition, stringValue));
  }

  protected abstract SszSchema<?> getSchema(final TestDefinition testDefinition);

  protected abstract Object parseString(final TestDefinition testDefinition, final String value);

  protected static int getSize(final TestDefinition testDefinition) {
    final String name = testDefinition.getTestName();
    final String typePart = name.substring(name.indexOf('/') + 1);
    final String withoutTypePrefix = typePart.substring(typePart.indexOf('_') + 1);
    final String size =
        withoutTypePrefix.contains("_")
            ? withoutTypePrefix.substring(0, withoutTypePrefix.indexOf('_'))
            : withoutTypePrefix;
    try {
      return Integer.parseInt(size);
    } catch (final NumberFormatException e) {
      if (name.startsWith("invalid/")) {
        return Integer.MAX_VALUE;
      }
      Assertions.fail(
          "Could not determine bitlist size for %s. %s failed to parse as a number", name, size);
      return 0;
    }
  }

  private static class Meta {
    @JsonProperty(value = "root", required = true)
    private String root;
  }
}
