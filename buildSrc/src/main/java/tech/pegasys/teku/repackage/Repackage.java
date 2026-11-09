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

package tech.pegasys.teku.repackage;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

public class Repackage {
  /**
   * To support reproducible builds, repackage the provided distribution file with modification
   * times set to the provided date. This will replace the original distribution file.
   *
   * @param distFile The distribution file to repackage.
   * @param date The files will have this modification date.
   * @throws IOException
   */
  public static void repackage(final String distFile, final Date date) throws IOException {
    final Path tempDir = Path.of(Files.createTempDirectory("repackage").toFile().getAbsolutePath());

    try {
      final Path distPath = Path.of(distFile);
      final Path tempDistPath = tempDir.resolve(distPath.getFileName());
      final FileTime fileTime = FileTime.fromMillis(date.getTime());

      if (distFile.endsWith(".tar.gz")) {
        repackageTarGz(distPath, fileTime, tempDir);
      } else if (distFile.endsWith(".zip")) {
        repackageZip(distPath, fileTime, tempDir);
      } else {
        throw new IllegalArgumentException("bad distribution");
      }

      Files.setLastModifiedTime(tempDistPath, fileTime);
      Files.copy(tempDistPath, distPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
    } finally {
      try (Stream<Path> walk = Files.walk(tempDir)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }

  private static void repackageTarGz(
      final Path tarDist, final FileTime fileTime, final Path tempDir) throws IOException {
    final Path newTarDist = tempDir.resolve(tarDist.getFileName());
    try (final TarArchiveInputStream source =
            new TarArchiveInputStream(
                new GzipCompressorInputStream(Files.newInputStream(tarDist)));
        final TarArchiveOutputStream target =
            new TarArchiveOutputStream(
                new GzipCompressorOutputStream(Files.newOutputStream(newTarDist)))) {
      target.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      TarArchiveEntry entry;
      while ((entry = source.getNextTarEntry()) != null) {
        entry.setModTime(fileTime);
        target.putArchiveEntry(entry);
        IOUtils.copy(source, target);
        target.closeArchiveEntry();
      }
    }
  }

  private static void repackageZip(final Path zipDist, final FileTime fileTime, final Path tempDir)
      throws IOException {
    final Path newZipDist = tempDir.resolve(zipDist.getFileName());
    try (final ZipFile source = new ZipFile(zipDist.toFile());
        final ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(newZipDist))) {
      final Enumeration<? extends ZipEntry> sourceEntries = source.entries();
      while (sourceEntries.hasMoreElements()) {
        final ZipEntry sourceEntry = sourceEntries.nextElement();
        final ZipEntry outputEntry = new ZipEntry(sourceEntry);
        outputEntry.setLastModifiedTime(fileTime);
        outputEntry.setLastAccessTime(fileTime);
        target.putNextEntry(outputEntry);
        IOUtils.copy(source.getInputStream(sourceEntry), target);
      }
    }
  }
}
