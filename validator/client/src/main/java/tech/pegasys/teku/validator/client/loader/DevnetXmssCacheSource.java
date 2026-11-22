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
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssConstants;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;
import tech.pegasys.teku.xmss.XmssSigningKey;

/**
 * The pregenerated leanXMSS tree caches of the PQ devnet-0 genesis keys (ethereum-package {@code
 * xmss_cache_url}), which save a node building each key's trees on its first start. The file holds
 * one record per genesis key, in genesis order: the key's top tree and the bottom trees of its
 * attestation leaves and its first per-slot leaves, {@value #RECORD_SIZE} bytes. Only a key's own
 * record is read, with an HTTP range request for a URL.
 *
 * <p>A key's record is found by its public parameter, which is cheap to derive from the seed, among
 * the devnet's genesis public keys, which ship with Teku (ethereum-package d4c9a8ace9 {@code
 * static_files/xmss-keys/b9dec4e1daa17dfe.bin}, the first 32 bytes of each 64-byte record). The
 * record's trees are then checked against that pinned public key, so the file needn't be trusted.
 */
class DevnetXmssCacheSource {

  private static final Logger LOG = LogManager.getLogger();

  static final int KEY_COUNT = 10_000;
  static final int RECORD_SIZE =
      XmssSigningKey.CACHE_TOP_TREE_SIZE + 2 * XmssSigningKey.CACHE_BOTTOM_TREE_SIZE;
  // Bottom tree 0 holds the attestation leaves of the first 2^15 epochs, and 24576 the per-slot
  // leaves of the first 4096 slots
  static final List<Integer> RECORD_BOTTOM_TREES = List.of(0, 24576);
  private static final String GENESIS_KEYS_RESOURCE = "devnet0_xmss_pubkeys.bin";
  private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
  private static final int READ_TIMEOUT_MILLIS = 30_000;
  private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

  private static final Supplier<Map<Bytes, Optional<GenesisKey>>> DEVNET_GENESIS_KEYS =
      Suppliers.memoize(DevnetXmssCacheSource::loadGenesisKeys);

  private final URI source;
  private final Supplier<Map<Bytes, Optional<GenesisKey>>> genesisKeys;

  DevnetXmssCacheSource(final URI source) {
    this(source, DEVNET_GENESIS_KEYS);
  }

  @VisibleForTesting
  DevnetXmssCacheSource(
      final URI source, final Supplier<Map<Bytes, Optional<GenesisKey>>> genesisKeys) {
    this.source = source;
    this.genesisKeys = genesisKeys;
  }

  /** A genesis key, by its position in the genesis keys and so in the cache file. */
  record GenesisKey(int index, XmssPublicKey publicKey) {}

  /**
   * The key loaded from its record, or empty if it isn't a devnet genesis key (or isn't {@code
   * expectedPublicKey}), or its record can't be read or doesn't verify (logged).
   */
  Optional<XmssSigningKey> load(
      final XmssSecretKey secretKey, final Optional<XmssPublicKey> expectedPublicKey) {
    final Optional<GenesisKey> genesisKey =
        genesisKeys.get().getOrDefault(Xmss.publicParameter(secretKey), Optional.empty());
    if (genesisKey.isEmpty()
        || expectedPublicKey.filter(key -> !key.equals(genesisKey.get().publicKey())).isPresent()) {
      return Optional.empty();
    }
    final Bytes record;
    try {
      record = readRecord(genesisKey.get().index());
    } catch (final IOException | RuntimeException e) {
      LOG.warn(
          "Unable to read the pregenerated leanXMSS tree cache of key {} from {}: {}",
          genesisKey.get().publicKey(),
          source,
          e.getMessage());
      return Optional.empty();
    }
    if (!XmssSigningKey.cachedBottomTrees(record).equals(Optional.of(RECORD_BOTTOM_TREES))) {
      LOG.warn(
          "Unexpected bottom trees in the pregenerated leanXMSS tree cache of key {} from {}",
          genesisKey.get().publicKey(),
          source);
      return Optional.empty();
    }
    final Optional<XmssSigningKey> key =
        Xmss.loadSigningKey(secretKey, genesisKey.get().publicKey(), record);
    if (key.isEmpty()) {
      LOG.warn(
          "The pregenerated leanXMSS tree cache of key {} from {} is not of this key",
          genesisKey.get().publicKey(),
          source);
    }
    return key;
  }

  private Bytes readRecord(final int index) throws IOException {
    final long start = (long) index * RECORD_SIZE;
    final String scheme = Optional.ofNullable(source.getScheme()).orElse("file");
    return switch (scheme) {
      case "http", "https" -> readHttpRecord(start);
      case "file" ->
          readFileRecord(
              Path.of(source.getScheme() == null ? source.toString() : source.getPath()), start);
      default -> throw new IOException("Unsupported XMSS cache URL scheme " + scheme);
    };
  }

  private static Bytes readFileRecord(final Path path, final long start) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("not a regular file");
    }
    try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
      if (!isValidLength(file.length(), start)) {
        throw new IOException("no complete record at offset " + start);
      }
      final byte[] record = new byte[RECORD_SIZE];
      file.seek(start);
      file.readFully(record);
      return Bytes.wrap(record);
    }
  }

  private Bytes readHttpRecord(final long start) throws IOException {
    final long end = start + RECORD_SIZE - 1;
    final URLConnection connection = source.toURL().openConnection();
    if (!(connection instanceof HttpURLConnection http)) {
      throw new IOException("not an HTTP connection");
    }
    http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    http.setReadTimeout(READ_TIMEOUT_MILLIS);
    http.setRequestProperty("Range", "bytes=" + start + "-" + end);
    http.setRequestProperty("Accept-Encoding", "identity");
    try {
      if (http.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
        throw new IOException("range request returned HTTP " + http.getResponseCode());
      }
      final Matcher range =
          CONTENT_RANGE.matcher(
              Optional.ofNullable(http.getHeaderField("Content-Range")).orElse(""));
      if (!range.matches()
          || Long.parseLong(range.group(1)) != start
          || Long.parseLong(range.group(2)) != end
          || !isValidLength(Long.parseLong(range.group(3)), start)) {
        throw new IOException("unexpected Content-Range");
      }
      try (InputStream in = http.getInputStream()) {
        final byte[] record = in.readNBytes(RECORD_SIZE);
        if (record.length != RECORD_SIZE) {
          throw new IOException("truncated record");
        }
        return Bytes.wrap(record);
      }
    } finally {
      http.disconnect();
    }
  }

  private static boolean isValidLength(final long length, final long start) {
    return length >= start + RECORD_SIZE
        && length <= (long) KEY_COUNT * RECORD_SIZE
        && length % RECORD_SIZE == 0;
  }

  /** The genesis keys by public parameter; a parameter two keys share identifies neither. */
  @VisibleForTesting
  static Map<Bytes, Optional<GenesisKey>> genesisKeysByParameter(final Bytes publicKeys) {
    final Map<Bytes, Optional<GenesisKey>> keys = new HashMap<>();
    for (int index = 0; index < publicKeys.size() / XmssConstants.PUBLIC_KEY_SIZE; index++) {
      final XmssPublicKey publicKey =
          XmssPublicKey.fromBytes(
              publicKeys.slice(
                  index * XmssConstants.PUBLIC_KEY_SIZE, XmssConstants.PUBLIC_KEY_SIZE));
      final Bytes parameter =
          publicKey.toBytes().slice(XmssConstants.PUBLIC_KEY_SIZE - Xmss.PUBLIC_PARAMETER_SIZE);
      final GenesisKey key = new GenesisKey(index, publicKey);
      keys.merge(parameter, Optional.of(key), (existing, added) -> Optional.empty());
    }
    return keys;
  }

  private static Map<Bytes, Optional<GenesisKey>> loadGenesisKeys() {
    try (InputStream in = DevnetXmssCacheSource.class.getResourceAsStream(GENESIS_KEYS_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + GENESIS_KEYS_RESOURCE);
      }
      return genesisKeysByParameter(Bytes.wrap(in.readAllBytes()));
    } catch (final IOException e) {
      throw new IllegalStateException("Unable to read " + GENESIS_KEYS_RESOURCE, e);
    }
  }
}
