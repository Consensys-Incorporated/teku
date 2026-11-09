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

package tech.pegasys.teku.storage.archive.filesystem;

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;

public class FileSystemBlobSidecarsArchiver implements BlobSidecarsArchiver {

  private static final String INDEX_FILE_SUFFIX = "index.dat";
  private static final long INDEX_FILE_SLOT_RANGE_SIZE = 100_000;

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Path baseDirectory;

  private final Map<SpecMilestone, DeserializableTypeDefinition<List<BlobSidecar>>>
      milestoneToTypeDefinitionCache = new HashMap<>();

  public FileSystemBlobSidecarsArchiver(final Spec spec, final Path baseDirectory) {
    this.spec = spec;
    this.baseDirectory = baseDirectory;
  }

  @Override
  public void archive(
      final SlotAndBlockRoot slotAndBlockRoot, final List<BlobSidecar> blobSidecars) {
    final Path archivePath = resolveArchivePath(slotAndBlockRoot.getBlockRoot());

    if (Files.exists(archivePath)) {
      LOG.error(
          "Failed to archive blob sidecars for {}. File exists: {}", slotAndBlockRoot, archivePath);
      return;
    }

    try {
      Files.createDirectories(archivePath.getParent());
    } catch (final IOException __) {
      LOG.error(
          "Failed to archive blob sidecars for {}. Could not create directories: {}",
          slotAndBlockRoot,
          archivePath.getParent());
      return;
    }

    final Path indexFile = resolveIndexFile(slotAndBlockRoot.getSlot());

    try (final OutputStream output = Files.newOutputStream(archivePath);
        final BufferedWriter indexWriter =
            Files.newBufferedWriter(
                indexFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
      if (blobSidecars.isEmpty()) {
        // empty list
        output.write("[]".getBytes(StandardCharsets.UTF_8));
      } else {
        writeBlobSidecars(output, slotAndBlockRoot.getSlot(), blobSidecars);
      }
      indexWriter.write(formatIndexFileOutput(slotAndBlockRoot));
      indexWriter.newLine();
    } catch (final IOException ex) {
      LOG.error(String.format("Failed to archive blob sidecars for %s", slotAndBlockRoot), ex);
    }
  }

  @Override
  public Optional<List<BlobSidecar>> retrieve(final SlotAndBlockRoot slotAndBlockRoot) {
    try {
      final Path archivePath = resolveArchivePath(slotAndBlockRoot.getBlockRoot());
      if (!Files.exists(archivePath)) {
        return Optional.empty();
      }
      final String blobSidecarsJson = Files.readString(archivePath);
      final List<BlobSidecar> blobSidecars =
          JsonUtil.parse(blobSidecarsJson, getJsonTypeDefinition(slotAndBlockRoot.getSlot()));
      return Optional.of(blobSidecars);
    } catch (IOException ex) {
      LOG.error(
          String.format(
              "Failed to retrieve blob sidecars for slot %s and block root %s",
              slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot()),
          ex);
      return Optional.empty();
    }
  }

  @Override
  public Optional<List<BlobSidecar>> retrieve(final UInt64 slot) {
    final Path indexFile = resolveIndexFile(slot);
    if (!Files.exists(indexFile)) {
      return Optional.empty();
    }
    try (final Stream<String> lines = Files.lines(indexFile)) {
      return lines
          .filter(line -> line.startsWith(slot.toString()))
          .findFirst()
          .flatMap(
              line -> {
                // lines in the index file are in the format of: "<slot> <block_root>"
                final Bytes32 blockRoot =
                    Bytes32.fromHexString(Iterables.get(Splitter.on(' ').split(line), 1));
                return retrieve(new SlotAndBlockRoot(slot, blockRoot));
              });
    } catch (IOException ex) {
      LOG.error(String.format("Failed to retrieve blob sidecars for slot %s", slot), ex);
      return Optional.empty();
    }
  }

  /**
   * Given a basePath, block root, return where to store/find the BlobSidecar. Initial
   * implementation uses blockRoot as a hex string in the directory of the first two characters.
   *
   * @param blockRoot the block root.
   * @return a path of where to store or find the BlobSidecar
   */
  @VisibleForTesting
  public Path resolveArchivePath(final Bytes32 blockRoot) {
    // For blockroot 0x1a2bcd...  the directory is basePath/1a/2b/1a2bcd...
    // 256 * 256 directories = 65,536.
    // Assume 8000 to 10000 blobs per day. With perfect hash distribution,
    // all directories have one file after a week. After 1 year, expect 50 files in each directory.
    final String blockRootString = blockRoot.toUnprefixedHexString();
    final String dir1 = blockRootString.substring(0, 2);
    final String dir2 = blockRootString.substring(2, 4);
    final String blobSidecarFilename =
        dir1 + File.separator + dir2 + File.separator + blockRootString;
    return baseDirectory.resolve(blobSidecarFilename);
  }

  /**
   * Given a basePath, slot, return where to store/find the slot -> root index file
   *
   * @param slot the slot.
   * @return a path of where to store or find the slot -> root index file
   */
  @VisibleForTesting
  Path resolveIndexFile(final UInt64 slot) {
    final UInt64 lowerBound =
        slot.dividedBy(INDEX_FILE_SLOT_RANGE_SIZE).times(INDEX_FILE_SLOT_RANGE_SIZE);
    final UInt64 upperBound = lowerBound.plus(INDEX_FILE_SLOT_RANGE_SIZE).minusMinZero(1);
    final String fileName = String.format("%s-%s_%s", lowerBound, upperBound, INDEX_FILE_SUFFIX);
    return baseDirectory.resolve(fileName);
  }

  private void writeBlobSidecars(
      final OutputStream out, final UInt64 slot, final List<BlobSidecar> blobSidecars)
      throws IOException {
    JsonUtil.serializeToBytes(blobSidecars, getJsonTypeDefinition(slot), out);
  }

  private DeserializableTypeDefinition<List<BlobSidecar>> getJsonTypeDefinition(final UInt64 slot) {
    return milestoneToTypeDefinitionCache.computeIfAbsent(
        spec.atSlot(slot).getMilestone(),
        milestone ->
            listOf(
                SchemaDefinitionsDeneb.required(spec.forMilestone(milestone).getSchemaDefinitions())
                    .getBlobSidecarSchema()
                    .getJsonTypeDefinition()));
  }

  private String formatIndexFileOutput(final SlotAndBlockRoot slotAndBlockRoot) {
    return slotAndBlockRoot.getSlot()
        + " "
        + slotAndBlockRoot.getBlockRoot().toUnprefixedHexString();
  }
}
