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

import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

public class GeneratingFilePrivateKeySource implements PrivateKeySource {
  private final String fileName;

  public GeneratingFilePrivateKeySource(final String fileName) {
    this.fileName = fileName;
  }

  @Override
  public Bytes getPrivateKeyBytes() {
    try {
      File file = new File(fileName);
      if (!file.createNewFile()) {
        return getPrivateKeyBytesFromTextFile();
      }
      final PrivKey privKey = KeyKt.generateKeyPair(KeyType.SECP256K1).component1();
      final Bytes privateKeyBytes = Bytes.wrap(KeyKt.marshalPrivateKey(privKey));
      Files.writeString(file.toPath(), privateKeyBytes.toHexString());
      STATUS_LOG.usingGeneratedP2pPrivateKey(fileName, true);
      return privateKeyBytes;

    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Not able to create or retrieve p2p private key file - " + fileName);
    }
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
      throw new RuntimeException("p2p private key file not found - " + fileName);
    }
  }

  private Bytes getPrivateKeyBytesFromBytesFile() {
    try {
      final Bytes privateKeyBytes = Bytes.wrap(Files.readAllBytes(Paths.get(fileName)));
      STATUS_LOG.usingGeneratedP2pPrivateKey(fileName, false);
      return privateKeyBytes;
    } catch (IOException e) {
      throw new RuntimeException("p2p private key file not found - " + fileName);
    }
  }

  public String getFileName() {
    return fileName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GeneratingFilePrivateKeySource that = (GeneratingFilePrivateKeySource) o;
    return Objects.equals(fileName, that.fileName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileName);
  }
}
