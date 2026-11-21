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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStore;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreSecrets;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.xmss.LocalXmssLeafProtector;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;
import tech.pegasys.teku.xmss.XmssSignature;

class PqValidatorKeyLoaderTest {

  // A small VRF tree: deriving the real one hashes about 2^32 leaves
  private static final int VRF_DEPTH = 8;
  private static final int VRF_CACHED_HEIGHT = 4;
  private static final String PASSWORD = "testpassword";
  private static final PqKeyStoreSecrets SECRETS =
      new PqKeyStoreSecrets(
          Bytes32.fromHexString(
              "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"),
          Bytes32.fromHexStringLenient("0x5eed"));
  private static final VrfTree VRF_TREE =
      VrfTree.derive(SECRETS.vrfSeed(), VRF_DEPTH, VRF_CACHED_HEIGHT);

  // Validator 0 of a PQ genesis made by ethpandaops/eth-beacon-genesis (pq branch) for the mnemonic
  // "giant issue aisle success illegal bike spike question tent bar rely arctic volcano long crawl
  // hungry vocal artwork sniff fantasy very lucky have athlete": its BLS secret key at
  // m/12381/3600/0/0/0 (EIP-2333), and the XMSS public key and VRF seed the tool derived from it.
  // The genesis tool's own VRF test pins the same VRF seed.
  private static final Bytes32 GENESIS_VALIDATOR_0_BLS_SECRET_KEY =
      Bytes32.fromHexString("0x0dce41fa73ae9f6bdfd51df4d422d75eee174553dba5fd450c4437e4ed3fc903");
  private static final BLSPublicKey GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY =
      BLSSecretKey.fromBytes(GENESIS_VALIDATOR_0_BLS_SECRET_KEY).toPublicKey();
  private static final XmssPublicKey GENESIS_VALIDATOR_0_XMSS_PUBLIC_KEY =
      XmssPublicKey.fromBytes(
          Bytes32.fromHexString(
              "0xd7792e263b94cf0b7a9f70d8374c6d34a600b4247057d4079f810caff711c632"));
  private static final Bytes32 GENESIS_VALIDATOR_0_VRF_SEED =
      Bytes32.fromHexString("0x543fc5a4d86e79a434e9741770f7ee17c25bdc31be8d61436d7f9836538b03df");

  // Loading a key builds its top tree, so derive the public key once
  private static XmssPublicKey xmssPublicKey;

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();

  @TempDir Path tempDir;
  private Path blsKeystorePath;
  private Path slashingProtectionDir;
  private PqValidatorKeyLoader loader;

  @BeforeAll
  static void deriveXmssPublicKey() {
    xmssPublicKey = Xmss.derivePublicKey(XmssSecretKey.fromBytes(SECRETS.xmssSeed()));
  }

  @BeforeEach
  void setUp() throws IOException {
    blsKeystorePath = tempDir.resolve("validator_1.json");
    slashingProtectionDir = Files.createDirectory(tempDir.resolve("slashprotection"));
    loader =
        new PqValidatorKeyLoader(
            spec,
            new LocalXmssLeafProtector(
                SyncDataAccessor.create(slashingProtectionDir), slashingProtectionDir),
            SYNC_RUNNER,
            VRF_DEPTH,
            VRF_CACHED_HEIGHT);
  }

  @Test
  void load_returnsEmptyWithoutPqKeystore() {
    assertThat(loader.load(blsKeystorePath, PASSWORD, blsPublicKey)).isEmpty();
  }

  @Test
  void load_buildsSignerFromKeystoreAndVrfCache() {
    writeKeystore(xmssPublicKey.toBytes(), VRF_TREE.getRoot(), Optional.of(blsPublicKey));
    writeVrfCache(VRF_TREE.getCachedNodes());

    final XmssSigner signer = loader.load(blsKeystorePath, PASSWORD, blsPublicKey).orElseThrow();

    assertThat(signer.getPublicKey()).isEqualTo(xmssPublicKey);
    final UInt64 slot = UInt64.valueOf(77);
    final List<Bytes32> proof = safeJoin(signer.computeVrfProof(slot));
    assertThat(VrfTree.computeRootFromProof(proof, slot)).isEqualTo(VRF_TREE.getRoot());

    // Signs with the keystore's key and records the leaf in the slashing protection directory
    final Bytes32 signingRoot = Bytes32.fromHexStringLenient("0x1234");
    final XmssSignature signature = safeJoin(signer.signAtReservedLeaf(signingRoot));
    assertThat(Xmss.verify(xmssPublicKey, signature.getLeafIndex(), signingRoot, signature))
        .isTrue();
    assertThat(
            slashingProtectionDir.resolve(
                xmssPublicKey.toBytes().toUnprefixedHexString() + ".xmss-leaves"))
        .exists();
    signer.delete();
  }

  @Test
  void load_acceptsKeystoreWithoutBlsPubkey() {
    writeKeystore(xmssPublicKey.toBytes(), VRF_TREE.getRoot(), Optional.empty());
    writeVrfCache(VRF_TREE.getCachedNodes());

    final XmssSigner signer = loader.load(blsKeystorePath, PASSWORD, blsPublicKey).orElseThrow();

    assertThat(signer.getPublicKey()).isEqualTo(xmssPublicKey);
    signer.delete();
  }

  @Test
  void load_rebuildsMissingVrfCache() throws IOException {
    writeKeystore(xmssPublicKey.toBytes(), VRF_TREE.getRoot(), Optional.of(blsPublicKey));

    final XmssSigner signer = loader.load(blsKeystorePath, PASSWORD, blsPublicKey).orElseThrow();

    assertThat(Bytes.wrap(Files.readAllBytes(PqKeyFiles.vrfCachePath(blsKeystorePath))))
        .isEqualTo(VRF_TREE.getCachedNodes());
    signer.delete();
  }

  @Test
  void load_rejectsKeystoreOfAnotherBlsKey() {
    writeKeystore(
        xmssPublicKey.toBytes(),
        VRF_TREE.getRoot(),
        Optional.of(dataStructureUtil.randomPublicKey()));
    writeVrfCache(VRF_TREE.getCachedNodes());

    assertThatThrownBy(() -> loader.load(blsKeystorePath, PASSWORD, blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("belongs to BLS key");
  }

  @Test
  void load_rejectsWrongPassword() {
    writeKeystore(xmssPublicKey.toBytes(), VRF_TREE.getRoot(), Optional.of(blsPublicKey));
    writeVrfCache(VRF_TREE.getCachedNodes());

    assertThatThrownBy(() -> loader.load(blsKeystorePath, "wrong", blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed to decrypt PQ keystore");
  }

  @Test
  void load_rejectsInvalidKeystore() throws IOException {
    Files.writeString(PqKeyFiles.keystorePath(blsKeystorePath), "{}");

    assertThatThrownBy(() -> loader.load(blsKeystorePath, PASSWORD, blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Invalid PQ keystore");
  }

  @Test
  void load_rejectsVrfCacheOfAnotherSeed() {
    final VrfTree otherTree =
        VrfTree.derive(Bytes32.fromHexStringLenient("0x07e5"), VRF_DEPTH, VRF_CACHED_HEIGHT);
    // The keystore and cache agree with each other, but not with the keystore's VRF seed
    writeKeystore(xmssPublicKey.toBytes(), otherTree.getRoot(), Optional.of(blsPublicKey));
    writeVrfCache(otherTree.getCachedNodes());

    assertThatThrownBy(() -> loader.load(blsKeystorePath, PASSWORD, blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("was not derived from its keystore's VRF seed");
  }

  @Test
  void load_rejectsVrfRootThatDoesNotMatchTheCache() {
    writeKeystore(
        xmssPublicKey.toBytes(), Bytes32.fromHexStringLenient("0xbad"), Optional.of(blsPublicKey));
    writeVrfCache(VRF_TREE.getCachedNodes());

    assertThatThrownBy(() -> loader.load(blsKeystorePath, PASSWORD, blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("declares VRF root");
  }

  @Test
  void load_rejectsTruncatedVrfCache() {
    writeKeystore(xmssPublicKey.toBytes(), VRF_TREE.getRoot(), Optional.of(blsPublicKey));
    writeVrfCache(VRF_TREE.getCachedNodes().slice(0, Bytes32.SIZE));

    assertThatThrownBy(() -> loader.load(blsKeystorePath, PASSWORD, blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Invalid VRF cache");
  }

  @Test
  void load_rejectsXmssPubkeyThatDoesNotMatchTheSeed() {
    writeKeystore(
        Bytes32.fromHexStringLenient("0xbad"), VRF_TREE.getRoot(), Optional.of(blsPublicKey));
    writeVrfCache(VRF_TREE.getCachedNodes());

    assertThatThrownBy(() -> loader.load(blsKeystorePath, PASSWORD, blsPublicKey))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("declares XMSS public key");
  }

  @Test
  void load_derivesKeysFromBlsKeyLikeTheDevnetGenesisTool() {
    final PqValidatorKeyLoader derivingLoader = derivingLoader();

    final XmssSigner signer =
        derivingLoader
            .load(
                blsKeystorePath,
                PASSWORD,
                GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY,
                () -> GENESIS_VALIDATOR_0_BLS_SECRET_KEY)
            .orElseThrow();

    // The XMSS public key the genesis tool registered for this validator
    assertThat(signer.getPublicKey()).isEqualTo(GENESIS_VALIDATOR_0_XMSS_PUBLIC_KEY);
    final VrfTree expectedVrfTree =
        VrfTree.derive(GENESIS_VALIDATOR_0_VRF_SEED, VRF_DEPTH, VRF_CACHED_HEIGHT);
    final UInt64 slot = UInt64.valueOf(201);
    assertThat(VrfTree.computeRootFromProof(safeJoin(signer.computeVrfProof(slot)), slot))
        .isEqualTo(expectedVrfTree.getRoot());
    assertThat(derivedVrfCachePath())
        .hasBinaryContent(expectedVrfTree.getCachedNodes().toArrayUnsafe());
  }

  @Test
  void load_derivedKeysReadTheSavedVrfCache() {
    // A cache from another seed shows the saved cache is read, not rebuilt
    writeFile(derivedVrfCachePath(), VRF_TREE.getCachedNodes());

    assertThatThrownBy(
            () ->
                derivingLoader()
                    .load(
                        blsKeystorePath,
                        PASSWORD,
                        GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY,
                        () -> GENESIS_VALIDATOR_0_BLS_SECRET_KEY))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("was not derived from");
  }

  @Test
  void load_prefersPqKeystoreOverDerivedKeys() {
    writeKeystore(xmssPublicKey.toBytes(), VRF_TREE.getRoot(), Optional.of(blsPublicKey));
    writeVrfCache(VRF_TREE.getCachedNodes());

    final XmssSigner signer =
        derivingLoader()
            .load(blsKeystorePath, PASSWORD, blsPublicKey, PqValidatorKeyLoaderTest::noSecretKey)
            .orElseThrow();

    assertThat(signer.getPublicKey()).isEqualTo(xmssPublicKey);
  }

  @Test
  void load_doesNotDecryptBlsKeyWhenDerivingIsDisabled() {
    assertThat(
            loader.load(
                blsKeystorePath, PASSWORD, blsPublicKey, PqValidatorKeyLoaderTest::noSecretKey))
        .isEmpty();
  }

  @Test
  void load_derivedKeysTakeTheirVrfStateFromTheStateUrl() {
    // Another key's state first, as in a file holding many validators' states
    final Path statesFile = tempDir.resolve("states.vrf");
    writeFile(
        statesFile,
        Bytes.concatenate(vrfStateOf(SECRETS.vrfSeed()), vrfStateOf(GENESIS_VALIDATOR_0_VRF_SEED)));

    final XmssSigner signer =
        derivingLoader(Optional.of(statesFile.toUri()))
            .load(
                blsKeystorePath,
                PASSWORD,
                GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY,
                () -> GENESIS_VALIDATOR_0_BLS_SECRET_KEY)
            .orElseThrow();

    final VrfTree expectedVrfTree =
        VrfTree.derive(GENESIS_VALIDATOR_0_VRF_SEED, VRF_DEPTH, VRF_CACHED_HEIGHT);
    final UInt64 slot = UInt64.valueOf(201);
    assertThat(safeJoin(signer.computeVrfProof(slot)))
        .isEqualTo(expectedVrfTree.computeProof(slot));
    // The state is saved, and the tree isn't derived in full
    assertThat(derivedVrfStatePath())
        .hasBinaryContent(vrfStateOf(GENESIS_VALIDATOR_0_VRF_SEED).toArrayUnsafe());
    assertThat(derivedVrfCachePath()).doesNotExist();
  }

  @Test
  void load_derivedKeysLoadedInParallelAllSaveTheirVrfStates() {
    // Keys load in parallel, and each saves its state as soon as it is found
    final List<Bytes32> blsSecretKeys =
        IntStream.range(0, 4)
            .mapToObj(seed -> BLSTestUtil.randomKeyPair(seed).getSecretKey().toBytes())
            .toList();
    final Path statesFile = tempDir.resolve("states.vrf");
    writeFile(
        statesFile,
        Bytes.concatenate(
            blsSecretKeys.stream()
                .map(sk -> vrfStateOf(PqKeyStoreSecrets.deriveFromBlsSecretKey(sk).vrfSeed()))
                .toArray(Bytes[]::new)));
    final PqValidatorKeyLoader loader = derivingLoader(Optional.of(statesFile.toUri()));
    final ExecutorService executor = Executors.newFixedThreadPool(blsSecretKeys.size());
    try {
      final List<SafeFuture<Optional<XmssSigner>>> loads =
          blsSecretKeys.stream()
              .map(
                  sk ->
                      SafeFuture.of(
                          CompletableFuture.supplyAsync(
                              () ->
                                  loader.load(
                                      blsKeystorePath,
                                      PASSWORD,
                                      BLSSecretKey.fromBytes(sk).toPublicKey(),
                                      () -> sk),
                              executor)))
              .toList();

      assertThat(SafeFuture.collectAll(loads.stream()))
          .succeedsWithin(Duration.ofMinutes(2))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .allMatch(signer -> ((Optional<?>) signer).isPresent());
    } finally {
      executor.shutdownNow();
    }
    for (final Bytes32 sk : blsSecretKeys) {
      assertThat(
              PqValidatorKeyLoader.derivedVrfStatePath(
                  derivedVrfCacheDir(), BLSSecretKey.fromBytes(sk).toPublicKey()))
          .exists();
    }
  }

  @Test
  void load_derivedKeysReadTheirSavedVrfState() {
    writeFile(derivedVrfStatePath(), vrfStateOf(GENESIS_VALIDATOR_0_VRF_SEED));

    // No state URL: the saved state alone saves deriving the tree
    final XmssSigner signer =
        derivingLoader()
            .load(
                blsKeystorePath,
                PASSWORD,
                GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY,
                () -> GENESIS_VALIDATOR_0_BLS_SECRET_KEY)
            .orElseThrow();

    final UInt64 slot = UInt64.valueOf(77);
    assertThat(safeJoin(signer.computeVrfProof(slot)))
        .isEqualTo(
            VrfTree.derive(GENESIS_VALIDATOR_0_VRF_SEED, VRF_DEPTH, VRF_CACHED_HEIGHT)
                .computeProof(slot));
    assertThat(derivedVrfCachePath()).doesNotExist();
  }

  @Test
  void load_derivesTheVrfTreeWhenTheStateUrlHasNoStateForTheKey() {
    final Path statesFile = tempDir.resolve("states.vrf");
    writeFile(statesFile, vrfStateOf(SECRETS.vrfSeed()));

    assertThat(
            derivingLoader(Optional.of(statesFile.toUri()))
                .load(
                    blsKeystorePath,
                    PASSWORD,
                    GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY,
                    () -> GENESIS_VALIDATOR_0_BLS_SECRET_KEY))
        .isPresent();

    assertThat(derivedVrfCachePath()).exists();
    assertThat(derivedVrfStatePath()).doesNotExist();
  }

  @Test
  void load_derivesTheVrfTreeWhenTheStateUrlCannotBeRead() {
    assertThat(
            derivingLoader(Optional.of(tempDir.resolve("missing.vrf").toUri()))
                .load(
                    blsKeystorePath,
                    PASSWORD,
                    GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY,
                    () -> GENESIS_VALIDATOR_0_BLS_SECRET_KEY))
        .isPresent();

    assertThat(derivedVrfCachePath()).exists();
  }

  /** The serialized slot-0 VRF state of the test-depth tree of {@code seed}. */
  private static Bytes vrfStateOf(final Bytes32 seed) {
    final VrfTree tree = VrfTree.derive(seed, VRF_DEPTH, VRF_CACHED_HEIGHT);
    final List<Bytes> siblings = new ArrayList<>();
    for (int height = 0; height < VRF_DEPTH; height++) {
      siblings.add(VrfTree.computeNode(seed, height, 1));
    }
    return Bytes.concatenate(
        tree.getRoot(), Bytes.wrap(new byte[Integer.BYTES]), Bytes.concatenate(siblings));
  }

  private Path derivedVrfStatePath() {
    return PqValidatorKeyLoader.derivedVrfStatePath(
        derivedVrfCacheDir(), GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY);
  }

  private PqValidatorKeyLoader derivingLoader() {
    return derivingLoader(Optional.empty());
  }

  private PqValidatorKeyLoader derivingLoader(final Optional<URI> vrfStateUrl) {
    return new PqValidatorKeyLoader(
        spec,
        new PqKeyLoadingConfig(
            new LocalXmssLeafProtector(
                SyncDataAccessor.create(slashingProtectionDir), slashingProtectionDir),
            Optional.of(derivedVrfCacheDir()),
            vrfStateUrl),
        SYNC_RUNNER,
        VRF_DEPTH,
        VRF_CACHED_HEIGHT);
  }

  private Path derivedVrfCacheDir() {
    return tempDir.resolve("pq-derived-vrf-cache");
  }

  private Path derivedVrfCachePath() {
    return PqValidatorKeyLoader.derivedVrfCachePath(
        derivedVrfCacheDir(), GENESIS_VALIDATOR_0_BLS_PUBLIC_KEY);
  }

  private static Bytes32 noSecretKey() {
    throw new AssertionError("The BLS secret key should not be needed");
  }

  private void writeKeystore(
      final Bytes32 declaredXmssPubkey,
      final Bytes32 declaredVrfRoot,
      final Optional<BLSPublicKey> declaredBlsKey) {
    try {
      PqKeyStore.saveToNewFile(
          PqKeyFiles.keystorePath(blsKeystorePath),
          PqKeyStore.encrypt(
              SECRETS,
              declaredXmssPubkey,
              declaredVrfRoot,
              declaredBlsKey.map(BLSPublicKey::toBytesCompressed),
              PASSWORD,
              new Pbkdf2Param(32, 2, Pbkdf2PseudoRandomFunction.HMAC_SHA256, Bytes32.ZERO),
              new Cipher(Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6"))));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeVrfCache(final Bytes nodes) {
    writeFile(PqKeyFiles.vrfCachePath(blsKeystorePath), nodes);
  }

  private static void writeFile(final Path path, final Bytes content) {
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, content.toArrayUnsafe());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
