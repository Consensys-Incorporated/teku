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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class DebugDataFileDumperTest {
  private DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalDeneb());
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  @Test
  void saveGossipMessageDecodingError_shouldSaveToFile(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final Optional<UInt64> arrivalTimestamp = Optional.of(timeProvider.getTimeInMillis());
    dumper.saveGossipMessageDecodingError(
        "/eth/test/topic", arrivalTimestamp, () -> messageBytes, new Throwable());

    final String fileName =
        String.format("%s.ssz", formatTimestamp(timeProvider.getTimeInMillis().longValue()));
    final Path expectedFile =
        tempDir
            .resolve("gossip_messages")
            .resolve("decoding_error")
            .resolve("_eth_test_topic")
            .resolve(fileName);
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipRejectedMessage_shouldSaveToFile(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final Optional<UInt64> arrivalTimestamp = Optional.of(timeProvider.getTimeInMillis());
    dumper.saveGossipRejectedMessage(
        "/eth/test/topic", arrivalTimestamp, () -> messageBytes, Optional.of("reason"));

    final String fileName =
        String.format("%s.ssz", formatTimestamp(timeProvider.getTimeInMillis().longValue()));
    final Path expectedFile =
        tempDir
            .resolve("gossip_messages")
            .resolve("rejected")
            .resolve("_eth_test_topic")
            .resolve(fileName);
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveInvalidBlockToFile_shouldSaveToFile(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    dumper.saveInvalidBlock(block, "reason", Optional.of(new Throwable()));

    final String fileName =
        String.format("%s_%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedFile = tempDir.resolve("invalid_blocks").resolve(fileName);
    checkBytesSavedToFile(expectedFile, block.sszSerialize());
  }

  @Test
  void saveInvalidBlobSidecars_shouldSaveToFiles(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    dumper.saveInvalidSidecars(blobSidecars, block);

    final String kzgCommitmentsFileName =
        String.format(
            "%s_%s_kzg_commitments.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedKzgCommitmentsFileName =
        tempDir.resolve("invalid_blob_sidecars").resolve(kzgCommitmentsFileName);
    checkBytesSavedToFile(
        expectedKzgCommitmentsFileName,
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow().sszSerialize());

    blobSidecars.forEach(
        blobSidecar -> {
          final String fileName =
              String.format(
                  "%s_%s_%s.ssz",
                  blobSidecar.getSlot(),
                  blobSidecar.getBlockRoot().toUnprefixedHexString(),
                  blobSidecar.getIndex());
          final Path expectedFile = tempDir.resolve("invalid_blob_sidecars").resolve(fileName);
          checkBytesSavedToFile(expectedFile, blobSidecar.sszSerialize());
        });
  }

  @Test
  void saveBytesToFile_shouldNotThrowExceptionWhenNoDirectory(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    assertDoesNotThrow(
        () -> {
          final boolean success =
              dumper.saveBytesToFile("object", Path.of("invalid").resolve("file.ssz"), Bytes.EMPTY);
          assertThat(success).isTrue(); // creates directory
        });
  }

  @Test
  void saveBytesToFile_shouldNotEscalateWhenIOException(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final File invalidPath = tempDir.resolve("invalid").toFile();
    assertThat(invalidPath.mkdirs()).isTrue();
    assertThat(invalidPath.setWritable(false)).isTrue();
    assertDoesNotThrow(
        () -> {
          final boolean success =
              dumper.saveBytesToFile("object", Path.of("invalid").resolve("file.ssz"), Bytes.EMPTY);
          assertThat(success).isFalse();
        });
  }

  @Test
  void constructionOfDirectories_shouldDisableWhenFailedToCreate(@TempDir final Path tempDir) {
    assertThat(tempDir.toFile().setWritable(false)).isTrue();
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    assertThat(dumper.isEnabled()).isFalse();
  }

  @Test
  void formatOptionalTimestamp_shouldFormatTimestamp(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final String formattedTimestamp =
        dumper.formatOptionalTimestamp(Optional.of(timeProvider.getTimeInMillis()), timeProvider);
    assertThat(formattedTimestamp)
        .isEqualTo(formatTimestamp(timeProvider.getTimeInMillis().longValue()));
  }

  @Test
  void formatOptionalTimestamp_shouldGenerateTimestamp(@TempDir final Path tempDir) {
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final String formattedTimestamp =
        dumper.formatOptionalTimestamp(Optional.empty(), timeProvider);
    assertThat(formattedTimestamp)
        .isEqualTo(formatTimestamp(timeProvider.getTimeInMillis().longValue()));
  }

  @Test
  void saveInvalidExecutionPayloadToFile_shouldSaveToFile(@TempDir final Path tempDir) {
    dataStructureUtil = new DataStructureUtil(TestSpecFactory.createMinimalGloas());
    final DebugDataFileDumper dumper = new DebugDataFileDumper(tempDir);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);
    dumper.saveInvalidExecutionPayload(executionPayload, "reason", Optional.of(new Throwable()));

    final String fileName =
        String.format(
            "%s_%s_%s.ssz",
            executionPayload.getSlot(),
            executionPayload.getBeaconBlockRoot().toUnprefixedHexString(),
            executionPayload.getMessage().getBuilderIndex());
    final Path expectedFile = tempDir.resolve("invalid_execution_payloads").resolve(fileName);
    checkBytesSavedToFile(expectedFile, executionPayload.sszSerialize());
  }

  private void checkBytesSavedToFile(final Path path, final Bytes expectedBytes) {
    try {
      final Bytes bytes = Bytes.wrap(Files.readAllBytes(path));
      assertThat(bytes).isEqualTo(expectedBytes);
    } catch (IOException e) {
      fail();
    }
  }

  private String formatTimestamp(final long timeInMillis) {
    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss.SS");
    final Date date = new Date(timeInMillis);
    return df.format(date);
  }
}
