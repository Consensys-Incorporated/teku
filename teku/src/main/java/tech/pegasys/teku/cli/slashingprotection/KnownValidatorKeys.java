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

package tech.pegasys.teku.cli.slashingprotection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.options.ValidatorKeysOptions;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;

/**
 * The validator public keys that a validator client using this configuration would load, found
 * without decrypting any keystore or contacting an external signer.
 *
 * @param publicKeys the keys found, in the order they were found, without duplicates
 * @param uncheckedSources descriptions of keys that could not be read, or were not fetched
 * @param hasKeySources false if no key source was configured or found, so no key was checked
 */
public record KnownValidatorKeys(
    List<BLSPublicKey> publicKeys, List<String> uncheckedSources, boolean hasKeySources) {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Finds the validator keys from, in order: keys imported through the key-manager API, keystores
   * given by --validator-keys, and public keys given by --validators-external-signer-public-keys.
   *
   * <p>Keystores are not decrypted, so no password is needed. External signer public keys given as
   * a URL, or as {@value PublicKeyLoader#EXTERNAL_SIGNER_SOURCE_ID}, are not fetched and are
   * reported as unchecked.
   */
  static KnownValidatorKeys load(
      final SubCommandLogger log,
      final DataDirLayout dataDirLayout,
      final List<String> validatorKeys,
      final List<String> externalSignerPublicKeySources) {
    final Set<BLSPublicKey> publicKeys = new LinkedHashSet<>();
    final List<String> uncheckedSources = new ArrayList<>();
    boolean hasKeySources = false;

    final Path localKeystorePath =
        ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout);
    if (Files.isDirectory(localKeystorePath)) {
      hasKeySources = true;
      final List<BLSPublicKey> keys = readKeyManagerLocalKeys(localKeystorePath, uncheckedSources);
      displayFound(log, keys.size(), "key-manager keystores in " + localKeystorePath);
      publicKeys.addAll(keys);
    }

    final Path remoteKeyPath = ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout);
    if (Files.isDirectory(remoteKeyPath)) {
      hasKeySources = true;
      final List<BLSPublicKey> keys = readKeyManagerRemoteKeys(remoteKeyPath, uncheckedSources);
      displayFound(log, keys.size(), "key-manager remote keys in " + remoteKeyPath);
      publicKeys.addAll(keys);
    }

    if (!validatorKeys.isEmpty()) {
      hasKeySources = true;
      final List<BLSPublicKey> keys = readValidatorKeys(validatorKeys, uncheckedSources);
      displayFound(log, keys.size(), "keystores given by --validator-keys");
      publicKeys.addAll(keys);
    }

    if (!externalSignerPublicKeySources.isEmpty()) {
      hasKeySources = true;
      final List<BLSPublicKey> keys =
          readExternalSignerPublicKeys(externalSignerPublicKeySources, uncheckedSources);
      displayFound(log, keys.size(), "keys given by --validators-external-signer-public-keys");
      publicKeys.addAll(keys);
    }

    return new KnownValidatorKeys(
        List.copyOf(publicKeys), List.copyOf(uncheckedSources), hasKeySources);
  }

  /**
   * Finds the keys a validator client would load with these options, and displays what was found.
   */
  public static KnownValidatorKeys load(
      final SubCommandLogger log,
      final ValidatorClientDataOptions dataOptions,
      final ValidatorKeysOptions validatorKeysOptions) {
    final KnownValidatorKeys keys =
        load(
            log,
            DataDirLayout.createFrom(dataOptions.getDataConfig()),
            validatorKeysOptions.getValidatorKeys(),
            validatorKeysOptions.getValidatorExternalSignerPublicKeySources());
    keys.displayUncheckedSources(log);
    return keys;
  }

  /**
   * A missing slashing protection directory is only allowed when it will be created to hold records
   * for these keys, which needs update all and not check only.
   *
   * @return true, after telling the user, if the missing directory will be created
   */
  public boolean willCreateSlashingProtectionPath(
      final SubCommandLogger log,
      final Path slashProtectionPath,
      final boolean updateAllEnabled,
      final boolean checkOnlyEnabled) {
    if (!updateAllEnabled
        || checkOnlyEnabled
        || publicKeys.isEmpty()
        || Files.exists(slashProtectionPath)) {
      return false;
    }
    log.display(
        slashProtectionPath + " does not exist, and will be created for the missing records.");
    return true;
  }

  /** Explains which keys were not checked for a missing slashing protection record. */
  public void displayUncheckedSources(final SubCommandLogger log) {
    if (!hasKeySources) {
      log.display(
          "No validator keys were found in the key-manager directories, --validator-keys or"
              + " --validators-external-signer-public-keys, so keys without a slashing protection"
              + " record were not checked.");
      return;
    }
    if (uncheckedSources.isEmpty()) {
      return;
    }
    log.display("These keys were not checked for a missing slashing protection record:");
    uncheckedSources.forEach(source -> log.display(" - " + source));
  }

  private static void displayFound(
      final SubCommandLogger log, final int count, final String description) {
    log.display(
        "Found " + count + " validator " + (count == 1 ? "key" : "keys") + " from " + description);
  }

  static List<BLSPublicKey> readKeyManagerLocalKeys(
      final Path localKeystorePath, final List<String> uncheckedSources) {
    final List<BLSPublicKey> keys = new ArrayList<>();
    for (final Path keystore : listJsonFiles(localKeystorePath, uncheckedSources)) {
      readKeystorePublicKey(keystore, uncheckedSources, keys);
    }
    return keys;
  }

  static List<BLSPublicKey> readKeyManagerRemoteKeys(
      final Path remoteKeyPath, final List<String> uncheckedSources) {
    final List<BLSPublicKey> keys = new ArrayList<>();
    for (final Path file : listJsonFiles(remoteKeyPath, uncheckedSources)) {
      try {
        final JsonNode pubkey = OBJECT_MAPPER.readTree(file.toFile()).get("pubkey");
        if (pubkey == null || !pubkey.isTextual()) {
          throw new IllegalArgumentException("no pubkey field");
        }
        keys.add(BLSPublicKey.fromHexString(pubkey.asText()));
      } catch (final Exception e) {
        uncheckedSources.add(file + ": could not read the public key (" + e.getMessage() + ")");
      }
    }
    return keys;
  }

  static List<BLSPublicKey> readValidatorKeys(
      final List<String> validatorKeys, final List<String> uncheckedSources) {
    final List<Pair<Path, Path>> keystorePasswordPairs;
    try {
      keystorePasswordPairs = new KeyStoreFilesLocator(validatorKeys, File.pathSeparator).parse();
    } catch (final Exception e) {
      uncheckedSources.add("--validator-keys: " + e.getMessage());
      return List.of();
    }
    final List<BLSPublicKey> keys = new ArrayList<>();
    keystorePasswordPairs.stream()
        .map(Pair::getKey)
        .sorted()
        .forEach(keystore -> readKeystorePublicKey(keystore, uncheckedSources, keys));
    return keys;
  }

  static List<BLSPublicKey> readExternalSignerPublicKeys(
      final List<String> sources, final List<String> uncheckedSources) {
    // only used to parse literal keys, which never needs the http client or signer url
    final PublicKeyLoader publicKeyLoader =
        new PublicKeyLoader(
            () -> {
              throw new IllegalStateException("Public keys are not fetched by repair");
            },
            null);
    final List<BLSPublicKey> keys = new ArrayList<>();
    for (final String source : sources) {
      if (source.equalsIgnoreCase(PublicKeyLoader.EXTERNAL_SIGNER_SOURCE_ID)) {
        uncheckedSources.add(
            "--validators-external-signer-public-keys: keys held by the external signer were not"
                + " fetched");
      } else if (source.contains(":")) {
        uncheckedSources.add(
            "--validators-external-signer-public-keys: keys at " + source + " were not fetched");
      } else {
        try {
          keys.addAll(publicKeyLoader.getPublicKeys(List.of(source)));
        } catch (final Exception e) {
          uncheckedSources.add(
              "--validators-external-signer-public-keys: " + source + " is not a valid public key");
        }
      }
    }
    return keys;
  }

  private static void readKeystorePublicKey(
      final Path keystore, final List<String> uncheckedSources, final List<BLSPublicKey> keys) {
    try {
      keys.add(
          BLSPublicKey.fromBytesCompressed(
              Bytes48.wrap(KeyStoreLoader.loadFromFile(keystore.toUri()).getPubkey())));
    } catch (final Exception e) {
      uncheckedSources.add(keystore + ": could not read the public key (" + e.getMessage() + ")");
    }
  }

  private static List<Path> listJsonFiles(
      final Path directory, final List<String> uncheckedSources) {
    try (Stream<Path> walk = Files.walk(directory, FileVisitOption.FOLLOW_LINKS)) {
      return walk.filter(Files::isRegularFile)
          .filter(path -> !path.toFile().isHidden())
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
          .sorted()
          .toList();
    } catch (final IOException | RuntimeException e) {
      uncheckedSources.add(directory + ": could not list files (" + e.getMessage() + ")");
      return List.of();
    }
  }
}
