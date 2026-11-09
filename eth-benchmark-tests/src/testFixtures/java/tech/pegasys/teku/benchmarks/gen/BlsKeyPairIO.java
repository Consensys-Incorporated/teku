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

package tech.pegasys.teku.benchmarks.gen;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;

/**
 * Given a file or resource reads BLS {@link BLSKeyPair} instances from it.
 *
 * <p>Supports inputs in GZIP format, requires ".gz" suffix.
 */
public class BlsKeyPairIO {

  public static class Reader implements AutoCloseable, Supplier<BLSKeyPair>, Iterable<BLSKeyPair> {
    private final BufferedReader reader;
    private int pairsToRead = Integer.MAX_VALUE;

    public Reader(final BufferedReader reader) {
      this.reader = reader;
    }

    public Reader withLimit(final int limit) {
      pairsToRead = limit;
      return this;
    }

    @Override
    public void close() throws Exception {
      reader.close();
    }

    @NotNull
    @Override
    public Iterator<BLSKeyPair> iterator() {
      return Utils.fromSupplier(this);
    }

    @SuppressWarnings("EmptyCatch")
    public List<BLSKeyPair> readAll(final int limit) {
      try {
        return StreamSupport.stream(withLimit(limit).spliterator(), false)
            .collect(Collectors.toList());
      } finally {
        try {
          close();
        } catch (Exception ignored) {
        }
      }
    }

    /**
     * Fetches next pair from the source.
     *
     * @return a key pair.
     * @throws RuntimeException if any error occurred during interaction with underlying source.
     */
    @Override
    public BLSKeyPair get() {
      if (pairsToRead == 0) {
        return null;
      }
      try {
        String parts = reader.readLine();
        if (parts == null) {
          return null;
        }
        int delimiterPos = parts.indexOf(":");
        if (delimiterPos < 0) {
          throw new RuntimeException("Invalid input: " + parts);
        }

        BLSPublicKey blsPublicKey =
            BLSPublicKey.fromBytesCompressed(
                Bytes48.fromHexString(parts.substring(delimiterPos + 1)));
        Bytes32 privateBytes = Bytes32.fromHexString(parts.substring(0, delimiterPos));
        BLSSecretKey blsSecretKey = BLSSecretKey.fromBytes(privateBytes);

        pairsToRead--;
        return new BLSKeyPair(blsPublicKey, blsSecretKey);
      } catch (IOException e) {
        throw new RuntimeException(e.getMessage());
      }
    }
  }

  public static class Writer implements AutoCloseable {
    private final Supplier<BLSKeyPair> generator;
    private final BufferedWriter writer;

    private Writer(final Supplier<BLSKeyPair> generator, final BufferedWriter writer) {
      this.generator = generator;
      this.writer = writer;
    }

    public void write(final int count) throws IOException {
      for (int i = 0; i < count; i++) {
        BLSKeyPair blsKeyPair = generator.get();
        writer
            .append(blsKeyPair.getSecretKey().toBytes().toHexString())
            .append(':')
            .append(blsKeyPair.getPublicKey().toBytesCompressed().toHexString())
            .append('\n');
      }
    }

    @Override
    public void close() throws Exception {
      writer.close();
    }
  }

  /**
   * Instantiates reader with predefined key pair source located in {@code /bls-key-pairs/} resource
   * folder.
   *
   * @return an instance of key pair reader.
   */
  public static Reader createReaderWithDefaultSource() {
    return createReaderForResource("/bls-key-pairs/bls-pairs-1m-seed_1.gz");
  }

  public static Reader createReaderForResource(final String resourceName) {
    try {
      return createReaderFromStream(
          BlsKeyPairIO.class.getResourceAsStream(resourceName), resourceName.endsWith(".gz"));
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public static Reader createReaderForFile(final File file) {
    try {
      return createReaderFromStream(new FileInputStream(file), file.getName().endsWith(".gz"));
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public static Reader createReaderFromStream(final InputStream input, final boolean gzipped)
      throws IOException {
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(gzipped ? new GZIPInputStream(input) : input, UTF_8));
    return new Reader(reader);
  }

  public static Writer createWriter(final File outFile, final Supplier<BLSKeyPair> generator) {
    try {
      return new Writer(generator, new BufferedWriter(new FileWriter(outFile, UTF_8)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
