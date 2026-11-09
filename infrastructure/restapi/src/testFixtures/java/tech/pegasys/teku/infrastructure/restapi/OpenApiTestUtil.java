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

package tech.pegasys.teku.infrastructure.restapi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Fail;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;

public class OpenApiTestUtil<TObject> {
  private final ObjectMapper mapper;
  private final Class<TObject> clazz;
  private final String path;

  public OpenApiTestUtil(final Class<TObject> clazz) {
    this.mapper =
        JsonMapper.builder()
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
            .build();
    this.clazz = clazz;
    this.path = clazz.getPackageName().replaceAll("\\.", "/");
  }

  public JsonNode parseSwagger(final String source) throws JsonProcessingException {
    return mapper.readTree(source);
  }

  // This test has 2 parts
  // 1. Go through all the objects in our swagger structure and write them to file, comparing to
  //    expected as we go
  //    - this will catch any changes, which if correct should be updated in the resource file
  //    - will also catch NEW objects, and these new objects will need to be added to the
  //      resources for the test
  // 2. Go through the list of resources and make sure we've created all of those resources
  //    - this will catch any REMOVED items, and if they should be removed

  /**
   * Test swagger structure contains expected paths and schema definitions
   *
   * @param tempDir The tempDir for the test (files need to be written for validation)
   * @param path The path to validate in the json (eg. "paths", "schema")
   * @param parentNode the node under test in the swagger document (eg. paths node or schema node)
   * @throws IOException
   */
  public void compareToKnownDefinitions(
      final Path tempDir, final String path, final JsonNode parentNode) throws IOException {
    List<String> fieldNames = ImmutableList.copyOf(parentNode.fieldNames());
    for (String current : fieldNames) {
      checkStructureIsConsistent(path, tempDir, current, parentNode.path(current));
    }

    checkAllExpectedFilesExist(tempDir, path);
  }

  public void compareToKnownDefinition(final OpenApiTypeDefinition schema)
      throws JsonProcessingException {
    final String filename = schema.getTypeName().orElseThrow() + ".json";
    final String openApiType = JsonUtil.serialize(schema::serializeOpenApiType);
    final JsonNode node = mapper.readTree(openApiType);
    final String namespace = "schema";
    JsonNode expectedNode = null;
    try {
      expectedNode = loadResourceFile(namespace + "/" + filename);
    } catch (Exception e) {
      Fail.fail(
          String.format(
              "Expected to find %s with content %s, does it need to be added?",
              namespace + "/" + filename, prettyJson(node)));
    }
    assertThat(node)
        .describedAs(
            String.format("Structure of %s -> (%s)", clazz.getName(), namespace + "/" + filename))
        .withFailMessage(
            String.format("Expected: %s\nbut was: %s", prettyJson(expectedNode), prettyJson(node)))
        .isEqualTo(expectedNode);
  }

  /**
   * Test swagger references are all defined within the JsonNode
   *
   * @param jsonNode The top of the swagger tree
   */
  public void checkReferences(final JsonNode jsonNode) {
    for (JsonNode node : jsonNode.findValues("$ref")) {
      Assertions.assertThat(node.asText())
          .withFailMessage("Did not start with '#/' " + node.asText())
          .startsWith("#/");
      final List<JsonNode> found = getNodesAtPath(jsonNode, node.asText().substring(2));
      Assertions.assertThat(found.isEmpty())
          .withFailMessage("Missing reference " + node.asText())
          .isFalse();
      Assertions.assertThat(found.size())
          .withFailMessage("Multiple schema objects satisfy reference " + node.asText())
          .isEqualTo(1);
    }
  }

  private List<JsonNode> getNodesAtPath(final JsonNode jsonNode, final String path) {
    JsonNode node;
    if (path.contains("/")) {
      node = jsonNode.path(path.substring(0, path.indexOf("/")));
      if (node.isMissingNode()) {
        return Collections.emptyList();
      }
      return getNodesAtPath(node, path.substring(path.indexOf("/") + 1));
    }
    return jsonNode.path(path).isMissingNode()
        ? Collections.emptyList()
        : jsonNode.findValues(path);
  }

  private String prettyJson(final JsonNode jsonNode) throws JsonProcessingException {
    return mapper.writeValueAsString(jsonNode);
  }

  private void checkStructureIsConsistent(
      final String namespace, final Path tempDir, final String elementName, final JsonNode node)
      throws IOException {
    final String filename = elementName.replaceAll("/", "_") + ".json";
    Files.write(tempDir.resolve(filename), prettyJson(node).getBytes(UTF_8));
    JsonNode expectedNode = null;
    try {
      expectedNode = loadResourceFile(namespace + "/" + filename);
    } catch (Exception e) {
      Fail.fail(
          String.format(
              "Expected to find %s with content %s, does it need to be added?",
              namespace + "/" + filename, prettyJson(node)));
    }
    // Text comparison order isn't guaranteed in object mapper output,
    // so the most reliable option is to have an object comparison
    // with a text diff that's pretty.
    assertThat(node)
        .describedAs(
            String.format("Structure of %s -> (%s)", elementName, namespace + "/" + filename))
        .withFailMessage(
            String.format("Expected: %s\nbut was: %s", prettyJson(expectedNode), prettyJson(node)))
        .isEqualTo(expectedNode);
  }

  private void checkAllExpectedFilesExist(final Path tempDir, final String schema)
      throws IOException {
    final InputStream resourcesAsStream =
        clazz.getClassLoader().getResourceAsStream(path + "/" + schema);
    assertThat(resourcesAsStream).isNotNull();
    final List<String> expectedFileListing = IOUtils.readLines(resourcesAsStream, UTF_8);

    for (String file : expectedFileListing) {
      if (!file.toLowerCase(Locale.ROOT).endsWith(".json")) {
        continue;
      }
      // Notes:
      //  * If a path has been deleted, just delete the reference file to make this test pass.
      //  * If this path shouldn't have been changed and the error appears, check changes.
      final String format =
          String.format(
              "Reference path %s exists but not in swagger - has it been removed?",
              schema + "/" + file);
      assertThat(tempDir.resolve(file).toFile()).withFailMessage(format).exists();
    }
  }

  private JsonNode loadResourceFile(final String resourceFile) throws IOException {
    final String resource = Resources.toString(Resources.getResource(clazz, resourceFile), UTF_8);
    return mapper.readTree(resource);
  }

  public static String serializeEndpointMetadata(final RestApiEndpoint endpoint) throws Exception {
    return JsonUtil.serialize(
        gen -> {
          gen.writeStartObject();
          endpoint.getMetadata().writeOpenApi(gen);
          gen.writeEndObject();
        });
  }
}
