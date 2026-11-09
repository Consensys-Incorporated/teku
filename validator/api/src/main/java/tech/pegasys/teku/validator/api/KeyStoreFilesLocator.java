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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class KeyStoreFilesLocator {
  private static final Logger LOG = LogManager.getLogger();
  private final List<String> colonSeparatedPairs;
  private final String pathSeparator;

  /**
   * Construct a KeyStoreFilesLocator with the specified parameters.
   *
   * @param colonSeparatedPairs A list of keyfile and password files. Each pair should be one of
   *     &lt;KEY_DIR&gt;:&lt;PASS_DIR&gt; | &lt;KEY_DIR&gt;:&lt;PASS_FILE&gt; |
   *     &lt;KEY_FILE&gt;:&lt;PASS_FILE&gt;
   * @param pathSeparator The path separator used in specifying the colonSeparatedPairs
   */
  public KeyStoreFilesLocator(final List<String> colonSeparatedPairs, final String pathSeparator) {
    this.colonSeparatedPairs = colonSeparatedPairs;
    this.pathSeparator = pathSeparator;
  }

  public List<Pair<Path, Path>> parse() {
    Map<Path, Path> pathMap = new HashMap<>();
    for (final String currentEntry : colonSeparatedPairs) {
      if (!currentEntry.contains(pathSeparator)) {
        throw new InvalidConfigurationException(
            "validatorKeys entry ("
                + currentEntry
                + ") did not contain key and password separated by '"
                + pathSeparator
                + "' as expected.");
      }

      final List<String> entry = Splitter.on(pathSeparator).limit(2).splitToList(currentEntry);
      parseEntry(entry.get(0), entry.get(1), pathMap);
    }
    return getFilePairs(pathMap);
  }

  private void parseEntry(
      final String keyFileName, final String passwordFileName, final Map<Path, Path> pathMap) {
    final File keyFile = new File(keyFileName);
    final File passwordFile = new File(passwordFileName);

    if (!keyFile.exists()) {
      throw new InvalidConfigurationException(
          String.format("Invalid configuration. Could not find the key file (%s).", keyFileName));
    }
    if (isDepositDataFile(keyFile)) {
      return;
    }
    if (!passwordFile.exists()) {
      throw new InvalidConfigurationException(
          String.format(
              "Invalid configuration. Could not find the password file (%s).", passwordFileName));
    }
    if (keyFile.isFile() && passwordFile.isDirectory()) {
      throw new InvalidConfigurationException(
          String.format(
              "Invalid configuration. Password directory (%s) makes no sense with individual "
                  + "key file (%s).",
              passwordFileName, keyFileName));
    }
    if (keyFile.isFile()) {
      pathMap.putIfAbsent(keyFile.toPath(), passwordFile.toPath());
    } else {
      parseDirectory(keyFile, passwordFile, pathMap);
    }
  }

  private boolean isDepositDataFile(final File keyFile) {
    String keyFileName = keyFile.toPath().getFileName().toString();
    if (keyFileName.startsWith("deposit_data-") && hasDepositDataFileContents(keyFile)) {
      LOG.debug("Ignoring deposit data file: " + keyFileName);
      return true;
    }
    return false;
  }

  private boolean hasDepositDataFileContents(final File keyFile) {
    try {
      byte[] fileData = Files.readAllBytes(keyFile.toPath());
      ObjectMapper objectMapper = new ObjectMapper();
      List<Map<String, String>> listData =
          objectMapper.readValue(fileData, new TypeReference<>() {});

      return !listData.isEmpty()
          && listData.get(0).containsKey("deposit_message_root")
          && listData.get(0).containsKey("deposit_data_root");
    } catch (final Exception e) {
      LOG.debug("Unable to determine if {} is a deposit_data file", keyFile, e);
      return false;
    }
  }

  private void parseDirectory(
      final File keyDirectory, final File passwordFileOrDirectory, final Map<Path, Path> pathMap) {
    try (Stream<Path> walk = Files.walk(keyDirectory.toPath(), FileVisitOption.FOLLOW_LINKS)) {
      walk.filter(Files::isRegularFile)
          .filter(
              (path) ->
                  !path.toFile().isHidden() && path.getFileName().toString().endsWith(".json"))
          .forEach(
              path -> {
                final Path relativeDirectoryPath =
                    keyDirectory.toPath().relativize(path.getParent());
                if (isDepositDataFile(path.toFile())) {
                  return;
                }
                final String keystoreName = path.getFileName().toString();
                final File passwordFile;
                if (passwordFileOrDirectory.isDirectory()) {
                  // User specified a password directory, search for the file matching this
                  // keystore.
                  passwordFile =
                      passwordFileOrDirectory
                          .toPath()
                          .resolve(relativeDirectoryPath)
                          .resolve(
                              keystoreName.substring(0, keystoreName.length() - ".json".length())
                                  + ".txt")
                          .toFile();
                  if (!passwordFile.isFile()) {
                    throw new InvalidConfigurationException(
                        String.format(
                            "Invalid configuration. Password file for keystore %s either doesn't "
                                + "exist or isn't readable. Expected to find it at %s",
                            path.toAbsolutePath(), passwordFile.getAbsolutePath()));
                  }
                } else if (passwordFileOrDirectory.isFile()) {
                  // User specified a single password file to use for all keystores.
                  passwordFile = passwordFileOrDirectory;
                } else {
                  throw new InvalidConfigurationException(
                      String.format(
                          "Invalid configuration. Password file (%s) is neither a directory nor a file",
                          passwordFileOrDirectory.getAbsolutePath()));
                }
                pathMap.putIfAbsent(path, passwordFile.toPath());
              });
    } catch (IOException e) {
      LOG.fatal("Failed to load keys from keystore", e);
    }
  }

  private List<Pair<Path, Path>> getFilePairs(final Map<Path, Path> pathMap) {
    return pathMap.entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
        .toList();
  }
}
