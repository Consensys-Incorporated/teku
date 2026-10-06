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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

/** Minimal tar.gz writer for tests: ustar headers plus GNU long-name and pax path records. */
final class TarGzTestWriter implements AutoCloseable {

  private final OutputStream out;

  TarGzTestWriter(final Path target) throws IOException {
    this.out = new GZIPOutputStream(Files.newOutputStream(target));
  }

  void directory(final String name) throws IOException {
    writeHeader(name, 0, (byte) '5');
  }

  void file(final String name, final byte[] content) throws IOException {
    writeHeader(name, content.length, (byte) '0');
    writeData(content);
  }

  /** Writes the name through a GNU "././@LongLink" entry, as GNU tar does for names > 100 chars. */
  void fileWithGnuLongName(final String name, final byte[] content) throws IOException {
    final byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
    writeHeader("././@LongLink", nameBytes.length, (byte) 'L');
    writeData(nameBytes);
    file(name.substring(0, Math.min(name.length(), 100)), content);
  }

  /** Writes the name through a pax extended header, as python tarfile does by default. */
  void fileWithPaxPath(final String name, final byte[] content) throws IOException {
    final String body = " path=" + name + "\n";
    // pax records are "<len> <key>=<value>\n" where len counts the whole record including itself
    int len = body.length() + 1;
    if (String.valueOf(len).length() > 1) {
      len = body.length() + String.valueOf(len).length();
    }
    final byte[] record = (len + body).getBytes(StandardCharsets.UTF_8);
    writeHeader("./PaxHeaders/x", record.length, (byte) 'x');
    writeData(record);
    file(name.substring(0, Math.min(name.length(), 100)), content);
  }

  @Override
  public void close() throws IOException {
    out.write(new byte[1024]);
    out.close();
  }

  private void writeHeader(final String name, final long size, final byte typeFlag)
      throws IOException {
    final byte[] header = new byte[512];
    put(header, 0, 100, name);
    put(header, 100, 8, "0000644");
    put(header, 108, 8, "0000000");
    put(header, 116, 8, "0000000");
    put(header, 124, 12, String.format("%011o", size));
    put(header, 136, 12, "00000000000");
    Arrays.fill(header, 148, 156, (byte) ' ');
    header[156] = typeFlag;
    put(header, 257, 6, "ustar");
    put(header, 263, 2, "00");
    long checksum = 0;
    for (final byte b : header) {
      checksum += b & 0xff;
    }
    put(header, 148, 8, String.format("%06o", checksum) + "\0 ");
    out.write(header);
  }

  private void writeData(final byte[] content) throws IOException {
    out.write(content);
    final int padding = (512 - (content.length % 512)) % 512;
    out.write(new byte[padding]);
  }

  private static void put(final byte[] header, final int offset, final int length, final String s) {
    final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
  }
}
