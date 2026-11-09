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

package tech.pegasys.teku.bls.keystore;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.apache.tuweni.bytes.Bytes.concatenate;

import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.teku.bls.keystore.model.Checksum;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.Crypto;
import tech.pegasys.teku.bls.keystore.model.Kdf;
import tech.pegasys.teku.bls.keystore.model.KdfParam;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.crypto.Hash;

/**
 * BLS Key Store implementation EIP-2335
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md">EIP-2335</a>
 */
public class KeyStore {
  private static final BouncyCastleProvider BC = new BouncyCastleProvider();

  /**
   * Encrypt the given BLS12-381 key with specified password.
   *
   * @param blsPrivateKey BLS12-381 private key in Bytes to encrypt. It is not validated to be a
   *     valid BLS12-381 key.
   * @param blsPublicKey BLS12-381 public key in Bytes. It is not validated and stored as it is.
   * @param password The password to use for encryption
   * @param path Path as defined in EIP-2334. Can be empty String.
   * @param kdfParam crypto function such as scrypt or PBKDF2 and related parameters such as dklen,
   *     salt etc.
   * @param cipher cipher function and iv parameter to use.
   * @return The constructed KeyStore with encrypted BLS Private Key as cipher.message and other
   *     details as defined by the EIP-2335 standard.
   */
  public static KeyStoreData encrypt(
      final Bytes blsPrivateKey,
      final Bytes blsPublicKey,
      final String password,
      final String path,
      final KdfParam kdfParam,
      final Cipher cipher) {

    checkNotNull(blsPrivateKey, "PrivateKey cannot be null");
    checkNotNull(blsPublicKey, "PublicKey cannot be null");
    checkNotNull(password, "Password cannot be null");
    checkNotNull(path, "Path cannot be null");
    checkNotNull(kdfParam, "KDFParam cannot be null");
    checkNotNull(cipher, "Cipher cannot be null");

    kdfParam.validate();
    cipher.validate();

    final Crypto crypto = encryptUsingCipherFunction(blsPrivateKey, password, kdfParam, cipher);
    return new KeyStoreData(crypto, blsPublicKey, path);
  }

  private static Crypto encryptUsingCipherFunction(
      final Bytes secret, final String password, final KdfParam kdfParam, final Cipher cipher) {
    final Bytes decryptionKey = kdfParam.generateDecryptionKey(password);
    final Bytes cipherMessage =
        applyCipherFunction(decryptionKey, cipher, true, secret.toArrayUnsafe());
    final Bytes checksumMessage = calculateSHA256Checksum(decryptionKey, cipherMessage);
    final Checksum checksum = new Checksum(checksumMessage);
    final Cipher encryptedCipher =
        new Cipher(cipher.getCipherFunction(), cipher.getCipherParam(), cipherMessage);
    final Kdf kdf = new Kdf(kdfParam);
    return new Crypto(kdf, checksum, encryptedCipher);
  }

  /**
   * Validates password without decrypting the key as defined in specifications
   *
   * @param password The password to validate
   * @param keyStoreData The Key Store against which password to validate
   * @return true if password is valid, false otherwise.
   */
  public static boolean validatePassword(final String password, final KeyStoreData keyStoreData) {
    checkNotNull(password, "Password cannot be null");
    checkNotNull(keyStoreData, "KeyStoreData cannot be null");

    final Bytes decryptionKey =
        keyStoreData.getCrypto().getKdf().getParam().generateDecryptionKey(password);
    return validateChecksum(decryptionKey, keyStoreData);
  }

  /**
   * Decrypts BLS private key from the given KeyStore
   *
   * @param password The password to use for decryption
   * @param keyStoreData The given Key Store
   * @return decrypted BLS private key in Bytes
   */
  public static Bytes decrypt(final String password, final KeyStoreData keyStoreData) {
    checkNotNull(password, "Password cannot be null");
    checkNotNull(keyStoreData, "KeyStoreData cannot be null");

    final Bytes decryptionKey =
        keyStoreData.getCrypto().getKdf().getParam().generateDecryptionKey(password);

    if (!validateChecksum(decryptionKey, keyStoreData)) {
      throw new KeyStoreValidationException(
          "Failed to decrypt KeyStore, checksum validation failed.");
    }

    final Cipher cipher = keyStoreData.getCrypto().getCipher();
    final byte[] encryptedMessage = cipher.getMessage().toArrayUnsafe();
    return applyCipherFunction(decryptionKey, cipher, false, encryptedMessage);
  }

  private static boolean validateChecksum(
      final Bytes decryptionKey, final KeyStoreData keyStoreData) {
    final Bytes checksum =
        calculateSHA256Checksum(decryptionKey, keyStoreData.getCrypto().getCipher().getMessage());
    return Objects.equals(checksum, keyStoreData.getCrypto().getChecksum().getMessage());
  }

  private static Bytes calculateSHA256Checksum(
      final Bytes decryptionKey, final Bytes cipherMessage) {
    // aes-128-ctr needs first 16 bytes for its key. The 2nd 16 bytes are used to create checksum
    final Bytes dkSliceSecondHalf = decryptionKey.slice(16, 16);
    return Hash.sha256(concatenate(dkSliceSecondHalf, cipherMessage));
  }

  private static Bytes applyCipherFunction(
      final Bytes decryptionKey,
      final Cipher cipher,
      final boolean encryptMode,
      final byte[] inputMessage) {
    // aes-128-ctr needs first 16 bytes for its key. The 2nd 16 bytes are used to create checksum
    final SecretKeySpec secretKey =
        new SecretKeySpec(decryptionKey.slice(0, 16).toArrayUnsafe(), "AES");
    final IvParameterSpec ivParameterSpec =
        new IvParameterSpec(cipher.getCipherParam().getIv().toArrayUnsafe());
    try {
      final javax.crypto.Cipher jceCipher =
          javax.crypto.Cipher.getInstance("AES/CTR/NoPadding", BC);
      jceCipher.init(encryptMode ? ENCRYPT_MODE : DECRYPT_MODE, secretKey, ivParameterSpec);
      return Bytes.wrap(jceCipher.doFinal(inputMessage));
    } catch (final GeneralSecurityException e) {
      throw new KeyStoreValidationException("Unexpected error while applying cipher function", e);
    }
  }
}
