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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.IOException;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;

/**
 * Defines the configuration for a database. The configuration used when a database is created is
 * written to a metadata.yaml file and reloaded to ensure we continue using compatible values for
 * the lifetime of that database.
 *
 * <p>To preserve backwards compatibility always ensure that the value assigned in field
 * declarations is compatible with existing databases. These values will be used if the field didn't
 * exist at the time the database was created, so typically should match the default.
 *
 * <p>If the value to use for new databases, differs from the original, set it in a factory function
 * e.g. {@link #v5Defaults()}.
 *
 * <p>Values that are safe to change for existing databases are marked with {@link
 * Access#WRITE_ONLY}. They will not be written to the metadata file but if present, the value will
 * be loaded providing a simple way to experiment with different values without it being fixed at
 * database creation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class V5DatabaseMetadata {

  @VisibleForTesting static final String HOT_DB_CONFIGURATION_KEY = "hotDbConfiguration";

  @JsonProperty(HOT_DB_CONFIGURATION_KEY)
  private KvStoreConfiguration hotDbConfiguration = new KvStoreConfiguration();

  @JsonProperty("archiveDbConfiguration")
  private KvStoreConfiguration archiveDbConfiguration = new KvStoreConfiguration();

  public static V5DatabaseMetadata v5Defaults() {
    final V5DatabaseMetadata metadata = new V5DatabaseMetadata();
    metadata.hotDbConfiguration = KvStoreConfiguration.v5HotDefaults();
    metadata.archiveDbConfiguration = KvStoreConfiguration.v5ArchiveDefaults();
    return metadata;
  }

  public KvStoreConfiguration getHotDbConfiguration() {
    return hotDbConfiguration;
  }

  public KvStoreConfiguration getArchiveDbConfiguration() {
    return archiveDbConfiguration;
  }

  public static V5DatabaseMetadata init(final File source, final V5DatabaseMetadata defaultValue)
      throws IOException {
    final ObjectMapper objectMapper =
        new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
    if (source.exists()) {
      return objectMapper.readerFor(V5DatabaseMetadata.class).readValue(source);
    } else {
      objectMapper.writerFor(V5DatabaseMetadata.class).writeValue(source, defaultValue);
      return defaultValue;
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("hotDbConfiguration", hotDbConfiguration)
        .add("archiveDbConfiguration", archiveDbConfiguration)
        .toString();
  }
}
