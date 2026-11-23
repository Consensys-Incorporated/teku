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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * record is read from a file.
 *
 * <p>A URL is read with HTTP range requests of {@value #BLOCK_RECORDS} records each. The devnet's
 * server takes almost a second to answer any range request, whether for one record or a thousand,
 * so a request per key made a node's first start take minutes. A node's keys are usually
 * consecutive genesis keys, so keys loading in parallel share the block of records they fall in,
 * and a few recent blocks are kept for the keys that follow.
 *
 * <p>A key's record is found by its public parameter, which is cheap to derive from the seed, among
 * the genesis public keys of the cache's key set. Those are read from the key set's {@code .bin}
 * file next to the cache ({@code <name>.bin} for {@code <name>.xmss}), as Lighthouse does: one
 * 64-byte record per key in the cache's order, the key's XMSS public key first. Without that file,
 * the keys of PQ devnet-0's key set are used, which ship with Teku (ethereum-package d4c9a8ace9
 * {@code static_files/xmss-keys/b9dec4e1daa17dfe.bin}). The record's trees are then checked against
 * the public key, so the cache needn't be trusted.
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
  // A record of a key set's .bin file: the XMSS public key, then the VRF root
  static final int GENESIS_KEY_RECORD_SIZE = 64;
  private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
  private static final int READ_TIMEOUT_MILLIS = 30_000;
  private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
  // 64 records are about 0.9 MiB, which the devnet's server sends in about the time it takes to
  // answer a request for one
  static final int BLOCK_RECORDS = 64;
  // Enough for the keys loading in parallel to each have their block, and to spare
  private static final int MAX_CACHED_BLOCKS = 16;

  private static final Supplier<Map<Bytes, Optional<GenesisKey>>> DEVNET_GENESIS_KEYS =
      Suppliers.memoize(DevnetXmssCacheSource::loadGenesisKeys);

  private final URI source;
  private final Supplier<Map<Bytes, Optional<GenesisKey>>> genesisKeys;
  private final AtomicBoolean loggedUnknownKey = new AtomicBoolean();
  // Concurrent loads of one block wait for a single request. A failed request isn't kept, so the
  // next key in its block tries again.
  private final LoadingCache<Long, Bytes> httpBlocks =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHED_BLOCKS)
          .softValues()
          .build(
              new CacheLoader<>() {
                @Override
                public Bytes load(final Long block) throws IOException {
                  return readHttpBlock(block);
                }
              });

  DevnetXmssCacheSource(final URI source) {
    this.source = source;
    this.genesisKeys =
        Suppliers.memoize(
            () ->
                readKeySetPublicKeys(source)
                    .map(DevnetXmssCacheSource::genesisKeysByParameter)
                    .orElseGet(DEVNET_GENESIS_KEYS));
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
    if (genesisKey.isEmpty()) {
      if (!loggedUnknownKey.getAndSet(true)) {
        LOG.warn(
            "Key with public parameter {} is not one of the keys of the leanXMSS tree cache {}, so"
                + " its trees are built instead, which takes seconds per key. Other keys not in the"
                + " cache are logged at debug.",
            Xmss.publicParameter(secretKey),
            source);
      } else {
        LOG.debug(
            "Key with public parameter {} is not one of the keys of the leanXMSS tree cache {}",
            Xmss.publicParameter(secretKey),
            source);
      }
      return Optional.empty();
    }
    if (expectedPublicKey.filter(key -> !key.equals(genesisKey.get().publicKey())).isPresent()) {
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

  @VisibleForTesting
  Bytes readRecord(final int index) throws IOException {
    final String scheme = Optional.ofNullable(source.getScheme()).orElse("file");
    return switch (scheme) {
      case "http", "https" -> readHttpRecord(index);
      case "file" ->
          readFileRecord(
              Path.of(source.getScheme() == null ? source.toString() : source.getPath()),
              (long) index * RECORD_SIZE);
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

  private Bytes readHttpRecord(final int index) throws IOException {
    final Bytes block;
    try {
      block = httpBlocks.get((long) index / BLOCK_RECORDS);
    } catch (final ExecutionException | UncheckedExecutionException e) {
      throw e.getCause() instanceof IOException cause ? cause : new IOException(e.getCause());
    }
    final int offset = (index % BLOCK_RECORDS) * RECORD_SIZE;
    if (block.size() < offset + RECORD_SIZE) {
      throw new IOException("no record at index " + index);
    }
    // A copy, so the key doesn't keep the whole block
    return block.slice(offset, RECORD_SIZE).copy();
  }

  /**
   * Reads the records of {@code block}, or as many as the file has: a server answers a range that
   * runs past the end of the file with the part that doesn't.
   */
  private Bytes readHttpBlock(final long block) throws IOException {
    final long start = block * BLOCK_RECORDS * RECORD_SIZE;
    final long maxEnd = (long) KEY_COUNT * RECORD_SIZE - 1;
    final long requestedEnd = Math.min(start + (long) BLOCK_RECORDS * RECORD_SIZE - 1, maxEnd);
    if (start > maxEnd) {
      throw new IOException("no record at offset " + start);
    }
    final URLConnection connection = source.toURL().openConnection();
    if (!(connection instanceof HttpURLConnection http)) {
      throw new IOException("not an HTTP connection");
    }
    http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    http.setReadTimeout(READ_TIMEOUT_MILLIS);
    http.setRequestProperty("Range", "bytes=" + start + "-" + requestedEnd);
    http.setRequestProperty("Accept-Encoding", "identity");
    try {
      if (http.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
        throw new IOException("range request returned HTTP " + http.getResponseCode());
      }
      final Matcher range =
          CONTENT_RANGE.matcher(
              Optional.ofNullable(http.getHeaderField("Content-Range")).orElse(""));
      if (!range.matches()) {
        throw new IOException("unexpected Content-Range");
      }
      final long length = Long.parseLong(range.group(3));
      final long end = Long.parseLong(range.group(2));
      if (Long.parseLong(range.group(1)) != start
          || !isValidLength(length, start)
          || end != Math.min(requestedEnd, length - 1)) {
        throw new IOException("unexpected Content-Range");
      }
      final int size = Math.toIntExact(end - start + 1);
      try (InputStream in = http.getInputStream()) {
        final byte[] records = in.readNBytes(size);
        if (records.length != size) {
          throw new IOException("truncated response");
        }
        return Bytes.wrap(records);
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

  /**
   * The public keys of the cache's key set, concatenated, from the {@code .bin} file next to the
   * cache, or empty if there is none that can be read (logged).
   */
  private static Optional<Bytes> readKeySetPublicKeys(final URI source) {
    final URI keySet = keySetUri(source);
    final Bytes records;
    try {
      records = readKeySet(keySet);
    } catch (final IOException | RuntimeException e) {
      LOG.info(
          "No key list for the leanXMSS tree cache {} at {} ({}). Using PQ devnet-0's genesis keys,"
              + " which ship with Teku.",
          source,
          keySet,
          e.getMessage());
      return Optional.empty();
    }
    if (records.isEmpty()
        || records.size() % GENESIS_KEY_RECORD_SIZE != 0
        || records.size() > KEY_COUNT * GENESIS_KEY_RECORD_SIZE) {
      LOG.warn(
          "Invalid key list {} of {} bytes for the leanXMSS tree cache {}. Using PQ devnet-0's"
              + " genesis keys, which ship with Teku.",
          keySet,
          records.size(),
          source);
      return Optional.empty();
    }
    final int count = records.size() / GENESIS_KEY_RECORD_SIZE;
    final Bytes[] publicKeys = new Bytes[count];
    for (int i = 0; i < count; i++) {
      publicKeys[i] = records.slice(i * GENESIS_KEY_RECORD_SIZE, XmssConstants.PUBLIC_KEY_SIZE);
    }
    LOG.info(
        "Finding keys in the leanXMSS tree cache {} by the {} keys of {}", source, count, keySet);
    return Optional.of(Bytes.concatenate(publicKeys));
  }

  /**
   * The key set's {@code .bin} file next to the cache: {@code <name>.bin} for {@code <name>.xmss}.
   */
  @VisibleForTesting
  static URI keySetUri(final URI source) {
    final String path = Optional.ofNullable(source.getPath()).orElse("");
    final int fileNameStart = path.lastIndexOf('/') + 1;
    final int extension = path.lastIndexOf('.');
    final String keySetPath =
        (extension > fileNameStart ? path.substring(0, extension) : path) + ".bin";
    try {
      return new URI(source.getScheme(), source.getAuthority(), keySetPath, null, null);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Invalid XMSS cache URL " + source, e);
    }
  }

  private static Bytes readKeySet(final URI keySet) throws IOException {
    final int maxSize = KEY_COUNT * GENESIS_KEY_RECORD_SIZE;
    final String scheme = Optional.ofNullable(keySet.getScheme()).orElse("file");
    final InputStream in;
    switch (scheme) {
      case "http", "https" -> {
        final URLConnection connection = keySet.toURL().openConnection();
        if (!(connection instanceof HttpURLConnection http)) {
          throw new IOException("not an HTTP connection");
        }
        http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        http.setReadTimeout(READ_TIMEOUT_MILLIS);
        http.setRequestProperty("Accept-Encoding", "identity");
        if (http.getResponseCode() != HttpURLConnection.HTTP_OK) {
          http.disconnect();
          throw new IOException("HTTP " + http.getResponseCode());
        }
        in = http.getInputStream();
      }
      case "file" -> {
        final Path path = Path.of(keySet.getPath());
        if (!Files.isRegularFile(path)) {
          throw new IOException("no such file");
        }
        in = Files.newInputStream(path);
      }
      default -> throw new IOException("Unsupported URL scheme " + scheme);
    }
    try (in) {
      // One byte more than a valid list, to tell a list that is too long
      return Bytes.wrap(in.readNBytes(maxSize + 1));
    }
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
