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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStore;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreData;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreSecrets;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.signatures.xmss.LocalXmssSigner;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;
import tech.pegasys.teku.spec.signatures.xmss.XmssLeafProtector;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;
import tech.pegasys.teku.xmss.XmssSigningKey;

/**
 * Loads a validator's PQ keys from the files next to its BLS keystore (see {@link PqKeyFiles}) and
 * builds its {@link LocalXmssSigner}. Every public value the keystore declares is checked against
 * the decrypted seeds before the signer is returned:
 *
 * <ul>
 *   <li>the VRF cache must hold nodes of the seed's tree (one is recomputed) and open to the
 *       keystore's {@code vrf_root};
 *   <li>the leanXMSS key must have the keystore's {@code xmss_pubkey};
 *   <li>if the keystore names a {@code bls_pubkey}, it must be the BLS keystore's.
 * </ul>
 *
 * A missing VRF cache is rebuilt from the seed, which hashes about 2^32 leaves and takes minutes.
 * Loading a leanXMSS key builds its top tree, which takes seconds.
 */
public class PqValidatorKeyLoader {

  private static final Logger LOG = LogManager.getLogger();
  private static final int PROGRESS_LOG_PERCENT = 10;

  private final Spec spec;
  private final XmssLeafProtector leafProtector;
  private final AsyncRunner asyncRunner;
  private final int vrfTreeDepth;
  private final int vrfCachedHeight;

  public PqValidatorKeyLoader(
      final Spec spec, final XmssLeafProtector leafProtector, final AsyncRunner asyncRunner) {
    this(
        spec,
        leafProtector,
        asyncRunner,
        PqConstants.VRF_TREE_DEPTH,
        VrfTree.DEFAULT_CACHED_HEIGHT);
  }

  /** Loads keys with a VRF tree of the given shape; real keys use the spec's depth. */
  @VisibleForTesting
  public PqValidatorKeyLoader(
      final Spec spec,
      final XmssLeafProtector leafProtector,
      final AsyncRunner asyncRunner,
      final int vrfTreeDepth,
      final int vrfCachedHeight) {
    this.spec = spec;
    this.leafProtector = leafProtector;
    this.asyncRunner = asyncRunner;
    this.vrfTreeDepth = vrfTreeDepth;
    this.vrfCachedHeight = vrfCachedHeight;
  }

  /**
   * Loads the PQ keys stored next to {@code blsKeystorePath}, encrypted with {@code password}.
   * Returns empty if the validator has no PQ keystore.
   *
   * @throws InvalidConfigurationException if the PQ keystore exists but cannot be loaded or does
   *     not match its seeds
   */
  public Optional<XmssSigner> load(
      final Path blsKeystorePath, final String password, final BLSPublicKey blsPublicKey) {
    final Path keystorePath = PqKeyFiles.keystorePath(blsKeystorePath);
    if (!Files.exists(keystorePath)) {
      return Optional.empty();
    }
    final PqKeyStoreData keyStoreData = loadKeyStore(keystorePath);
    keyStoreData
        .getBlsPubkey()
        .filter(declared -> !declared.equals(blsPublicKey.toBytesCompressed()))
        .ifPresent(
            declared -> {
              throw new InvalidConfigurationException(
                  String.format(
                      "PQ keystore %s belongs to BLS key %s, not %s from %s",
                      keystorePath, declared, blsPublicKey, blsKeystorePath));
            });
    return Optional.of(
        createSigner(
            keystorePath, keyStoreData, PqKeyFiles.vrfCachePath(blsKeystorePath), password));
  }

  @VisibleForTesting
  XmssSigner createSigner(
      final Path keystorePath,
      final PqKeyStoreData keyStoreData,
      final Path vrfCachePath,
      final String password) {
    final PqKeyStoreSecrets secrets = decrypt(keystorePath, keyStoreData, password);
    final VrfTree vrfTree = loadVrfTree(keystorePath, keyStoreData, vrfCachePath, secrets);
    final XmssSigningKey signingKey =
        Xmss.loadSigningKey(XmssSecretKey.fromBytes(secrets.xmssSeed()));
    final XmssPublicKey declaredPublicKey = XmssPublicKey.fromBytes(keyStoreData.getXmssPubkey());
    if (!signingKey.getPublicKey().equals(declaredPublicKey)) {
      signingKey.close();
      throw new InvalidConfigurationException(
          String.format(
              "PQ keystore %s declares XMSS public key %s but its seed has %s",
              keystorePath, declaredPublicKey, signingKey.getPublicKey()));
    }
    return new LocalXmssSigner(spec, signingKey, vrfTree, leafProtector, asyncRunner);
  }

  private static PqKeyStoreData loadKeyStore(final Path keystorePath) {
    try {
      return PqKeyStore.loadFromFile(keystorePath);
    } catch (final KeyStoreValidationException e) {
      throw new InvalidConfigurationException(
          "Invalid PQ keystore " + keystorePath + ": " + e.getMessage(), e);
    }
  }

  private static PqKeyStoreSecrets decrypt(
      final Path keystorePath, final PqKeyStoreData keyStoreData, final String password) {
    try {
      return PqKeyStore.decrypt(password, keyStoreData);
    } catch (final KeyStoreValidationException e) {
      throw new InvalidConfigurationException(
          "Failed to decrypt PQ keystore "
              + keystorePath
              + ". It must use the same password as its BLS keystore.",
          e);
    }
  }

  private VrfTree loadVrfTree(
      final Path keystorePath,
      final PqKeyStoreData keyStoreData,
      final Path vrfCachePath,
      final PqKeyStoreSecrets secrets) {
    final VrfTree vrfTree =
        Files.exists(vrfCachePath)
            ? readVrfCache(vrfCachePath, secrets.vrfSeed())
            : rebuildVrfCache(keystorePath, vrfCachePath, secrets.vrfSeed());
    if (!vrfTree.getRoot().equals(keyStoreData.getVrfRoot())) {
      throw new InvalidConfigurationException(
          String.format(
              "VRF tree from %s has root %s but PQ keystore %s declares VRF root %s",
              vrfCachePath, vrfTree.getRoot(), keystorePath, keyStoreData.getVrfRoot()));
    }
    return vrfTree;
  }

  private VrfTree readVrfCache(final Path vrfCachePath, final Bytes32 vrfSeed) {
    final VrfTree vrfTree;
    try {
      vrfTree =
          VrfTree.fromCachedNodes(
              vrfSeed, vrfTreeDepth, vrfCachedHeight, Bytes.wrap(Files.readAllBytes(vrfCachePath)));
    } catch (final IOException | IllegalArgumentException e) {
      throw new InvalidConfigurationException(
          "Invalid VRF cache " + vrfCachePath + ": " + e.getMessage(), e);
    }
    // The root check only shows the nodes are consistent with each other and the keystore.
    // Recompute one node from the seed (2^cachedHeight hashes) to show they come from this seed.
    final int nodeCount = 1 << (vrfTreeDepth - vrfCachedHeight);
    final int index = ThreadLocalRandom.current().nextInt(nodeCount);
    final Bytes cachedNode = vrfTree.getCachedNodes().slice(index * Bytes32.SIZE, Bytes32.SIZE);
    if (!VrfTree.computeNode(vrfSeed, vrfCachedHeight, index).equals(cachedNode)) {
      throw new InvalidConfigurationException(
          "VRF cache " + vrfCachePath + " was not derived from its keystore's VRF seed");
    }
    return vrfTree;
  }

  private VrfTree rebuildVrfCache(
      final Path keystorePath, final Path vrfCachePath, final Bytes32 vrfSeed) {
    LOG.warn(
        "VRF cache {} for PQ keystore {} is missing. Rebuilding it from the VRF seed, which can"
            + " take several minutes.",
        vrfCachePath,
        keystorePath);
    final VrfTree vrfTree =
        VrfTree.derive(
            vrfSeed,
            vrfTreeDepth,
            vrfCachedHeight,
            progressLogger("Rebuilding VRF cache " + vrfCachePath));
    try {
      SyncDataAccessor.create(vrfCachePath.toAbsolutePath().getParent())
          .syncedWrite(vrfCachePath, vrfTree.getCachedNodes());
    } catch (final IOException e) {
      LOG.warn("Unable to save rebuilt VRF cache {}: {}", vrfCachePath, e.getMessage());
    }
    return vrfTree;
  }

  private static VrfTree.DeriveProgressListener progressLogger(final String task) {
    return (derivedNodes, totalNodes) -> {
      final long percent = 100L * derivedNodes / totalNodes;
      final long previousPercent = 100L * (derivedNodes - 1) / totalNodes;
      if (percent / PROGRESS_LOG_PERCENT != previousPercent / PROGRESS_LOG_PERCENT) {
        LOG.info("{}: {}%", task, percent);
      }
    };
  }
}
