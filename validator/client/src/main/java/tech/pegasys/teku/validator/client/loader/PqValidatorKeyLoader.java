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
import com.google.common.base.Suppliers;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
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
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.spec.signatures.xmss.LocalXmssSigner;
import tech.pegasys.teku.spec.signatures.xmss.VrfLeafZeroState;
import tech.pegasys.teku.spec.signatures.xmss.VrfProver;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;
import tech.pegasys.teku.spec.signatures.xmss.XmssLeafProtector;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.validator.client.loader.DevnetXmssCacheSource.RegisteredKey;
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
 *
 * <p>On PQ devnets, a validator with no PQ keystore can instead get keys derived from its BLS
 * secret key (see {@link PqKeyStoreSecrets#deriveFromBlsSecretKey}), when {@link
 * PqKeyLoadingConfig#derivedKeysVrfCacheDirectory()} is set. There are no declared public values to
 * check those against; a key that doesn't match the chain's registry simply finds no validator.
 */
public class PqValidatorKeyLoader {

  private static final Logger LOG = LogManager.getLogger();
  private static final int PROGRESS_LOG_PERCENT = 10;
  static final String VRF_STATE_SUFFIX = ".vrf-state";
  private static final int VRF_STATE_CONNECT_TIMEOUT_MILLIS = 30_000;
  private static final int VRF_STATE_READ_TIMEOUT_MILLIS = 300_000;

  private final Spec spec;
  private final XmssLeafProtector leafProtector;
  private final Optional<Path> derivedKeysVrfCacheDirectory;
  private final Optional<URI> vrfStateUrl;
  private final Optional<DevnetXmssCacheSource> xmssCacheSource;
  // Fetched at most once, when the first derived key without a saved VRF tree or state needs it
  private final Supplier<Optional<Bytes>> fetchedVrfStates;
  private final Map<Path, SyncDataAccessor> dataAccessors = new ConcurrentHashMap<>();
  private final AsyncRunner asyncRunner;
  private final int vrfTreeDepth;
  private final int vrfCachedHeight;

  public PqValidatorKeyLoader(
      final Spec spec, final XmssLeafProtector leafProtector, final AsyncRunner asyncRunner) {
    this(spec, PqKeyLoadingConfig.keystoresOnly(leafProtector), asyncRunner);
  }

  public PqValidatorKeyLoader(
      final Spec spec, final PqKeyLoadingConfig config, final AsyncRunner asyncRunner) {
    this(spec, config, asyncRunner, PqConstants.VRF_TREE_DEPTH, VrfTree.DEFAULT_CACHED_HEIGHT);
  }

  /** Loads keys with a VRF tree of the given shape; real keys use the spec's depth. */
  @VisibleForTesting
  public PqValidatorKeyLoader(
      final Spec spec,
      final XmssLeafProtector leafProtector,
      final AsyncRunner asyncRunner,
      final int vrfTreeDepth,
      final int vrfCachedHeight) {
    this(
        spec,
        PqKeyLoadingConfig.keystoresOnly(leafProtector),
        asyncRunner,
        vrfTreeDepth,
        vrfCachedHeight);
  }

  /** Loads keys with a VRF tree of the given shape; real keys use the spec's depth. */
  @VisibleForTesting
  public PqValidatorKeyLoader(
      final Spec spec,
      final PqKeyLoadingConfig config,
      final AsyncRunner asyncRunner,
      final int vrfTreeDepth,
      final int vrfCachedHeight) {
    this.spec = spec;
    this.leafProtector = config.leafProtector();
    this.derivedKeysVrfCacheDirectory = config.derivedKeysVrfCacheDirectory();
    this.vrfStateUrl = config.vrfStateUrl();
    this.fetchedVrfStates =
        Suppliers.memoize(() -> vrfStateUrl.flatMap(PqValidatorKeyLoader::fetchVrfStates));
    final Supplier<Optional<List<RegisteredKey>>> registeredKeys =
        Suppliers.memoize(
            () ->
                config
                    .genesisState()
                    .flatMap(genesisState -> readRegisteredKeys(spec, genesisState)));
    this.xmssCacheSource =
        config
            .xmssCacheUrl()
            .map(url -> new DevnetXmssCacheSource(url, registeredKeys, fetchedVrfStates));
    this.asyncRunner = asyncRunner;
    this.vrfTreeDepth = vrfTreeDepth;
    this.vrfCachedHeight = vrfCachedHeight;
  }

  /**
   * Loads the PQ keys stored next to {@code blsKeystorePath}, encrypted with {@code password}.
   * Returns empty if the validator has no PQ keystore. Never derives keys.
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
    return Optional.of(loadKeyStoreSigner(blsKeystorePath, keystorePath, password, blsPublicKey));
  }

  /**
   * Loads the PQ keys stored next to {@code blsKeystorePath}, like {@link #load(Path, String,
   * BLSPublicKey)}. If there is no PQ keystore and derived keys are enabled, derives the keys from
   * the BLS secret key instead, which {@code blsSecretKey} supplies only then.
   */
  public Optional<XmssSigner> load(
      final Path blsKeystorePath,
      final String password,
      final BLSPublicKey blsPublicKey,
      final Supplier<Bytes32> blsSecretKey) {
    final Path keystorePath = PqKeyFiles.keystorePath(blsKeystorePath);
    if (Files.exists(keystorePath)) {
      return Optional.of(loadKeyStoreSigner(blsKeystorePath, keystorePath, password, blsPublicKey));
    }
    return derivedKeysVrfCacheDirectory.map(
        directory -> deriveSigner(blsSecretKey.get(), blsPublicKey, directory));
  }

  /**
   * Builds the signer for PQ keys derived from {@code blsSecretKey}. The VRF tree is read from
   * {@code vrfCacheDirectory}, or derived and saved there the first time.
   */
  @VisibleForTesting
  XmssSigner deriveSigner(
      final Bytes32 blsSecretKey, final BLSPublicKey blsPublicKey, final Path vrfCacheDirectory) {
    final PqKeyStoreSecrets secrets = PqKeyStoreSecrets.deriveFromBlsSecretKey(blsSecretKey);
    final VrfProver vrfProver =
        loadDerivedVrfProver(secrets.vrfSeed(), blsPublicKey, vrfCacheDirectory);
    final XmssSigningKey signingKey =
        loadSigningKey(
            XmssSecretKey.fromBytes(secrets.xmssSeed()),
            derivedXmssCachePath(vrfCacheDirectory, blsPublicKey),
            Optional.empty());
    LOG.info(
        "Using PQ keys derived from BLS key {}: XMSS public key {}, VRF root {}",
        blsPublicKey,
        signingKey.getPublicKey(),
        vrfProver.getRoot());
    return new LocalXmssSigner(spec, signingKey, vrfProver, leafProtector, asyncRunner);
  }

  /**
   * The VRF prover of a derived key, from the first of: its saved VRF tree, its saved VRF state,
   * its state in the {@code vrf_state_url} file (then saved), or its tree derived in full (then
   * saved).
   */
  private VrfProver loadDerivedVrfProver(
      final Bytes32 vrfSeed, final BLSPublicKey blsPublicKey, final Path vrfCacheDirectory) {
    final Path vrfCachePath = derivedVrfCachePath(vrfCacheDirectory, blsPublicKey);
    if (Files.exists(vrfCachePath)) {
      return readVrfCache(vrfCachePath, vrfSeed);
    }
    final Path vrfStatePath = derivedVrfStatePath(vrfCacheDirectory, blsPublicKey);
    if (Files.exists(vrfStatePath)) {
      return readVrfState(vrfStatePath, vrfSeed);
    }
    final Optional<VrfLeafZeroState> fetchedState = findFetchedVrfState(vrfSeed, blsPublicKey);
    if (fetchedState.isPresent()) {
      saveDerivedFile(vrfStatePath, fetchedState.get().toBytes());
      return fetchedState.get();
    }
    return rebuildVrfCache("derived PQ keys of " + blsPublicKey, vrfCachePath, vrfSeed);
  }

  private Optional<VrfLeafZeroState> findFetchedVrfState(
      final Bytes32 vrfSeed, final BLSPublicKey blsPublicKey) {
    if (vrfStateUrl.isEmpty()) {
      return Optional.empty();
    }
    final Optional<VrfLeafZeroState> state =
        fetchedVrfStates
            .get()
            .flatMap(states -> VrfLeafZeroState.find(vrfSeed, vrfTreeDepth, states));
    if (state.isEmpty()) {
      LOG.warn(
          "No VRF state for the derived PQ keys of {} in {}. Deriving the VRF tree instead.",
          blsPublicKey,
          vrfStateUrl.get());
    }
    return state;
  }

  /**
   * The genesis validators' leanXMSS public keys and VRF roots, or empty if the genesis state can't
   * be read or is not a PQ state (logged).
   */
  @VisibleForTesting
  static Optional<List<RegisteredKey>> readRegisteredKeys(
      final Spec spec, final String genesisState) {
    final List<RegisteredKey> keys;
    try {
      keys =
          ChainDataLoader.loadState(spec, genesisState).getValidators().stream()
              .filter(ValidatorPq.class::isInstance)
              .map(ValidatorPq.class::cast)
              .map(
                  validator ->
                      new RegisteredKey(
                          XmssPublicKey.fromBytes(validator.getXmssPublicKey()),
                          validator.getVrfRoot()))
              .toList();
    } catch (final IOException | RuntimeException e) {
      LOG.warn(
          "Unable to read the genesis validators' PQ keys from {}: {}",
          genesisState,
          e.getMessage());
      return Optional.empty();
    }
    if (keys.isEmpty()) {
      LOG.info("The genesis state {} has no PQ validators", genesisState);
      return Optional.empty();
    }
    return Optional.of(keys);
  }

  /** Fetches the whole {@code vrf_state_url} file, or empty if it can't be read. */
  private static Optional<Bytes> fetchVrfStates(final URI url) {
    LOG.info("Fetching precomputed VRF states from {}", url);
    try {
      final URLConnection connection = url.toURL().openConnection();
      connection.setConnectTimeout(VRF_STATE_CONNECT_TIMEOUT_MILLIS);
      connection.setReadTimeout(VRF_STATE_READ_TIMEOUT_MILLIS);
      try (InputStream in = connection.getInputStream()) {
        final Bytes states = Bytes.wrap(in.readAllBytes());
        LOG.info(
            "Fetched {} VRF states from {}", states.size() / VrfLeafZeroState.SERIALIZED_SIZE, url);
        return Optional.of(states);
      }
    } catch (final IOException | IllegalArgumentException e) {
      LOG.warn("Unable to fetch VRF states from {}: {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  private VrfProver readVrfState(final Path vrfStatePath, final Bytes32 vrfSeed) {
    try {
      return VrfLeafZeroState.fromBytes(
          vrfSeed, vrfTreeDepth, Bytes.wrap(Files.readAllBytes(vrfStatePath)));
    } catch (final IOException | IllegalArgumentException e) {
      throw new InvalidConfigurationException(
          "Invalid VRF state " + vrfStatePath + ": " + e.getMessage(), e);
    }
  }

  /**
   * Saves a VRF tree or state, which is derived data: if it can't be saved, it is derived again on
   * the next start.
   */
  private void saveDerivedFile(final Path path, final Bytes content) {
    try {
      final Path directory = path.toAbsolutePath().getParent();
      Files.createDirectories(directory);
      // Keys load in parallel. Creating an accessor runs a write test through a fixed temporary
      // file name, so the keys share one accessor per directory.
      dataAccessors.computeIfAbsent(directory, SyncDataAccessor::create).syncedWrite(path, content);
    } catch (final IOException | RuntimeException e) {
      LOG.warn("Unable to save {}: {}", path, e.getMessage());
    }
  }

  /**
   * Loads a leanXMSS key from the first of: its trees saved in {@code cachePath}, its pregenerated
   * devnet trees, or its seed. Its trees are saved to {@code cachePath} then, and again whenever a
   * signature builds a new bottom tree, so the next start loads in milliseconds.
   *
   * @param expectedPublicKey the key's declared public key, which cached trees must be of
   */
  private XmssSigningKey loadSigningKey(
      final XmssSecretKey secretKey,
      final Path cachePath,
      final Optional<XmssPublicKey> expectedPublicKey) {
    final XmssTreeCacheFile cacheFile =
        new XmssTreeCacheFile(cachePath, secretKey, this::saveDerivedFile);
    final XmssSigningKey signingKey =
        cacheFile
            .read(expectedPublicKey)
            .or(() -> xmssCacheSource.flatMap(source -> source.load(secretKey, expectedPublicKey)))
            .orElseGet(() -> Xmss.loadSigningKey(secretKey));
    cacheFile.writeIfChanged(signingKey);
    signingKey.onBottomTreeBuilt(() -> cacheFile.writeIfChanged(signingKey));
    return signingKey;
  }

  /** Where the leanXMSS trees of the keys derived for {@code blsPublicKey} are saved. */
  static Path derivedXmssCachePath(final Path vrfCacheDirectory, final BLSPublicKey blsPublicKey) {
    return vrfCacheDirectory.resolve(
        blsPublicKey.toBytesCompressed().toUnprefixedHexString() + XmssTreeCacheFile.SUFFIX);
  }

  /** Where the VRF tree of the keys derived for {@code blsPublicKey} is saved. */
  static Path derivedVrfCachePath(final Path vrfCacheDirectory, final BLSPublicKey blsPublicKey) {
    return vrfCacheDirectory.resolve(
        blsPublicKey.toBytesCompressed().toUnprefixedHexString() + PqKeyFiles.VRF_CACHE_SUFFIX);
  }

  /** Where the VRF state of the keys derived for {@code blsPublicKey} is saved. */
  static Path derivedVrfStatePath(final Path vrfCacheDirectory, final BLSPublicKey blsPublicKey) {
    return vrfCacheDirectory.resolve(
        blsPublicKey.toBytesCompressed().toUnprefixedHexString() + VRF_STATE_SUFFIX);
  }

  private XmssSigner loadKeyStoreSigner(
      final Path blsKeystorePath,
      final Path keystorePath,
      final String password,
      final BLSPublicKey blsPublicKey) {
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
    return createSigner(
        keystorePath, keyStoreData, PqKeyFiles.vrfCachePath(blsKeystorePath), password);
  }

  @VisibleForTesting
  XmssSigner createSigner(
      final Path keystorePath,
      final PqKeyStoreData keyStoreData,
      final Path vrfCachePath,
      final String password) {
    final PqKeyStoreSecrets secrets = decrypt(keystorePath, keyStoreData, password);
    final VrfTree vrfTree = loadVrfTree(keystorePath, keyStoreData, vrfCachePath, secrets);
    final XmssPublicKey declaredPublicKey = XmssPublicKey.fromBytes(keyStoreData.getXmssPubkey());
    final XmssSigningKey signingKey =
        loadSigningKey(
            XmssSecretKey.fromBytes(secrets.xmssSeed()),
            PqKeyFiles.xmssCachePath(vrfCachePath),
            Optional.of(declaredPublicKey));
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
            : rebuildVrfCache("PQ keystore " + keystorePath, vrfCachePath, secrets.vrfSeed());
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
      final String keysDescription, final Path vrfCachePath, final Bytes32 vrfSeed) {
    LOG.warn(
        "VRF cache {} for {} is missing. Rebuilding it from the VRF seed, which can take several"
            + " minutes.",
        vrfCachePath,
        keysDescription);
    final VrfTree vrfTree =
        VrfTree.derive(
            vrfSeed,
            vrfTreeDepth,
            vrfCachedHeight,
            progressLogger("Rebuilding VRF cache " + vrfCachePath));
    saveDerivedFile(vrfCachePath, vrfTree.getCachedNodes());
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
