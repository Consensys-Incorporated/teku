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

package tech.pegasys.teku.ethereum.executionclient.auth;

import io.jsonwebtoken.Jwts.SIG;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class JwtSecretKeyLoader {

  private static final Logger LOG = LogManager.getLogger();

  private static final String HMAC_SHA256_ALGORITHM_NAME = "HmacSHA256";
  public static final String JWT_SECRET_FILE_NAME = "ee-jwt-secret.hex";
  private final Optional<String> jwtSecretFile;
  private final Path beaconDataDirectory;

  public JwtSecretKeyLoader(final Optional<String> jwtSecretFile, final Path beaconDataDirectory) {
    this.jwtSecretFile = jwtSecretFile;
    this.beaconDataDirectory = beaconDataDirectory;
  }

  public SecretKeySpec getSecretKey() {
    return jwtSecretFile.map(this::loadSecretFromFile).orElseGet(this::generateNewSecret);
  }

  private SecretKeySpec generateNewSecret() {
    final Key key = SIG.HS256.key().build();
    final byte[] keyData = key.getEncoded();
    final SecretKeySpec wrappedKey = new SecretKeySpec(keyData, key.getAlgorithm());
    writeGeneratedKeyToFile(wrappedKey);
    return wrappedKey;
  }

  private void writeGeneratedKeyToFile(final Key key) {
    final Path generatedKeyFilePath = beaconDataDirectory.resolve(JWT_SECRET_FILE_NAME);
    try {
      if (!beaconDataDirectory.toFile().mkdirs() && !beaconDataDirectory.toFile().isDirectory()) {
        throw new IOException("Unable to create directory " + beaconDataDirectory);
      }
      Files.writeString(generatedKeyFilePath, Bytes.wrap(key.getEncoded()).toHexString());
      LOG.info(
          "New execution engine JWT secret generated in {}", generatedKeyFilePath.toAbsolutePath());
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Unable to write generated key to file: " + generatedKeyFilePath, e);
    }
  }

  private SecretKeySpec loadSecretFromFile(final String jwtSecretFile) {
    final Path filePath = Paths.get(jwtSecretFile);
    try {
      final Bytes bytesFromHex = Bytes.fromHexString(Files.readString(filePath).trim());
      LOG.info("JWT secret loaded from {}", filePath.toAbsolutePath());
      return new SecretKeySpec(bytesFromHex.toArray(), HMAC_SHA256_ALGORITHM_NAME);
    } catch (final FileNotFoundException | NoSuchFileException e) {
      throw new InvalidConfigurationException(
          "Could not find execution engine JWT secret file: " + filePath.toAbsolutePath(), e);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Could not read execution engine JWT secret file: " + filePath.toAbsolutePath(), e);
    }
  }
}
