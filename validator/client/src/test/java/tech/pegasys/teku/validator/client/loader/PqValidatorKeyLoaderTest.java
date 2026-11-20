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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStore;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreSecrets;
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
    try {
      Files.write(PqKeyFiles.vrfCachePath(blsKeystorePath), nodes.toArrayUnsafe());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
