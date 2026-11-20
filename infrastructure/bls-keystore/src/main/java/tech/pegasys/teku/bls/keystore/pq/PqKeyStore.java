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

package tech.pegasys.teku.bls.keystore.pq;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreBytesModule;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.Crypto;
import tech.pegasys.teku.bls.keystore.model.KdfParam;

/**
 * Encrypts, decrypts, reads and writes {@link PqKeyStoreData PQ validator keystores}. Encryption is
 * EIP-2335's, through {@link KeyStore}.
 */
public class PqKeyStore {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new KeyStoreBytesModule())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private PqKeyStore() {}

  /**
   * Encrypts {@code secrets} with {@code password}. The public values are stored as given; they are
   * not checked against the seeds.
   */
  public static PqKeyStoreData encrypt(
      final PqKeyStoreSecrets secrets,
      final Bytes32 xmssPubkey,
      final Bytes32 vrfRoot,
      final Optional<Bytes> blsPubkey,
      final String password,
      final KdfParam kdfParam,
      final Cipher cipher) {
    checkNotNull(secrets, "Secrets cannot be null");
    checkNotNull(xmssPubkey, "XMSS public key cannot be null");
    checkNotNull(vrfRoot, "VRF root cannot be null");
    final Crypto crypto = KeyStore.encryptSecret(secrets.toBytes(), password, kdfParam, cipher);
    final PqKeyStoreData data = new PqKeyStoreData(xmssPubkey, vrfRoot, blsPubkey, crypto);
    data.validate();
    return data;
  }

  /** Decrypts the seeds. Throws {@link KeyStoreValidationException} if the password is wrong. */
  public static PqKeyStoreSecrets decrypt(final String password, final PqKeyStoreData data) {
    checkNotNull(data, "PQ keystore cannot be null");
    final Bytes secret = KeyStore.decrypt(password, data.getCrypto());
    if (secret.size() != PqKeyStoreData.SECRET_SIZE) {
      throw new KeyStoreValidationException(
          String.format(
              "Expected %s bytes of PQ keystore secrets but got %s",
              PqKeyStoreData.SECRET_SIZE, secret.size()));
    }
    return PqKeyStoreSecrets.fromBytes(secret);
  }

  public static boolean validatePassword(final String password, final PqKeyStoreData data) {
    checkNotNull(data, "PQ keystore cannot be null");
    return KeyStore.validatePassword(password, data.getCrypto());
  }

  public static PqKeyStoreData loadFromFile(final Path file) throws KeyStoreValidationException {
    checkNotNull(file, "PQ keystore path cannot be null");
    final String json;
    try {
      json = Files.readString(file, UTF_8);
    } catch (final NoSuchFileException e) {
      throw new KeyStoreValidationException("PQ keystore file not found: " + file, e);
    } catch (final IOException e) {
      throw new KeyStoreValidationException(
          "Unexpected IO error while reading PQ keystore " + file + ": " + e.getMessage(), e);
    }
    return loadFromString(json);
  }

  public static PqKeyStoreData loadFromString(final String json)
      throws KeyStoreValidationException {
    final PqKeyStoreData data;
    try {
      data = OBJECT_MAPPER.readValue(json, PqKeyStoreData.class);
    } catch (final JsonProcessingException e) {
      if (e.getCause() instanceof KeyStoreValidationException keyStoreValidationException) {
        throw keyStoreValidationException;
      }
      throw new KeyStoreValidationException("Invalid PQ keystore: " + e.getMessage(), e);
    }
    data.validate();
    return data;
  }

  /**
   * Writes {@code data} to a new file. Fails rather than replace an existing file, since the
   * keystore it would replace may hold seeds that are already registered.
   */
  public static void saveToNewFile(final Path file, final PqKeyStoreData data) throws IOException {
    checkNotNull(file, "PQ keystore path cannot be null");
    checkNotNull(data, "PQ keystore cannot be null");
    try {
      Files.writeString(file, toJson(data), UTF_8, StandardOpenOption.CREATE_NEW);
    } catch (final FileAlreadyExistsException e) {
      throw new FileAlreadyExistsException(
          file.toString(), null, "Refusing to replace an existing PQ keystore");
    }
  }

  public static String toJson(final PqKeyStoreData data) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    } catch (final JsonProcessingException e) {
      throw new KeyStoreValidationException(
          "Error converting PQ keystore to JSON: " + e.getMessage(), e);
    }
  }
}
