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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.ssz_generic.containers.UInt16PrimitiveSchema.UINT16_SCHEMA;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.units.bigints.UInt256;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.reference.TestDataUtils;

public class SszGenericBasicVectorTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected void assertValueCorrect(final TestDefinition testDefinition, final SszData result)
      throws IOException {
    final List<String> expectedValue =
        ((List<?>) TestDataUtils.loadYaml(testDefinition, "value.yaml", List.class))
            .stream().map(value -> parseString(testDefinition, value.toString())).collect(toList());
    final SszVector<?> vector = (SszVector<?>) result;
    final List<?> actualList = vector.stream().map(Object::toString).collect(toList());
    assertThat(actualList).isEqualTo(expectedValue);
  }

  @Override
  protected SszSchema<?> getSchema(final TestDefinition testDefinition) {
    final int vectorLength = getVectorLength(testDefinition);
    final SszSchema<?> elementSchema = getElementSchema(testDefinition);
    return SszVectorSchema.create(elementSchema, vectorLength);
  }

  @Override
  protected String parseString(final TestDefinition testDefinition, final String value) {
    return switch (getElementType(testDefinition)) {
      // Java will treat the byte as a signed byte so unsigned value to signed byte
      case "uint8" -> Byte.toString((byte) Integer.parseUnsignedInt(value));
      // UInt256.toString outputs hex whereas the tests use decimal - reformat to match
      case "uint256" -> UInt256.valueOf(new BigInteger(value)).toString();
      default -> value;
    };
  }

  // vec_{element type}_{length}
  private SszSchema<?> getElementSchema(final TestDefinition testDefinition) {
    final String elementType = getElementType(testDefinition);
    return switch (elementType) {
      case "uint8" -> SszPrimitiveSchemas.BYTE_SCHEMA;
      case "bool" -> SszPrimitiveSchemas.BOOLEAN_SCHEMA;
      case "uint16" -> UINT16_SCHEMA;
      case "uint64" -> SszPrimitiveSchemas.UINT64_SCHEMA;
      case "uint256" -> SszPrimitiveSchemas.UINT256_SCHEMA;
      case "uint32", "uint128" ->
          throw new TestAbortedException(
              "Element type not supported: "
                  + elementType
                  + " From: "
                  + testDefinition.getTestName());
      default ->
          throw new UnsupportedOperationException(
              "No schema for type: " + testDefinition.getTestName());
    };
  }

  private String getElementType(final TestDefinition testDefinition) {
    final String testName = testDefinition.getTestName();
    final String typePart = testName.substring(testName.indexOf('/') + 1 + "vec_".length());
    return typePart.substring(0, typePart.indexOf("_"));
  }

  private int getVectorLength(final TestDefinition testDefinition) {
    final String testName = testDefinition.getTestName();
    final String typePart = testName.substring(testName.indexOf('/') + 1 + "vec_".length());
    final String lengthString = typePart.split("_", -1)[1];
    return Integer.parseInt(lengthString);
  }
}
