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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssConstants;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;
import tech.pegasys.teku.xmss.XmssSigningKey;

/**
 * A leanXMSS key's trees saved on disk, so the key loads in milliseconds rather than rebuilding its
 * top tree and bottom trees, seconds each. Leaf usage is recorded separately, by the leaf
 * protector; this file only speeds up loading.
 *
 * <p>The file is {@code magic ‖ public key ‖ trees ‖ tag}. The trees are checked against the public
 * key when loaded, and the public key and trees are authenticated by an HMAC-SHA256 tag with a key
 * derived from the XMSS seed, so only a file this node wrote for this seed loads.
 */
final class XmssTreeCacheFile {

  private static final Logger LOG = LogManager.getLogger();

  static final String SUFFIX = PqKeyFiles.XMSS_CACHE_SUFFIX;
  private static final Bytes MAGIC =
      Bytes.wrap("TKXMSS\u0000\u0001".getBytes(StandardCharsets.US_ASCII));
  private static final Bytes MAC_KEY_DOMAIN =
      Bytes.wrap("Teku leanXMSS tree cache v1".getBytes(StandardCharsets.US_ASCII));
  private static final String HMAC = "HmacSHA256";
  private static final int TAG_SIZE = 32;
  static final int MAX_FILE_SIZE =
      MAGIC.size() + XmssConstants.PUBLIC_KEY_SIZE + XmssSigningKey.MAX_CACHE_SIZE + TAG_SIZE;

  private final Path path;
  private final XmssSecretKey secretKey;
  private final byte[] macKey;
  private final BiConsumer<Path, Bytes> save;
  // The bottom trees last saved, so a file is only rewritten when they change
  private Optional<List<Integer>> savedBottomTrees = Optional.empty();

  XmssTreeCacheFile(
      final Path path, final XmssSecretKey secretKey, final BiConsumer<Path, Bytes> save) {
    this.path = path;
    this.secretKey = secretKey;
    this.macKey = hmac(secretKey.toBytes().toArrayUnsafe(), MAC_KEY_DOMAIN);
    this.save = save;
  }

  /**
   * The key loaded from this file, or empty if there is no file, or it is unusable (logged), or it
   * holds another public key than {@code expectedPublicKey}.
   */
  synchronized Optional<XmssSigningKey> read(final Optional<XmssPublicKey> expectedPublicKey) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    final Optional<XmssSigningKey> key;
    try {
      key = readKey(expectedPublicKey);
    } catch (final IOException e) {
      LOG.warn("Ignoring unreadable leanXMSS tree cache {}: {}", path, e.getMessage());
      return Optional.empty();
    }
    if (key.isEmpty()) {
      LOG.warn("Ignoring leanXMSS tree cache {}, which is not of this key", path);
    }
    return key;
  }

  private Optional<XmssSigningKey> readKey(final Optional<XmssPublicKey> expectedPublicKey)
      throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_SIZE) {
      return Optional.empty();
    }
    final Bytes file = Bytes.wrap(Files.readAllBytes(path));
    if (file.size() < MAGIC.size() + XmssConstants.PUBLIC_KEY_SIZE + TAG_SIZE
        || !file.slice(0, MAGIC.size()).equals(MAGIC)) {
      return Optional.empty();
    }
    final Bytes content = file.slice(0, file.size() - TAG_SIZE);
    final Bytes tag = file.slice(file.size() - TAG_SIZE);
    if (!MessageDigest.isEqual(hmac(macKey, content), tag.toArrayUnsafe())) {
      return Optional.empty();
    }
    final XmssPublicKey publicKey =
        XmssPublicKey.fromBytes(content.slice(MAGIC.size(), XmssConstants.PUBLIC_KEY_SIZE));
    if (expectedPublicKey.isPresent() && !expectedPublicKey.get().equals(publicKey)) {
      return Optional.empty();
    }
    final Bytes trees = content.slice(MAGIC.size() + XmssConstants.PUBLIC_KEY_SIZE);
    final Optional<XmssSigningKey> key = Xmss.loadSigningKey(secretKey, publicKey, trees);
    savedBottomTrees = key.flatMap(__ -> XmssSigningKey.cachedBottomTrees(trees));
    return key;
  }

  /** Saves {@code key}'s trees, unless the file already holds the same bottom trees. */
  synchronized void writeIfChanged(final XmssSigningKey key) {
    final Bytes trees = key.cacheToBytes();
    final Optional<List<Integer>> bottomTrees = XmssSigningKey.cachedBottomTrees(trees);
    if (bottomTrees.isEmpty() || bottomTrees.equals(savedBottomTrees)) {
      return;
    }
    final Bytes content = Bytes.concatenate(MAGIC, key.getPublicKey().toBytes(), trees);
    save.accept(path, Bytes.concatenate(content, Bytes.wrap(hmac(macKey, content))));
    savedBottomTrees = bottomTrees;
  }

  private static byte[] hmac(final byte[] key, final Bytes message) {
    try {
      final Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return mac.doFinal(message.toArrayUnsafe());
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is unavailable", e);
    }
  }
}
