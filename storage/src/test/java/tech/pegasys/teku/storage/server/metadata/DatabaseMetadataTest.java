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

package tech.pegasys.teku.storage.server.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.storage.server.metadata.V5DatabaseMetadata.HOT_DB_CONFIGURATION_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;

class DatabaseMetadataTest {

  @Test
  void shouldCreateMetadataFile(@TempDir final File tempDir) throws Exception {
    final File metadataFile = new File(tempDir, "metadata.yml");
    assertThat(metadataFile).doesNotExist();
    final V5DatabaseMetadata expectedMetadata = V5DatabaseMetadata.v5Defaults();
    final V5DatabaseMetadata loadedMetadata =
        V5DatabaseMetadata.init(metadataFile, expectedMetadata);

    assertMetadataEquals(loadedMetadata, expectedMetadata);
    assertThat(metadataFile).exists();

    // And should reload those settings now that the file exists
    final V5DatabaseMetadata reloadedData =
        V5DatabaseMetadata.init(metadataFile, new V5DatabaseMetadata());
    assertMetadataEquals(reloadedData, expectedMetadata);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldNotStoreValuesThatAreSafeToChange(@TempDir final File tempDir) throws Exception {
    final File metadataFile = new File(tempDir, "metadata.yml");
    final V5DatabaseMetadata expectedMetadata = V5DatabaseMetadata.v5Defaults();
    V5DatabaseMetadata.init(metadataFile, expectedMetadata);
    final Map<String, Object> metadata = loadMetaData(metadataFile);
    final Map<String, Object> hotConfig =
        (Map<String, Object>) metadata.get(HOT_DB_CONFIGURATION_KEY);
    assertThat(hotConfig).doesNotContainKeys("maxOpenFiles", "databaseDir");
  }

  @Test
  void shouldUseSafeToChangeValuesFromFileWhenTheyExist(@TempDir final File tempDir)
      throws Exception {
    final File metadataFile = new File(tempDir, "metadata.yml");
    writeMetaData(
        ImmutableMap.of(HOT_DB_CONFIGURATION_KEY, ImmutableMap.of("maxOpenFiles", 1234)),
        metadataFile);

    final V5DatabaseMetadata result =
        V5DatabaseMetadata.init(metadataFile, V5DatabaseMetadata.v5Defaults());
    assertThat(result.getHotDbConfiguration().getMaxOpenFiles()).isEqualTo(1234);
  }

  @Test
  void shouldPopulateValuesForNewFields(@TempDir final File tempDir) throws Exception {
    final File metadataFile = new File(tempDir, "metadata.yml");
    writeMetaData(ImmutableMap.of(HOT_DB_CONFIGURATION_KEY, Collections.emptyMap()), metadataFile);
    final KvStoreConfiguration defaultConfiguration = new KvStoreConfiguration();

    final V5DatabaseMetadata result =
        V5DatabaseMetadata.init(metadataFile, V5DatabaseMetadata.v5Defaults());
    assertThat(result.getHotDbConfiguration().getMaxOpenFiles())
        .isEqualTo(defaultConfiguration.getMaxOpenFiles());
    assertThat(result.getArchiveDbConfiguration()).isNotNull();
    assertThat(result.getArchiveDbConfiguration())
        .usingRecursiveComparison()
        .isEqualTo(defaultConfiguration);
  }

  @Test
  void shouldCreateV6SingleMetadataFile(@TempDir final File tempDir) throws Exception {
    final File metadataFile = new File(tempDir, "metadata.yml");
    assertThat(metadataFile).doesNotExist();
    final V6DatabaseMetadata expectedMetadata = V6DatabaseMetadata.singleDBDefault();
    final V6DatabaseMetadata loadedMetadata =
        V6DatabaseMetadata.init(metadataFile, expectedMetadata);

    assertThat(loadedMetadata).usingRecursiveComparison().isEqualTo(expectedMetadata);
    assertThat(metadataFile).exists();

    // And should reload those settings now that the file exists
    final V6DatabaseMetadata reloadedData =
        V6DatabaseMetadata.init(metadataFile, new V6DatabaseMetadata());
    assertThat(reloadedData).usingRecursiveComparison().isEqualTo(expectedMetadata);
  }

  private Map<String, Object> loadMetaData(final File metadataFile) throws IOException {
    return new ObjectMapper(new YAMLFactory())
        .readValue(
            metadataFile,
            TypeFactory.defaultInstance().constructMapType(Map.class, String.class, Object.class));
  }

  private void writeMetaData(final Map<String, Object> metadata, final File metadataFile)
      throws IOException {
    new ObjectMapper(new YAMLFactory()).writeValue(metadataFile, metadata);
  }

  private void assertMetadataEquals(
      final V5DatabaseMetadata actual, final V5DatabaseMetadata expected) {
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
}
