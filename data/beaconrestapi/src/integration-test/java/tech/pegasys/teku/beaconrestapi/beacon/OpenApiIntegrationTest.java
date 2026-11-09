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

package tech.pegasys.teku.beaconrestapi.beacon;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.infrastructure.restapi.OpenApiTestUtil;

public class OpenApiIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private JsonNode currentJsonNodes;
  private final OpenApiTestUtil<OpenApiIntegrationTest> util =
      new OpenApiTestUtil<>(OpenApiIntegrationTest.class);

  @BeforeEach
  public void setup() throws IOException {
    startRestAPIAtGenesisWithHighestSupportedMilestone();
    currentJsonNodes = util.parseSwagger(getOpenApiDoc().body().string());
  }

  @Test
  void schemaObjects_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode schemas = currentJsonNodes.findPath("components").findPath("schemas");
    util.compareToKnownDefinitions(tempDir, "schema", schemas);
  }

  @Test
  void shouldHaveConsistentReferences() {
    util.checkReferences(currentJsonNodes);
  }

  @Test
  void paths_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode paths = currentJsonNodes.findPath("paths");

    util.compareToKnownDefinitions(tempDir, "paths", paths);
  }

  public Response getOpenApiDoc() throws IOException {
    return getResponse("/swagger-docs");
  }
}
