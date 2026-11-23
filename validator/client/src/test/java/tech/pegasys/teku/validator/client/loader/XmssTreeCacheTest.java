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

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.keystore.pq.PqKeyStoreSecrets;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.signatures.xmss.VrfLeafZeroState;
import tech.pegasys.teku.validator.client.loader.DevnetXmssCacheSource.GenesisKey;
import tech.pegasys.teku.validator.client.loader.DevnetXmssCacheSource.RegisteredKey;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSecretKey;
import tech.pegasys.teku.xmss.XmssSigningKey;

/** The on-disk tree cache and the devnet's pregenerated trees. */
class XmssTreeCacheTest {

  // Validator 0 of PQ devnet-0, whose keys are derived from the ethereum-package mnemonic's first
  // BLS key (see PqValidatorKeyLoaderTest)
  private static final XmssSecretKey DEVNET_KEY_0 =
      XmssSecretKey.fromBytes(
          PqKeyStoreSecrets.deriveFromBlsSecretKey(
                  Bytes32.fromHexString(
                      "0x0dce41fa73ae9f6bdfd51df4d422d75eee174553dba5fd450c4437e4ed3fc903"))
              .xmssSeed());
  private static final XmssPublicKey DEVNET_KEY_0_PUBLIC_KEY =
      XmssPublicKey.fromBytes(
          Bytes32.fromHexString(
              "0xd7792e263b94cf0b7a9f70d8374c6d34a600b4247057d4079f810caff711c632"));
  private static final XmssSecretKey OTHER_KEY =
      XmssSecretKey.fromBytes(Bytes32.fromHexString("0x" + "05".repeat(32)));
  // A per-slot leaf of slot 100: in bottom tree 24576, the record's second
  private static final UInt64 SLOT_100_LEAF =
      UInt64.valueOf((1L << 15) + (1L << 29) + (1L << 30) + 100 * 8);
  private static final Bytes32 MESSAGE = Bytes32.fromHexString("0x" + "cd".repeat(32));

  // Lighthouse's copy of the devnet-0 xmss_cache_url record of key 0
  private static Bytes devnetRecord0;
  private static XmssSigningKey devnetKey0;

  private static final int BLOCK_SIZE =
      DevnetXmssCacheSource.BLOCK_RECORDS * DevnetXmssCacheSource.RECORD_SIZE;

  @TempDir Path tempDir;

  @BeforeAll
  static void loadDevnetRecord() throws IOException {
    try (InputStream in = XmssTreeCacheTest.class.getResourceAsStream("devnet0_index0.xmss")) {
      devnetRecord0 = Bytes.wrap(in.readAllBytes());
    }
    devnetKey0 =
        Xmss.loadSigningKey(DEVNET_KEY_0, DEVNET_KEY_0_PUBLIC_KEY, devnetRecord0).orElseThrow();
  }

  @AfterAll
  static void closeKey() {
    devnetKey0.close();
  }

  @Test
  void lighthousesDevnetRecordIsATekuTreeCache() {
    assertThat(devnetRecord0.size()).isEqualTo(DevnetXmssCacheSource.RECORD_SIZE);
    assertThat(XmssSigningKey.cachedBottomTrees(devnetRecord0))
        .contains(DevnetXmssCacheSource.RECORD_BOTTOM_TREES);
    assertThat(devnetKey0.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
    assertThat(
            Xmss.verify(
                DEVNET_KEY_0_PUBLIC_KEY,
                SLOT_100_LEAF,
                MESSAGE,
                devnetKey0.sign(SLOT_100_LEAF, MESSAGE)))
        .isTrue();
  }

  @Test
  void cacheFileLoadsTheTreesItSaved() {
    final List<Path> saved = new ArrayList<>();
    final Path path = tempDir.resolve("key" + XmssTreeCacheFile.SUFFIX);
    final XmssTreeCacheFile file = new XmssTreeCacheFile(path, DEVNET_KEY_0, recording(saved));
    assertThat(file.read(Optional.empty())).isEmpty();

    file.writeIfChanged(devnetKey0);
    // unchanged trees aren't written again
    file.writeIfChanged(devnetKey0);
    assertThat(saved).containsExactly(path);

    try (XmssSigningKey loaded =
        new XmssTreeCacheFile(path, DEVNET_KEY_0, recording(saved))
            .read(Optional.of(DEVNET_KEY_0_PUBLIC_KEY))
            .orElseThrow()) {
      assertThat(loaded.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
      assertThat(XmssSigningKey.cachedBottomTrees(loaded.cacheToBytes()))
          .contains(DevnetXmssCacheSource.RECORD_BOTTOM_TREES);
    }
  }

  @Test
  void cacheFileIsOnlyReadForItsSeedAndExpectedKey() throws IOException {
    final Path path = tempDir.resolve("key" + XmssTreeCacheFile.SUFFIX);
    new XmssTreeCacheFile(path, DEVNET_KEY_0, XmssTreeCacheTest::write).writeIfChanged(devnetKey0);

    // another seed's HMAC key doesn't authenticate it
    assertThat(
            new XmssTreeCacheFile(path, OTHER_KEY, XmssTreeCacheTest::write).read(Optional.empty()))
        .isEmpty();
    // nor is it the key a keystore declares
    assertThat(
            new XmssTreeCacheFile(path, DEVNET_KEY_0, XmssTreeCacheTest::write)
                .read(Optional.of(XmssPublicKey.fromBytes(Bytes32.ZERO))))
        .isEmpty();

    // a changed byte fails authentication
    final MutableBytes tampered = Bytes.wrap(Files.readAllBytes(path)).mutableCopy();
    tampered.set(100, (byte) (tampered.get(100) ^ 1));
    Files.write(path, tampered.toArrayUnsafe());
    assertThat(
            new XmssTreeCacheFile(path, DEVNET_KEY_0, XmssTreeCacheTest::write)
                .read(Optional.empty()))
        .isEmpty();

    Files.write(path, new byte[XmssTreeCacheFile.MAX_FILE_SIZE + 1]);
    assertThat(
            new XmssTreeCacheFile(path, DEVNET_KEY_0, XmssTreeCacheTest::write)
                .read(Optional.empty()))
        .isEmpty();
  }

  @Test
  void devnetSourceReadsAKeysRecordFromAFile() throws IOException {
    final Path cacheFile = tempDir.resolve("devnet.xmss");
    // key 0's record, then a second record
    Files.write(cacheFile, Bytes.concatenate(devnetRecord0, devnetRecord0).toArrayUnsafe());

    for (final URI source : List.of(cacheFile.toUri(), URI.create(cacheFile.toString()))) {
      try (XmssSigningKey key =
          new DevnetXmssCacheSource(source).load(DEVNET_KEY_0, Optional.empty()).orElseThrow()) {
        assertThat(key.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
      }
    }
    // a declared key must be the genesis key
    assertThat(
            new DevnetXmssCacheSource(cacheFile.toUri())
                .load(DEVNET_KEY_0, Optional.of(XmssPublicKey.fromBytes(Bytes32.ZERO))))
        .isEmpty();
    // not a genesis key
    assertThat(new DevnetXmssCacheSource(cacheFile.toUri()).load(OTHER_KEY, Optional.empty()))
        .isEmpty();
  }

  @Test
  void devnetSourceFindsKeysByTheKeyListNextToTheCache() throws IOException {
    // A key set whose second key is devnet key 0, unlike PQ devnet-0's where it is the first
    final Path cacheFile = tempDir.resolve("other-set.xmss");
    Files.write(
        cacheFile,
        Bytes.concatenate(Bytes.random(devnetRecord0.size()), devnetRecord0).toArrayUnsafe());
    Files.write(tempDir.resolve("other-set.bin"), keyList(1).toArrayUnsafe());

    for (final URI source : List.of(cacheFile.toUri(), URI.create(cacheFile.toString()))) {
      try (XmssSigningKey key =
          new DevnetXmssCacheSource(source).load(DEVNET_KEY_0, Optional.empty()).orElseThrow()) {
        assertThat(key.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
      }
    }

    // a key list of the wrong size isn't used, so key 0 is looked for at index 0
    Files.write(tempDir.resolve("other-set.bin"), keyList(1).slice(1).toArrayUnsafe());
    assertThat(new DevnetXmssCacheSource(cacheFile.toUri()).load(DEVNET_KEY_0, Optional.empty()))
        .isEmpty();
  }

  @Test
  void devnetSourceReadsTheKeyListNextToACacheUrl() throws IOException {
    final Bytes file = Bytes.concatenate(Bytes.random(devnetRecord0.size()), devnetRecord0);
    final List<String> ranges = new CopyOnWriteArrayList<>();
    final HttpServer server = rangeServer(file, ranges);
    server.createContext(
        "/cache.bin",
        exchange -> {
          final Bytes keyList = keyList(1);
          exchange.sendResponseHeaders(200, keyList.size());
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(keyList.toArrayUnsafe());
          }
        });
    try {
      final URI source =
          URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/cache.xmss");
      try (XmssSigningKey key =
          new DevnetXmssCacheSource(source).load(DEVNET_KEY_0, Optional.empty()).orElseThrow()) {
        assertThat(key.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
      }
    } finally {
      server.stop(0);
    }
  }

  @Test
  void devnetSourceFindsKeysByTheGenesisValidatorsVrfRootsWithoutAKeyList() throws IOException {
    // A key set whose second key is devnet key 0, with no key list next to its cache
    final Path cacheFile = tempDir.resolve("other-set.xmss");
    Files.write(
        cacheFile,
        Bytes.concatenate(Bytes.random(devnetRecord0.size()), devnetRecord0).toArrayUnsafe());
    final Bytes32 otherRoot = Bytes32.random();
    final Bytes32 key0Root = Bytes32.random();
    final List<RegisteredKey> genesisValidators =
        List.of(
            new RegisteredKey(XmssPublicKey.fromBytes(Bytes32.random()), otherRoot),
            new RegisteredKey(DEVNET_KEY_0_PUBLIC_KEY, key0Root));
    final Bytes vrfStates = Bytes.concatenate(vrfState(otherRoot), vrfState(key0Root));

    try (XmssSigningKey key =
        new DevnetXmssCacheSource(
                cacheFile.toUri(),
                () -> Optional.of(genesisValidators),
                () -> Optional.of(vrfStates))
            .load(DEVNET_KEY_0, Optional.empty())
            .orElseThrow()) {
      assertThat(key.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
    }

    // without the VRF states, PQ devnet-0's keys are used, which put key 0 at index 0
    assertThat(
            new DevnetXmssCacheSource(
                    cacheFile.toUri(), () -> Optional.of(genesisValidators), Optional::empty)
                .load(DEVNET_KEY_0, Optional.empty()))
        .isEmpty();
  }

  @Test
  void devnetSourcePrefersTheKeyListToTheGenesisValidators() throws IOException {
    final Path cacheFile = tempDir.resolve("other-set.xmss");
    Files.write(
        cacheFile,
        Bytes.concatenate(Bytes.random(devnetRecord0.size()), devnetRecord0).toArrayUnsafe());
    Files.write(tempDir.resolve("other-set.bin"), keyList(1).toArrayUnsafe());
    final Bytes32 key0Root = Bytes32.random();

    // the VRF states would put key 0 at index 0, which holds random bytes
    try (XmssSigningKey key =
        new DevnetXmssCacheSource(
                cacheFile.toUri(),
                () -> Optional.of(List.of(new RegisteredKey(DEVNET_KEY_0_PUBLIC_KEY, key0Root))),
                () -> Optional.of(vrfState(key0Root)))
            .load(DEVNET_KEY_0, Optional.empty())
            .orElseThrow()) {
      assertThat(key.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
    }
  }

  @Test
  void genesisKeysAreAtTheIndexOfTheirVrfRoot() {
    final XmssPublicKey keyA = XmssPublicKey.fromBytes(Bytes32.random());
    final XmssPublicKey keyB = XmssPublicKey.fromBytes(Bytes32.random());
    final XmssPublicKey keyC = XmssPublicKey.fromBytes(Bytes32.random());
    final Bytes32 rootA = Bytes32.random();
    final Bytes32 sharedRoot = Bytes32.random();
    final Bytes vrfStates =
        Bytes.concatenate(
            vrfState(sharedRoot),
            vrfState(Bytes32.random()),
            vrfState(rootA),
            vrfState(sharedRoot));

    assertThat(
            DevnetXmssCacheSource.genesisKeysByVrfRoot(
                List.of(
                    new RegisteredKey(keyA, rootA),
                    // two records share its root, so neither identifies it
                    new RegisteredKey(keyB, sharedRoot),
                    // no record has its root
                    new RegisteredKey(keyC, Bytes32.random())),
                vrfStates))
        .containsExactly(Map.entry(keyA.toBytes().slice(16), Optional.of(new GenesisKey(2, keyA))));
  }

  /** A VRF state record of the {@code vrf_state_url} file: the root, then the state. */
  private static Bytes vrfState(final Bytes32 root) {
    return Bytes.concatenate(root, Bytes.random(VrfLeafZeroState.SERIALIZED_SIZE - Bytes32.SIZE));
  }

  @Test
  void keyListIsTheCachesNameWithTheBinExtension() {
    assertThat(DevnetXmssCacheSource.keySetUri(URI.create("https://host/uploads/abc.xmss")))
        .isEqualTo(URI.create("https://host/uploads/abc.bin"));
    assertThat(DevnetXmssCacheSource.keySetUri(URI.create("/pq-keys/abc.xmss")))
        .isEqualTo(URI.create("/pq-keys/abc.bin"));
    assertThat(DevnetXmssCacheSource.keySetUri(URI.create("file:///pq-keys/abc.xmss")))
        .isEqualTo(URI.create("file:///pq-keys/abc.bin"));
    assertThat(DevnetXmssCacheSource.keySetUri(URI.create("/pq.keys/abc")))
        .isEqualTo(URI.create("/pq.keys/abc.bin"));
  }

  /** A key set's .bin file in which devnet key 0 is at {@code index}, after random keys. */
  private static Bytes keyList(final int index) {
    final List<Bytes> records = new ArrayList<>();
    for (int i = 0; i < index; i++) {
      records.add(Bytes.random(DevnetXmssCacheSource.GENESIS_KEY_RECORD_SIZE));
    }
    records.add(
        Bytes.concatenate(
            DEVNET_KEY_0_PUBLIC_KEY.toBytes(),
            Bytes.random(DevnetXmssCacheSource.GENESIS_KEY_RECORD_SIZE - 32)));
    return Bytes.concatenate(records);
  }

  @Test
  void devnetSourceRejectsIncompleteFiles() throws IOException {
    final Path cacheFile = tempDir.resolve("devnet.xmss");
    Files.write(cacheFile, devnetRecord0.slice(0, devnetRecord0.size() - 1).toArrayUnsafe());

    assertThat(new DevnetXmssCacheSource(cacheFile.toUri()).load(DEVNET_KEY_0, Optional.empty()))
        .isEmpty();
    assertThat(
            new DevnetXmssCacheSource(tempDir.resolve("missing").toUri())
                .load(DEVNET_KEY_0, Optional.empty()))
        .isEmpty();
  }

  @Test
  void devnetSourceReadsAKeysRecordWithARangeRequest() throws IOException {
    final List<String> ranges = new CopyOnWriteArrayList<>();
    final HttpServer server = rangeServer(Bytes.concatenate(devnetRecord0, devnetRecord0), ranges);
    try {
      final URI source =
          URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/cache.xmss");
      try (XmssSigningKey key =
          new DevnetXmssCacheSource(source).load(DEVNET_KEY_0, Optional.empty()).orElseThrow()) {
        assertThat(key.getPublicKey()).isEqualTo(DEVNET_KEY_0_PUBLIC_KEY);
      }
      // the first block, of which the file holds two records
      assertThat(ranges).containsExactly("bytes=0-" + (BLOCK_SIZE - 1));

      // a server that ignores the range isn't read in full
      assertThat(
              new DevnetXmssCacheSource(
                      URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/missing"))
                  .load(DEVNET_KEY_0, Optional.empty()))
          .isEmpty();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void devnetSourceReadsEachBlockOfRecordsOnce() throws Exception {
    // 70 records: a full block, then a block of 6
    final int recordCount = DevnetXmssCacheSource.BLOCK_RECORDS + 6;
    final Bytes file = Bytes.random(recordCount * DevnetXmssCacheSource.RECORD_SIZE);
    final List<String> ranges = new CopyOnWriteArrayList<>();
    final HttpServer server = rangeServer(file, ranges);
    try {
      final DevnetXmssCacheSource source =
          new DevnetXmssCacheSource(
              URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/cache.xmss"));

      // keys loading in parallel share their block's request
      final ExecutorService executor = Executors.newFixedThreadPool(8);
      try {
        final List<Future<Bytes>> records = new ArrayList<>();
        for (int index = 0; index < DevnetXmssCacheSource.BLOCK_RECORDS; index++) {
          final int recordIndex = index;
          records.add(executor.submit(() -> source.readRecord(recordIndex)));
        }
        for (int index = 0; index < records.size(); index++) {
          assertThat(records.get(index).get()).isEqualTo(record(file, index));
        }
      } finally {
        executor.shutdownNow();
      }
      assertThat(ranges).containsExactly("bytes=0-" + (BLOCK_SIZE - 1));

      // the last block is as much of it as the file holds
      assertThat(source.readRecord(recordCount - 1)).isEqualTo(record(file, recordCount - 1));
      assertThat(source.readRecord(DevnetXmssCacheSource.BLOCK_RECORDS))
          .isEqualTo(record(file, DevnetXmssCacheSource.BLOCK_RECORDS));
      assertThat(ranges)
          .containsExactly(
              "bytes=0-" + (BLOCK_SIZE - 1), "bytes=" + BLOCK_SIZE + "-" + (2 * BLOCK_SIZE - 1));

      // a record past the end of the file's last block, or of the file
      assertThatThrownBy(() -> source.readRecord(recordCount))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("no record at index");
      assertThatThrownBy(() -> source.readRecord(2 * DevnetXmssCacheSource.BLOCK_RECORDS))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("HTTP 416");
      // nor asked for past the last genesis key's block
      assertThatThrownBy(
              () ->
                  source.readRecord(
                      DevnetXmssCacheSource.KEY_COUNT + DevnetXmssCacheSource.BLOCK_RECORDS))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("no record at offset");
    } finally {
      server.stop(0);
    }
  }

  private static Bytes record(final Bytes file, final int index) {
    return file.slice(index * DevnetXmssCacheSource.RECORD_SIZE, DevnetXmssCacheSource.RECORD_SIZE);
  }

  /**
   * Serves {@code file} at {@code /cache.xmss}, answering a range as a server does: cut at the end
   * of the file, and HTTP 416 if it starts past the end.
   */
  private static HttpServer rangeServer(final Bytes file, final List<String> ranges)
      throws IOException {
    final HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/cache.xmss",
        exchange -> {
          final String range = exchange.getRequestHeaders().getFirst("Range");
          ranges.add(range);
          final Matcher matcher = Pattern.compile("bytes=(\\d+)-(\\d+)").matcher(range);
          if (!matcher.matches()) {
            exchange.sendResponseHeaders(200, file.size());
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(file.toArrayUnsafe());
            }
            return;
          }
          final int start = Integer.parseInt(matcher.group(1));
          if (start >= file.size()) {
            exchange.getResponseHeaders().add("Content-Range", "bytes */" + file.size());
            exchange.sendResponseHeaders(416, -1);
            exchange.close();
            return;
          }
          final int end = Math.min(Integer.parseInt(matcher.group(2)), file.size() - 1);
          exchange
              .getResponseHeaders()
              .add("Content-Range", "bytes " + start + "-" + end + "/" + file.size());
          exchange.sendResponseHeaders(206, end - start + 1);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(file.slice(start, end - start + 1).toArrayUnsafe());
          }
        });
    server.start();
    return server;
  }

  @Test
  void genesisKeysSharingAParameterIdentifyNeither() {
    final Bytes key = devnetRecord0.slice(0, 32);
    assertThat(DevnetXmssCacheSource.genesisKeysByParameter(Bytes.concatenate(key, key)))
        .containsExactly(Map.entry(key.slice(16), Optional.empty()));
  }

  private static BiConsumer<Path, Bytes> recording(final List<Path> saved) {
    return (path, content) -> {
      saved.add(path);
      write(path, content);
    };
  }

  private static void write(final Path path, final Bytes content) {
    try {
      Files.write(path, content.toArrayUnsafe());
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
