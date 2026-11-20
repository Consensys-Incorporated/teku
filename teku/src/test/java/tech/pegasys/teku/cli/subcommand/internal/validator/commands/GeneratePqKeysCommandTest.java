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

package tech.pegasys.teku.cli.subcommand.internal.validator.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStore;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreData;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.PqKeyGenerator;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;
import tech.pegasys.teku.spec.signatures.xmss.PqValidatorPublicData;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;
import tech.pegasys.teku.xmss.XmssPublicKey;

class GeneratePqKeysCommandTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String PASSWORD = "testpassword";
  private static final BLSKeyPair BLS_KEY_PAIR = BLSTestUtil.randomKeyPair(1);

  @TempDir Path tempDir;
  private Path keysDir;
  private Path passwordsDir;
  private final StringWriter errors = new StringWriter();

  @BeforeEach
  void setUp() throws IOException {
    keysDir = Files.createDirectory(tempDir.resolve("keys"));
    passwordsDir = Files.createDirectory(tempDir.resolve("passwords"));
  }

  @Test
  void generatesKeysNextToBlsKeystores() throws IOException {
    writeBlsKeystore("validator_1", PASSWORD);
    final Path registrationFile = tempDir.resolve("registrations.json");

    final int exitCode =
        execute(
            "--bls-keystores",
            keysDir + File.pathSeparator + passwordsDir,
            "--registration-data-file",
            registrationFile.toString());

    assertThat(exitCode).isZero();
    final Path pqKeystore = keysDir.resolve("validator_1.pq-keystore");
    final PqKeyStoreData keyStoreData = PqKeyStore.loadFromFile(pqKeystore);
    assertThat(keyStoreData.getBlsPubkey())
        .contains(BLS_KEY_PAIR.getPublicKey().toBytesCompressed());
    // Encrypted with the BLS keystore's password, which the validator client loads it with
    assertThat(PqKeyStore.validatePassword(PASSWORD, keyStoreData)).isTrue();
    assertThat(keysDir.resolve("validator_1.vrf-cache")).exists();

    final JsonNode registrations = OBJECT_MAPPER.readTree(registrationFile.toFile());
    assertThat(registrations).hasSize(1);
    final JsonNode registration = registrations.get(0);
    final PqValidatorPublicData publicData =
        new PqValidatorPublicData(
            XmssPublicKey.fromBytes(keyStoreData.getXmssPubkey()), keyStoreData.getVrfRoot());
    assertThat(fields(registration))
        .containsExactly(
            Map.entry("bls_pubkey", BLS_KEY_PAIR.getPublicKey().toBytesCompressed().toHexString()),
            Map.entry("xmss_pubkey", keyStoreData.getXmssPubkey().toHexString()),
            Map.entry("vrf_root", keyStoreData.getVrfRoot().toHexString()),
            Map.entry("pq_data", publicData.getPqData().toHexString()),
            Map.entry("deposit_pubkey", publicData.getDepositPubkey().toHexString()),
            Map.entry("deposit_signature", publicData.getDepositSignature().toHexString()),
            Map.entry("keystore", pqKeystore.toString()));
  }

  @Test
  void generatesKeysForNewValidators() throws IOException {
    final Path outputDir = tempDir.resolve("pq-keys");
    final Path passwordFile = Files.writeString(tempDir.resolve("password.txt"), PASSWORD);

    final int exitCode =
        execute(
            "--number-of-validators",
            "2",
            "--keys-output-path",
            outputDir.toString(),
            "--encrypted-keystore-validator-password-file",
            passwordFile.toString());

    assertThat(exitCode).isZero();
    try (Stream<Path> files = Files.list(outputDir)) {
      final List<Path> keystores =
          files.filter(path -> path.toString().endsWith(".pq-keystore")).toList();
      assertThat(keystores).hasSize(2);
      for (final Path keystore : keystores) {
        final PqKeyStoreData keyStoreData = PqKeyStore.loadFromFile(keystore);
        assertThat(keyStoreData.getBlsPubkey()).isEmpty();
        // Named after the XMSS public key
        assertThat(keystore.getFileName().toString())
            .isEqualTo(
                "pq_" + keyStoreData.getXmssPubkey().toUnprefixedHexString() + ".pq-keystore");
        assertThat(PqKeyStore.validatePassword(PASSWORD, keyStoreData)).isTrue();
      }
    }
  }

  @Test
  void refusesToReplaceExistingPqKeystore() throws IOException {
    writeBlsKeystore("validator_1", PASSWORD);
    Files.writeString(keysDir.resolve("validator_1.pq-keystore"), "existing");

    final int exitCode = execute("--bls-keystores", keysDir + File.pathSeparator + passwordsDir);

    assertThat(exitCode).isNotZero();
    assertThat(errors.toString()).contains("Refusing to replace an existing PQ key file");
    assertThat(Files.readString(keysDir.resolve("validator_1.pq-keystore"))).isEqualTo("existing");
    assertThat(keysDir.resolve("validator_1.vrf-cache")).doesNotExist();
  }

  @Test
  void checksEveryKeystoreBeforeGeneratingAny() throws IOException {
    writeBlsKeystore("validator_1", PASSWORD);
    writeBlsKeystore("validator_2", PASSWORD);
    Files.writeString(passwordsDir.resolve("validator_2.txt"), "wrongpassword");

    final int exitCode = execute("--bls-keystores", keysDir + File.pathSeparator + passwordsDir);

    assertThat(exitCode).isNotZero();
    assertThat(errors.toString()).contains("does not decrypt BLS keystore");
    try (Stream<Path> files = Files.list(keysDir)) {
      assertThat(files.filter(path -> path.toString().endsWith(".pq-keystore"))).isEmpty();
    }
  }

  @Test
  void requiresExactlyOneSourceOfValidators() throws IOException {
    writeBlsKeystore("validator_1", PASSWORD);

    assertThat(execute()).isNotZero();
    assertThat(
            execute(
                "--bls-keystores",
                keysDir + File.pathSeparator + passwordsDir,
                "--number-of-validators",
                "1"))
        .isNotZero();
    assertThat(errors.toString())
        .contains("Specify exactly one of --bls-keystores or --number-of-validators");
  }

  @Test
  void newValidatorsRequireAPassword() {
    assertThat(execute("--number-of-validators", "1")).isNotZero();
    assertThat(errors.toString()).contains("New validators need a keystore password");
  }

  @Test
  void refusesToReplaceRegistrationDataFile() throws IOException {
    writeBlsKeystore("validator_1", PASSWORD);
    final Path registrationFile = Files.writeString(tempDir.resolve("reg.json"), "existing");

    final int exitCode =
        execute(
            "--bls-keystores",
            keysDir + File.pathSeparator + passwordsDir,
            "--registration-data-file",
            registrationFile.toString());

    assertThat(exitCode).isNotZero();
    assertThat(keysDir.resolve("validator_1.pq-keystore")).doesNotExist();
  }

  private int execute(final String... args) {
    final GeneratePqKeysCommand command =
        new GeneratePqKeysCommand(
            output ->
                new PqKeyGenerator(
                    SecureRandomProvider.createSecureRandom(),
                    // Stand-ins for leanXMSS key generation and the full-depth VRF tree
                    seed -> XmssPublicKey.fromBytes(Hash.sha256(seed)),
                    (seed, progress) -> VrfTree.derive(seed, 6, 2, progress),
                    2,
                    output),
            name -> null);
    return new CommandLine(command).setErr(new PrintWriter(errors, true)).execute(args);
  }

  private void writeBlsKeystore(final String name, final String password) throws IOException {
    KeyStoreLoader.saveToFile(
        keysDir.resolve(name + ".json"),
        KeyStore.encrypt(
            BLS_KEY_PAIR.getSecretKey().toBytes(),
            BLS_KEY_PAIR.getPublicKey().toBytesCompressed(),
            password,
            "",
            new Pbkdf2Param(32, 2, Pbkdf2PseudoRandomFunction.HMAC_SHA256, Bytes32.ZERO),
            new Cipher(Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6"))));
    Files.writeString(passwordsDir.resolve(name + ".txt"), password);
  }

  private static List<Map.Entry<String, String>> fields(final JsonNode node) {
    return node.properties().stream()
        .map(entry -> Map.entry(entry.getKey(), entry.getValue().asText()))
        .toList();
  }
}
