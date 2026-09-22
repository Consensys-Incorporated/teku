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

package tech.pegasys.teku.ethtests.finder;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Locates the per-config reference test directories ({@code general}, {@code minimal}, {@code
 * mainnet}, {@code bls}, {@code slashing-protection-interchange}).
 *
 * <p>Two layouts are supported:
 *
 * <ul>
 *   <li><b>Archives</b> ({@value #ARCHIVES_DIR_PROPERTY}): a directory of {@code <config>.zip}
 *       files, each containing a single top-level {@code <config>/} directory. Every zip is mounted
 *       lazily through the JDK zip filesystem, so fixtures are read straight from the archive.
 *   <li><b>Directory</b> ({@value #ROOT_DIR_PROPERTY}): a plain directory containing one {@code
 *       <config>/} sub-directory each, e.g. an expanded tarball or locally generated vectors.
 * </ul>
 */
public class ReferenceTestRoot {

  public static final String ARCHIVES_DIR_PROPERTY = "teku.ref-test.archives-dir";
  public static final String ROOT_DIR_PROPERTY = "teku.ref-test.root-dir";

  private static final Map<Path, FileSystem> MOUNTED_ARCHIVES = new ConcurrentHashMap<>();

  private final Optional<Path> archivesDir;
  private final Optional<Path> rootDir;

  private ReferenceTestRoot(final Optional<Path> archivesDir, final Optional<Path> rootDir) {
    this.archivesDir = archivesDir;
    this.rootDir = rootDir;
  }

  public static ReferenceTestRoot ofArchivesDir(final Path archivesDir) {
    return new ReferenceTestRoot(Optional.of(archivesDir), Optional.empty());
  }

  public static ReferenceTestRoot ofDirectory(final Path rootDir) {
    return new ReferenceTestRoot(Optional.empty(), Optional.of(rootDir));
  }

  public static ReferenceTestRoot fromSystemProperties() {
    final String archivesDir = System.getProperty(ARCHIVES_DIR_PROPERTY);
    if (archivesDir != null && !archivesDir.isBlank()) {
      return ofArchivesDir(Path.of(archivesDir));
    }
    final String rootDir = System.getProperty(ROOT_DIR_PROPERTY);
    if (rootDir != null && !rootDir.isBlank()) {
      return ofDirectory(Path.of(rootDir));
    }
    throw new IllegalStateException(
        "Reference tests location not configured. Set -D"
            + ARCHIVES_DIR_PROPERTY
            + "=<dir with <config>.zip archives> (produced by ./gradlew convertRefTests) or -D"
            + ROOT_DIR_PROPERTY
            + "=<dir with <config>/ sub-directories>");
  }

  @MustBeClosed
  public Stream<Path> listSpecDirectories() throws IOException {
    if (rootDir.isPresent()) {
      return Files.list(rootDir.get());
    }
    return Files.list(archivesDir.orElseThrow())
        .filter(path -> path.getFileName().toString().endsWith(".zip"))
        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
        .map(zip -> specDirectoryInArchive(zip, configName(zip)));
  }

  public Path getSpecDirectory(final String configName) {
    if (rootDir.isPresent()) {
      return rootDir.get().resolve(configName);
    }
    final Path zip = archivesDir.orElseThrow().resolve(configName + ".zip");
    if (!Files.isRegularFile(zip)) {
      throw new IllegalStateException(
          "Reference test archive "
              + zip
              + " not found. Run ./gradlew convertRefTests or check -D"
              + ARCHIVES_DIR_PROPERTY);
    }
    return specDirectoryInArchive(zip, configName);
  }

  private static String configName(final Path zip) {
    final String fileName = zip.getFileName().toString();
    return fileName.substring(0, fileName.length() - ".zip".length());
  }

  private static Path specDirectoryInArchive(final Path zip, final String configName) {
    return mount(zip).getPath("/", configName);
  }

  private static FileSystem mount(final Path zip) {
    return MOUNTED_ARCHIVES.computeIfAbsent(
        zip.toAbsolutePath().normalize(),
        path -> {
          try {
            return FileSystems.newFileSystem(path, Map.of());
          } catch (final IOException e) {
            throw new UncheckedIOException("Failed to open reference test archive " + path, e);
          }
        });
  }
}
