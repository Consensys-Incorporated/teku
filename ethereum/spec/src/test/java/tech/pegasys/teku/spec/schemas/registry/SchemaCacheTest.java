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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaCacheTest {

  private SchemaCache schemaCache;

  @BeforeEach
  void setUp() {
    schemaCache = SchemaCache.createDefault();
  }

  @Test
  void shouldPutAndGetSchema() {
    final SchemaId<String> schemaId = SchemaTypes.create("test");
    final SpecMilestone milestone = SpecMilestone.PHASE0;
    final String schema = "Test Schema";

    schemaCache.put(milestone, schemaId, schema);
    final String retrievedSchema = schemaCache.get(milestone, schemaId);

    assertEquals(schema, retrievedSchema);
  }

  @Test
  void shouldReturnNullForNonExistentSchema() {
    final SchemaId<String> schemaId = SchemaTypes.create("nonexistent");
    final SpecMilestone milestone = SpecMilestone.PHASE0;

    final String retrievedSchema = schemaCache.get(milestone, schemaId);

    assertNull(retrievedSchema);
  }

  @Test
  void shouldPutAndGetMultipleSchemasForSameMilestone() {
    final SchemaId<String> schemaId1 = SchemaTypes.create("test1");
    final SchemaId<Integer> schemaId2 = SchemaTypes.create("test2");
    final SpecMilestone milestone = SpecMilestone.PHASE0;
    final String schema1 = "Test Schema 1";
    final Integer schema2 = 42;

    schemaCache.put(milestone, schemaId1, schema1);
    schemaCache.put(milestone, schemaId2, schema2);

    final String retrievedSchema1 = schemaCache.get(milestone, schemaId1);
    final Integer retrievedSchema2 = schemaCache.get(milestone, schemaId2);

    assertEquals(schema1, retrievedSchema1);
    assertEquals(schema2, retrievedSchema2);
  }

  @Test
  void shouldPutAndGetSchemasForDifferentMilestones() {
    final SchemaId<String> schemaId = SchemaTypes.create("test");
    final SpecMilestone milestone1 = SpecMilestone.PHASE0;
    final SpecMilestone milestone2 = SpecMilestone.ALTAIR;
    final String schema1 = "Test Schema 1";
    final String schema2 = "Test Schema 2";

    schemaCache.put(milestone1, schemaId, schema1);
    schemaCache.put(milestone2, schemaId, schema2);

    final String retrievedSchema1 = schemaCache.get(milestone1, schemaId);
    final String retrievedSchema2 = schemaCache.get(milestone2, schemaId);

    assertEquals(schema1, retrievedSchema1);
    assertEquals(schema2, retrievedSchema2);
  }

  @Test
  void shouldOverwriteExistingSchema() {
    final SchemaId<String> schemaId = SchemaTypes.create("test");
    final SpecMilestone milestone = SpecMilestone.PHASE0;
    final String schema1 = "Test Schema 1";
    final String schema2 = "Test Schema 2";

    schemaCache.put(milestone, schemaId, schema1);
    schemaCache.put(milestone, schemaId, schema2);

    final String retrievedSchema = schemaCache.get(milestone, schemaId);

    assertEquals(schema2, retrievedSchema);
  }
}
