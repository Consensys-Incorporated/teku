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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;

class EncryptedKeystoreWriterTest {
  private static final BLSSecretKey VALIDATOR_1_SECRET_KEY =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x2CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460"));
  private static final BLSSecretKey WITHDRAWAL_1_SECRET_KEY =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x6EA631AA885EC84AFA60BBD7887B5DBC91F594DEA29334E99576B51FAAD0E453"));
  private static final BLSSecretKey VALIDATOR_2_SECRET_KEY =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x147599AA450AADF69988F20FF1ADB3A3BF31BF9CDC77CF492FF95667708D8E79"));
  private static final BLSSecretKey WITHDRAWAL_2_SECRET_KEY =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x0610B84CD68FB0FAB2F04A2A05EE01CD5F7374EB8EA93E26DB9C61DD2704B5BD"));
  private static final String VALIDATOR_1_PUB_KEY =
      new BLSPublicKey(VALIDATOR_1_SECRET_KEY).toString();
  private static final String VALIDATOR_2_PUB_KEY =
      new BLSPublicKey(VALIDATOR_2_SECRET_KEY).toString();

  private static final String PASSWORD = "test123";

  @Test
  void keysAreWrittenToEncryptedKeystores(@TempDir final Path tempDir) {
    final KeysWriter keysWriter =
        new EncryptedKeystoreWriter(
            SecureRandomProvider.createSecureRandom(),
            PASSWORD,
            PASSWORD,
            tempDir,
            System.out::println);
    keysWriter.writeKeys(
        new BLSKeyPair(VALIDATOR_1_SECRET_KEY), new BLSKeyPair(WITHDRAWAL_1_SECRET_KEY));

    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(trimPublicKey(VALIDATOR_1_PUB_KEY) + "_validator.json"),
        VALIDATOR_1_SECRET_KEY);
    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(trimPublicKey(VALIDATOR_1_PUB_KEY) + "_withdrawal.json"),
        WITHDRAWAL_1_SECRET_KEY);

    keysWriter.writeKeys(
        new BLSKeyPair(VALIDATOR_2_SECRET_KEY), new BLSKeyPair(WITHDRAWAL_2_SECRET_KEY));

    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(trimPublicKey(VALIDATOR_2_PUB_KEY) + "_validator.json"),
        VALIDATOR_2_SECRET_KEY);
    assertKeyStoreCreatedAndCanBeDecrypted(
        tempDir.resolve(trimPublicKey(VALIDATOR_2_PUB_KEY) + "_withdrawal.json"),
        WITHDRAWAL_2_SECRET_KEY);
  }

  private void assertKeyStoreCreatedAndCanBeDecrypted(
      final Path keystorePath, final BLSSecretKey blsSecretKey) {
    final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystorePath.toUri());
    assertThat(KeyStore.validatePassword(PASSWORD, keyStoreData)).isTrue();
    assertThat(KeyStore.decrypt(PASSWORD, keyStoreData)).isEqualTo(blsSecretKey.toBytes());
  }

  private String trimPublicKey(final String publicKey) {
    if (publicKey.toLowerCase(Locale.ROOT).startsWith("0x")) {
      return publicKey.substring(2, 9);
    }
    return publicKey.substring(0, 7);
  }
}
