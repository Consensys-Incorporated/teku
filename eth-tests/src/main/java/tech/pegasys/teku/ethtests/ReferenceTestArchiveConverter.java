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

package tech.pegasys.teku.ethtests;

import com.google.common.base.Splitter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Streams a reference test {@code tar.gz} into a zip without expanding it on disk.
 *
 * <p>A gzip stream cannot be read at random, but a zip can: the JDK's {@code jdk.zipfs} provider
 * mounts the result as a {@link java.nio.file.FileSystem} so the reference tests read fixtures
 * straight out of the archive instead of from hundreds of thousands of expanded files.
 *
 * <p>Every entry is deflated at the fastest level: snappy leaves enough on the table that this
 * shrinks the mainnet archive about 3x compared to storing {@code .ssz_snappy} entries as-is.
 *
 * <p>Usage: {@code <input.tar.gz> <output.zip> [--strip-prefix p] [--add-prefix p] [--include
 * regex] [--flatten]}
 */
public class ReferenceTestArchiveConverter {

  private static final int TAR_BLOCK_SIZE = 512;
  private static final int BUFFER_SIZE = 1 << 20;

  /**
   * @param stripPrefix leading path removed from every entry when present, e.g. {@code tests/}
   * @param addPrefix leading path prepended to every entry, e.g. {@code bls/}
   * @param includeRegex when non-null only entries whose full tar path matches are kept
   * @param flatten when true only the file name of each entry is kept
   */
  public record Options(
      String stripPrefix, String addPrefix, String includeRegex, boolean flatten) {
    public static final Options DEFAULT = new Options(null, null, null, false);
  }

  public static void main(final String[] args) throws IOException {
    if (args.length < 2) {
      throw new IllegalArgumentException(
          "Usage: <input.tar.gz> <output.zip> [--strip-prefix p] [--add-prefix p] [--include regex] [--flatten]");
    }
    String stripPrefix = null;
    String addPrefix = null;
    String includeRegex = null;
    boolean flatten = false;
    for (int i = 2; i < args.length; i++) {
      switch (args[i]) {
        case "--strip-prefix" -> stripPrefix = valueOf(args, i++);
        case "--add-prefix" -> addPrefix = valueOf(args, i++);
        case "--include" -> includeRegex = valueOf(args, i++);
        case "--flatten" -> flatten = true;
        default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
      }
    }
    convert(
        Path.of(args[0]),
        Path.of(args[1]),
        new Options(stripPrefix, addPrefix, includeRegex, flatten));
  }

  private static String valueOf(final String[] args, final int flagIndex) {
    if (flagIndex + 1 >= args.length) {
      throw new IllegalArgumentException("Missing value for " + args[flagIndex]);
    }
    return args[flagIndex + 1];
  }

  public static void convert(final Path tarGz, final Path zip, final Options options)
      throws IOException {
    final Pattern include =
        options.includeRegex() == null ? null : Pattern.compile(options.includeRegex());
    final byte[] header = new byte[TAR_BLOCK_SIZE];
    String gnuLongName = null;
    String paxPath = null;
    long entries = 0;
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(tarGz), BUFFER_SIZE);
        DataInputStream tar = new DataInputStream(new GZIPInputStream(raw, BUFFER_SIZE));
        ZipOutputStream out =
            new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zip), BUFFER_SIZE))) {
      out.setLevel(Deflater.BEST_SPEED);
      while (true) {
        try {
          tar.readFully(header);
        } catch (final EOFException e) {
          break;
        }
        if (isEndOfArchive(header)) {
          break;
        }
        final long size = octal(header, 124, 12);
        final byte typeFlag = header[156];
        String name = string(header, 0, 100);
        final String prefix = string(header, 345, 155);
        if (!prefix.isEmpty()) {
          name = prefix + "/" + name;
        }
        final byte[] data = readEntry(tar, size);
        switch (typeFlag) {
          case 'L' -> {
            gnuLongName = string(data, 0, data.length);
            continue;
          }
          case 'x' -> {
            paxPath = paxPath(data);
            continue;
          }
          case 'g', '5', '1', '2' -> {
            // global pax header, directory, hard link, symlink: nothing to copy
            gnuLongName = null;
            paxPath = null;
            continue;
          }
          default -> {}
        }
        if (gnuLongName != null) {
          name = gnuLongName;
          gnuLongName = null;
        }
        if (paxPath != null) {
          name = paxPath;
          paxPath = null;
        }
        if (name.startsWith("./")) {
          name = name.substring(2);
        }
        if (include != null && !include.matcher(name).matches()) {
          continue;
        }
        final String entryName = entryName(name, options);
        out.putNextEntry(zipEntry(entryName));
        out.write(data);
        out.closeEntry();
        entries++;
      }
    }
    System.out.printf("Converted %s -> %s (%d entries)%n", tarGz.getFileName(), zip, entries);
  }

  private static String entryName(final String tarName, final Options options) {
    String name = tarName;
    if (options.flatten()) {
      name = name.substring(name.lastIndexOf('/') + 1);
    } else if (options.stripPrefix() != null && name.startsWith(options.stripPrefix())) {
      name = name.substring(options.stripPrefix().length());
    }
    if (options.addPrefix() != null) {
      name = options.addPrefix() + name;
    }
    return name;
  }

  private static ZipEntry zipEntry(final String name) {
    final ZipEntry entry = new ZipEntry(name);
    entry.setMethod(ZipEntry.DEFLATED);
    return entry;
  }

  private static byte[] readEntry(final DataInputStream tar, final long size) throws IOException {
    if (size > Integer.MAX_VALUE) {
      throw new IOException("Tar entry too large: " + size);
    }
    final byte[] data = new byte[(int) size];
    tar.readFully(data);
    final long padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
    tar.skipNBytes(padding);
    return data;
  }

  private static String paxPath(final byte[] data) {
    final String records = new String(data, StandardCharsets.UTF_8);
    for (final String record : Splitter.on('\n').split(records)) {
      final int space = record.indexOf(' ');
      if (space < 0) {
        continue;
      }
      final String keyValue = record.substring(space + 1);
      if (keyValue.startsWith("path=")) {
        return keyValue.substring("path=".length());
      }
    }
    return null;
  }

  private static boolean isEndOfArchive(final byte[] header) {
    for (final byte b : header) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private static String string(final byte[] bytes, final int offset, final int length) {
    int end = offset;
    while (end < offset + length && bytes[end] != 0) {
      end++;
    }
    return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
  }

  private static long octal(final byte[] header, final int offset, final int length)
      throws IOException {
    final String value = string(header, offset, length).trim();
    try {
      return value.isEmpty() ? 0 : Long.parseLong(value, 8);
    } catch (final NumberFormatException e) {
      throw new IOException("Corrupt tar header: invalid octal field '" + value + "'", e);
    }
  }
}
