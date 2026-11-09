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

package tech.pegasys.teku.spec.schemas.registry;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaTypesTest {

  @Test
  public void shouldProvideContainerNameViaSchemaId() {
    final SchemaId<String> schemaId = SchemaTypes.create("MY_TEST_SCHEMA");
    assertEquals("MyTest", schemaId.getSchemaName());
    assertEquals("MyTestDeneb", schemaId.getSchemaName(SpecMilestone.DENEB));
  }

  @Test
  public void validateStaticFieldNamesAndSchemaIdNames() throws IllegalAccessException {
    // Get all declared fields in the SchemaTypes class
    final Field[] fields = SchemaTypes.class.getDeclaredFields();

    for (final Field field : fields) {
      // Ensure the field is static and final
      if (isStatic(field.getModifiers()) && isFinal(field.getModifiers())) {

        // Get the field name
        final String fieldName = field.getName();

        assertThat(fieldName).matches("^[A-Z][A-Z_]*_SCHEMA$");

        // Get the value of the field
        if (field.get(null) instanceof SchemaId<?> schemaId) {
          assertEquals(
              fieldName,
              schemaId.getName(),
              "Field name does not match the create argument for field: " + fieldName);
        }
      }
    }
  }
}
