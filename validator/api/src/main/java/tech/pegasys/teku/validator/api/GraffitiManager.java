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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

public class GraffitiManager {
  private static final Logger LOG = LogManager.getLogger();
  static final String GRAFFITI_DIR = "graffiti";
  private final Path directory;

  public GraffitiManager(final DataDirLayout dataDirLayout) {
    this(dataDirLayout.getValidatorDataDirectory().resolve(GRAFFITI_DIR));
  }

  public GraffitiManager(final Path directory) {
    this.directory = directory;
    if (!directory.toFile().exists() && !directory.toFile().mkdirs()) {
      throw new IllegalStateException("Unable to create directory for graffiti management.");
    }
  }

  public synchronized void setGraffiti(final BLSPublicKey publicKey, final String graffiti)
      throws GraffitiManagementException {
    final String strippedGraffiti = graffiti.strip();
    final int graffitiSize = strippedGraffiti.getBytes(StandardCharsets.UTF_8).length;
    if (graffitiSize > 32) {
      throw new IllegalArgumentException(
          String.format(
              "'%s' converts to %s bytes. Input must be 32 bytes or less.",
              strippedGraffiti, graffitiSize));
    }

    try {
      final Path file = directory.resolve(resolveFileName(publicKey));
      Files.writeString(file, strippedGraffiti);
    } catch (IOException e) {
      throw new GraffitiManagementException(
          "Unable to update graffiti for validator " + publicKey, e);
    }
  }

  public synchronized void deleteGraffiti(final BLSPublicKey publicKey)
      throws GraffitiManagementException {
    final Path file = directory.resolve(resolveFileName(publicKey));
    if (!file.toFile().exists()) {
      return;
    }

    try {
      Files.delete(file);
    } catch (IOException e) {
      throw new GraffitiManagementException(
          "Unable to delete graffiti for validator " + publicKey, e);
    }
  }

  public synchronized Optional<Bytes32> getGraffiti(final BLSPublicKey publicKey)
      throws GraffitiManagementException {
    final Path filePath = directory.resolve(resolveFileName(publicKey));
    if (!filePath.toFile().exists()) {
      return Optional.empty();
    }

    try {
      return Optional.of(GraffitiParser.loadFromFile(filePath));
    } catch (GraffitiLoaderException | IllegalArgumentException e) {
      LOG.error("Loading graffiti from graffiti storage failed.", e);
      throw new GraffitiManagementException(
          "Unable to retrieve stored graffiti for validator " + publicKey, e);
    }
  }

  private String resolveFileName(final BLSPublicKey publicKey) {
    return publicKey.toSSZBytes().toUnprefixedHexString() + ".txt";
  }
}
