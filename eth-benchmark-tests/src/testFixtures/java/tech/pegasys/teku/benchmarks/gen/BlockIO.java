/*
 * Copyright Consensys Software Inc., 2026
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

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

/** Utility class to read/write SSZ serialized blocks */
public class BlockIO {

  public static class Reader
      implements AutoCloseable, Supplier<SignedBeaconBlock>, Iterable<SignedBeaconBlock> {

    private final ObjectInputStream inputStream;
    private final Spec spec;

    Reader(final ObjectInputStream inputStream, final Spec spec) {
      this.inputStream = inputStream;
      this.spec = spec;
    }

    @Override
    public void close() throws Exception {
      inputStream.close();
    }

    @Override
    public SignedBeaconBlock get() {
      try {
        int size = inputStream.readInt();
        byte[] bytes = new byte[size];
        inputStream.readFully(bytes);
        return spec.deserializeSignedBeaconBlock(Bytes.wrap(bytes));
      } catch (Exception e) {
        return null;
      }
    }

    @NotNull
    @Override
    public Iterator<SignedBeaconBlock> iterator() {
      return Utils.fromSupplier(this);
    }

    @SuppressWarnings("EmptyCatch")
    public List<SignedBeaconBlock> readAll(final int limit) {
      try {
        return StreamSupport.stream(spliterator(), false).limit(limit).collect(Collectors.toList());
      } finally {
        try {
          close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  public static class Writer implements AutoCloseable, Consumer<SignedBeaconBlock> {
    private final ObjectOutputStream outputStream;

    Writer(final ObjectOutputStream outputStream) {
      this.outputStream = outputStream;
    }

    @Override
    public void close() throws Exception {
      outputStream.close();
    }

    @Override
    public void accept(final SignedBeaconBlock block) {
      try {
        Bytes bytes = block.sszSerialize();
        outputStream.writeInt(bytes.size());
        outputStream.write(bytes.toArrayUnsafe());
        outputStream.flush();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Writer createFileWriter(final String outFile) {
    try {
      return new Writer(
          new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(outFile))));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Reader createResourceReader(final Spec spec, final String resourcePath) {
    try {
      return createReader(
          spec, BlockIO.class.getResourceAsStream(resourcePath), resourcePath.endsWith(".gz"));
    } catch (Exception e) {
      throw new RuntimeException("Error opening resource " + resourcePath, e);
    }
  }

  public static List<byte[]> readResourceBytes(final String resourcePath, final int limit) {
    try (ObjectInputStream inputStream =
        createObjectInputStream(
            BlockIO.class.getResourceAsStream(resourcePath), resourcePath.endsWith(".gz"))) {
      return readBytes(inputStream, limit);
    } catch (IOException e) {
      throw new RuntimeException("Error reading resource " + resourcePath, e);
    }
  }

  public static Reader createFileReader(final Spec spec, final String inFile) {
    try {
      return createReader(spec, new FileInputStream(inFile), inFile.endsWith(".gz"));
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error opening file " + inFile, e);
    }
  }

  public static Reader createReader(
      final Spec spec, final InputStream inputStream, final boolean gzipped) {
    try {
      return new Reader(createObjectInputStream(inputStream, gzipped), spec);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static ObjectInputStream createObjectInputStream(
      final InputStream inputStream, final boolean gzipped) throws IOException {
    if (inputStream == null) {
      throw new IOException("Input stream was not available");
    }
    return new ObjectInputStream(gzipped ? new GZIPInputStream(inputStream) : inputStream);
  }

  private static List<byte[]> readBytes(final ObjectInputStream inputStream, final int limit)
      throws IOException {
    final List<byte[]> blocks = new ArrayList<>();
    while (blocks.size() < limit) {
      final int size;
      try {
        size = inputStream.readInt();
      } catch (EOFException e) {
        break;
      }
      final byte[] bytes = new byte[size];
      inputStream.readFully(bytes);
      blocks.add(bytes);
    }
    return blocks;
  }
}
