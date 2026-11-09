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

package tech.pegasys.teku.infrastructure.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;

/**
 * Helper class to read a list of entries from a file (either local file or remote url). The file
 * must use UTF-8 encoding and have one entry per line. It is possible to add comments to the file
 * using the '#' prefix.
 */
public class MultilineEntriesReader {

  public static final String COMMENT_PREFIX = "#";

  public static List<String> readEntries(final String resourceUrl) {
    try (final BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(loadResource(resourceUrl), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT_PREFIX))
          .toList();
    } catch (final Exception e) {
      throw new InvalidConfigurationException(
          String.format("Failed reading entries from resource %s", resourceUrl), e);
    }
  }

  private static InputStream loadResource(final String resourceUrl) throws IOException {
    return ResourceLoader.urlOrFile()
        .load(resourceUrl)
        .orElseThrow(
            () ->
                new InvalidConfigurationException(
                    String.format("Couldn't load resource %s", resourceUrl)));
  }
}
