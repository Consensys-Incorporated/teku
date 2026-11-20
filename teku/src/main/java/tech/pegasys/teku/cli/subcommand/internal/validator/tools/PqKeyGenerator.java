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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.CipherFunction;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStore;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreData;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreSecrets;
import tech.pegasys.teku.spec.signatures.xmss.PqValidatorPublicData;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;

/**
 * Generates a PQ validator's keys (PQ validator.md, "Initialization"): a random leanXMSS seed and
 * its public key, and a random VRF seed and its VRF tree. It writes the encrypted {@link
 * PqKeyStoreData keystore} and the VRF cache, and returns the public values to register.
 *
 * <p>Deriving the VRF tree hashes about 2^32 leaves on every core, which takes minutes per key.
 */
public class PqKeyGenerator {

  /** EIP-2335's PBKDF2 work factor. */
  public static final int DEFAULT_PBKDF2_ITERATIONS = 262_144;

  private static final int PROGRESS_PERCENT_STEP = 10;

  private final SecureRandom secureRandom;
  private final Function<Bytes32, XmssPublicKey> xmssPublicKeyDeriver;
  private final VrfTreeDeriver vrfTreeDeriver;
  private final int pbkdf2Iterations;
  private final Consumer<String> commandOutput;

  public PqKeyGenerator(final SecureRandom secureRandom, final Consumer<String> commandOutput) {
    this(
        secureRandom,
        seed -> Xmss.derivePublicKey(XmssSecretKey.fromBytes(seed)),
        VrfTree::derive,
        DEFAULT_PBKDF2_ITERATIONS,
        commandOutput);
  }

  @VisibleForTesting
  public PqKeyGenerator(
      final SecureRandom secureRandom,
      final Function<Bytes32, XmssPublicKey> xmssPublicKeyDeriver,
      final VrfTreeDeriver vrfTreeDeriver,
      final int pbkdf2Iterations,
      final Consumer<String> commandOutput) {
    this.secureRandom = secureRandom;
    this.xmssPublicKeyDeriver = xmssPublicKeyDeriver;
    this.vrfTreeDeriver = vrfTreeDeriver;
    this.pbkdf2Iterations = pbkdf2Iterations;
    this.commandOutput = commandOutput;
  }

  /**
   * Generates one validator's PQ keys and writes them to the paths {@code pathsForKey} chooses for
   * its XMSS public key. Refuses to replace existing files.
   *
   * @param label names the validator in progress output
   * @param blsPublicKey the BLS validator the keys belong to, if any; recorded in the keystore
   */
  public GeneratedPqKey generate(
      final String label,
      final Function<XmssPublicKey, PqKeyPaths> pathsForKey,
      final String password,
      final Optional<BLSPublicKey> blsPublicKey) {
    final PqKeyStoreSecrets secrets =
        new PqKeyStoreSecrets(Bytes32.random(secureRandom), Bytes32.random(secureRandom));

    commandOutput.accept(label + ": deriving the XMSS public key");
    final XmssPublicKey xmssPublicKey = xmssPublicKeyDeriver.apply(secrets.xmssSeed());
    final PqKeyPaths paths = pathsForKey.apply(xmssPublicKey);
    paths.checkNoneExist();

    commandOutput.accept(label + ": deriving the VRF tree");
    final VrfTree vrfTree = vrfTreeDeriver.derive(secrets.vrfSeed(), progressReporter(label));
    final PqValidatorPublicData publicData =
        new PqValidatorPublicData(xmssPublicKey, vrfTree.getRoot());

    final PqKeyStoreData keyStoreData =
        PqKeyStore.encrypt(
            secrets,
            xmssPublicKey.toBytes(),
            vrfTree.getRoot(),
            blsPublicKey.map(BLSPublicKey::toBytesCompressed),
            password,
            new Pbkdf2Param(
                32,
                pbkdf2Iterations,
                Pbkdf2PseudoRandomFunction.HMAC_SHA256,
                Bytes32.random(secureRandom)),
            new Cipher(CipherFunction.AES_128_CTR, Bytes.random(16, secureRandom)));
    try {
      // The keystore first: without a cache the loader rebuilds it from the seed
      PqKeyStore.saveToNewFile(paths.keystore(), keyStoreData);
      Files.write(
          paths.vrfCache(),
          vrfTree.getCachedNodes().toArrayUnsafe(),
          StandardOpenOption.CREATE_NEW);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    commandOutput.accept(
        label
            + ": saved PQ keystore "
            + paths.keystore()
            + " (XMSS public key "
            + xmssPublicKey
            + ", VRF root "
            + vrfTree.getRoot()
            + ")");
    return new GeneratedPqKey(publicData, blsPublicKey, paths);
  }

  private VrfTree.DeriveProgressListener progressReporter(final String label) {
    return (derivedNodes, totalNodes) -> {
      final long percent = 100L * derivedNodes / totalNodes;
      final long previousPercent = 100L * (derivedNodes - 1) / totalNodes;
      if (percent / PROGRESS_PERCENT_STEP != previousPercent / PROGRESS_PERCENT_STEP) {
        commandOutput.accept(label + ": VRF tree " + percent + "%");
      }
    };
  }

  /** Derives a VRF tree; a hook so tests can use a small tree. */
  @FunctionalInterface
  public interface VrfTreeDeriver {
    VrfTree derive(Bytes32 seed, VrfTree.DeriveProgressListener progressListener);
  }

  /** Where one validator's PQ keystore and VRF cache are written. */
  public record PqKeyPaths(Path keystore, Path vrfCache) {

    /** Fails if either file exists: an existing keystore may hold seeds already registered. */
    public void checkNoneExist() {
      for (final Path path : new Path[] {keystore, vrfCache}) {
        if (Files.exists(path)) {
          throw new UncheckedIOException(
              new FileAlreadyExistsException(
                  path.toString(), null, "Refusing to replace an existing PQ key file"));
        }
      }
    }
  }

  /** The public results of generating one validator's PQ keys. */
  public record GeneratedPqKey(
      PqValidatorPublicData publicData, Optional<BLSPublicKey> blsPublicKey, PqKeyPaths paths) {}
}
