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

package tech.pegasys.teku.validator.api;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A type to hold the current graffiti value. Graffiti can be loaded from 2 places:
 *
 * <ul>
 *   <li>The default value supplied at initialisation
 *   <li>The graffiti file which will be regularly checked
 * </ul>
 *
 * In the case that the file cannot be read, the default graffiti value will be used.
 */
public class FileBackedGraffitiProvider implements GraffitiProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final Optional<Bytes32> defaultGraffiti;
  private final Optional<Path> graffitiFile;

  public FileBackedGraffitiProvider() {
    this(Optional.empty(), Optional.empty());
  }

  public FileBackedGraffitiProvider(
      final Optional<Bytes32> defaultGraffiti, final Optional<Path> graffitiFile) {
    this.defaultGraffiti = defaultGraffiti;
    this.graffitiFile = graffitiFile;
  }

  @Override
  public Optional<Bytes32> get() {
    return graffitiFile.flatMap(this::loadGraffitiFromFile).or(() -> defaultGraffiti);
  }

  private Optional<Bytes32> loadGraffitiFromFile(final Path path) {
    try {
      return Optional.of(GraffitiParser.loadFromFile(path));
    } catch (final Exception e) {
      LOG.error("Loading graffiti from file " + graffitiFile + " failed.", e);
      return Optional.empty();
    }
  }
}
