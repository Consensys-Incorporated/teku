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

package tech.pegasys.teku.networking.p2p.network.config;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public record TypedFilePrivateKeySource(String fileName, Type type) implements PrivateKeySource {

  @Override
  public Bytes getPrivateKeyBytes() {
    File file = new File(fileName);
    if (file.exists()) {
      return getPrivateKeyBytesFromTextFile();
    }
    throw new InvalidConfigurationException(
        String.format("Private key file %s does not exist.", fileName));
  }

  private Bytes getPrivateKeyBytesFromTextFile() {
    try {
      final Bytes privateKeyBytes =
          Bytes.fromHexString(Files.readString(Paths.get(fileName)).trim());
      STATUS_LOG.usingGeneratedP2pPrivateKey(fileName, false);
      return privateKeyBytes;
    } catch (MalformedInputException e) {
      return getPrivateKeyBytesFromBytesFile();
    } catch (IOException e) {
      throw new RuntimeException(
          String.format(
              "Private key file %s could not be read: %s", fileName, e.getClass().getSimpleName()));
    }
  }

  private Bytes getPrivateKeyBytesFromBytesFile() {
    try {
      final Bytes privateKeyBytes = Bytes.wrap(Files.readAllBytes(Paths.get(fileName)));
      STATUS_LOG.usingGeneratedP2pPrivateKey(fileName, false);
      return privateKeyBytes;
    } catch (IOException e) {
      throw new RuntimeException(
          String.format(
              "Private key file %s could not be read: %s", fileName, e.getClass().getSimpleName()));
    }
  }

  @Override
  public Optional<Type> getType() {
    return Optional.of(type);
  }
}
