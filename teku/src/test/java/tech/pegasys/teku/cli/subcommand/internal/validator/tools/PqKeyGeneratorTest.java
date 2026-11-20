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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStore;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreData;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreSecrets;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.PqKeyGenerator.GeneratedPqKey;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.PqKeyGenerator.PqKeyPaths;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.xmss.LocalXmssLeafProtector;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.validator.client.loader.PqKeyFiles;
import tech.pegasys.teku.validator.client.loader.PqValidatorKeyLoader;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;

class PqKeyGeneratorTest {

  // A small VRF tree: deriving the real one hashes about 2^32 leaves
  private static final int VRF_DEPTH = 8;
  private static final int VRF_CACHED_HEIGHT = 4;
  private static final String PASSWORD = "testpassword";
  // Stands in for leanXMSS key generation, which builds a top tree and takes seconds
  private static final Function<Bytes32, XmssPublicKey> FAKE_XMSS_PUBLIC_KEY =
      seed -> XmssPublicKey.fromBytes(Hash.sha256(seed));

  private final BLSPublicKey blsPublicKey = BLSTestUtil.randomPublicKey(1);
  private final List<String> output = new CopyOnWriteArrayList<>();

  @TempDir Path tempDir;
  private Path blsKeystorePath;
  private PqKeyPaths paths;

  @BeforeEach
  void setUp() {
    blsKeystorePath = tempDir.resolve("validator_1.json");
    paths =
        new PqKeyPaths(
            PqKeyFiles.keystorePath(blsKeystorePath), PqKeyFiles.vrfCachePath(blsKeystorePath));
  }

  @Test
  void generate_writesKeystoreAndVrfCacheForTheReturnedPublicData() throws IOException {
    final GeneratedPqKey generated =
        generator(FAKE_XMSS_PUBLIC_KEY)
            .generate("Validator 1/1", key -> paths, PASSWORD, Optional.of(blsPublicKey));

    final PqKeyStoreData keyStoreData = PqKeyStore.loadFromFile(paths.keystore());
    final PqKeyStoreSecrets secrets = PqKeyStore.decrypt(PASSWORD, keyStoreData);
    final XmssPublicKey xmssPublicKey = FAKE_XMSS_PUBLIC_KEY.apply(secrets.xmssSeed());
    final VrfTree vrfTree =
        VrfTree.fromCachedNodes(
            secrets.vrfSeed(),
            VRF_DEPTH,
            VRF_CACHED_HEIGHT,
            Bytes.wrap(Files.readAllBytes(paths.vrfCache())));

    assertThat(secrets.xmssSeed()).isNotEqualTo(secrets.vrfSeed());
    assertThat(keyStoreData.getXmssPubkey()).isEqualTo(xmssPublicKey.toBytes());
    assertThat(keyStoreData.getVrfRoot()).isEqualTo(vrfTree.getRoot());
    assertThat(vrfTree.getRoot())
        .isEqualTo(VrfTree.derive(secrets.vrfSeed(), VRF_DEPTH, VRF_CACHED_HEIGHT).getRoot());
    assertThat(keyStoreData.getBlsPubkey()).contains(blsPublicKey.toBytesCompressed());
    assertThat(generated.publicData().xmssPublicKey()).isEqualTo(xmssPublicKey);
    assertThat(generated.publicData().vrfRoot()).isEqualTo(vrfTree.getRoot());
    assertThat(generated.blsPublicKey()).contains(blsPublicKey);
    assertThat(generated.paths()).isEqualTo(paths);
    assertThat(output).contains("Validator 1/1: VRF tree 100%");
  }

  @Test
  void generate_usesFreshSeedsForEachKey() {
    final PqKeyGenerator generator = generator(FAKE_XMSS_PUBLIC_KEY);
    final GeneratedPqKey first =
        generator.generate("first", key -> paths, PASSWORD, Optional.empty());
    final GeneratedPqKey second =
        generator.generate(
            "second",
            key ->
                new PqKeyPaths(
                    PqKeyFiles.keystorePath(tempDir, "second"),
                    PqKeyFiles.vrfCachePath(tempDir, "second")),
            PASSWORD,
            Optional.empty());

    assertThat(first.publicData().xmssPublicKey())
        .isNotEqualTo(second.publicData().xmssPublicKey());
    assertThat(first.publicData().vrfRoot()).isNotEqualTo(second.publicData().vrfRoot());
    assertThat(PqKeyStore.loadFromFile(paths.keystore()).getBlsPubkey()).isEmpty();
  }

  @Test
  void generate_refusesToReplaceExistingFiles() throws IOException {
    Files.writeString(paths.vrfCache(), "existing");

    assertThatThrownBy(
            () ->
                generator(FAKE_XMSS_PUBLIC_KEY)
                    .generate("Validator 1/1", key -> paths, PASSWORD, Optional.empty()))
        .isInstanceOf(UncheckedIOException.class)
        .hasCauseInstanceOf(FileAlreadyExistsException.class);
    assertThat(paths.keystore()).doesNotExist();
    assertThat(Files.readString(paths.vrfCache())).isEqualTo("existing");
  }

  @Test
  void generate_writesKeysTheValidatorClientLoads() {
    final GeneratedPqKey generated =
        generator(seed -> Xmss.derivePublicKey(XmssSecretKey.fromBytes(seed)))
            .generate("Validator 1/1", key -> paths, PASSWORD, Optional.of(blsPublicKey));
    final Path slashingProtectionDir = tempDir.resolve("slashprotection");
    final PqValidatorKeyLoader loader =
        new PqValidatorKeyLoader(
            TestSpecFactory.createMinimalPq(),
            new LocalXmssLeafProtector(SyncDataAccessor.create(tempDir), slashingProtectionDir),
            SYNC_RUNNER,
            VRF_DEPTH,
            VRF_CACHED_HEIGHT);

    final XmssSigner signer = loader.load(blsKeystorePath, PASSWORD, blsPublicKey).orElseThrow();

    assertThat(signer.getPublicKey()).isEqualTo(generated.publicData().xmssPublicKey());
    signer.delete();
  }

  private PqKeyGenerator generator(final Function<Bytes32, XmssPublicKey> xmssPublicKeyDeriver) {
    return new PqKeyGenerator(
        SecureRandomProvider.createSecureRandom(),
        xmssPublicKeyDeriver,
        (seed, progress) -> VrfTree.derive(seed, VRF_DEPTH, VRF_CACHED_HEIGHT, progress),
        2,
        output::add);
  }
}
