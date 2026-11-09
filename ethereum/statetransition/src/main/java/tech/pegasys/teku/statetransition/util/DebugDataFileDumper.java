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

import static tech.pegasys.teku.infrastructure.time.SystemTimeProvider.SYSTEM_TIME_PROVIDER;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class DebugDataFileDumper implements DebugDataDumper {

  private static final Logger LOG = LogManager.getLogger();

  private static final String GOSSIP_MESSAGES_DIR = "gossip_messages";
  private static final String DECODING_ERROR_SUB_DIR = "decoding_error";
  private static final String REJECTED_SUB_DIR = "rejected";
  private static final String INVALID_BLOCK_DIR = "invalid_blocks";
  private static final String INVALID_BLOB_SIDECARS_DIR = "invalid_blob_sidecars";
  private static final String INVALID_DATA_COLUMN_SIDECARS_DIR = "invalid_data_column_sidecars";
  private static final String INVALID_EXECUTION_PAYLOAD_DIR = "invalid_execution_payloads";

  private boolean enabled;
  private final Path directory;

  public DebugDataFileDumper(final Path directory) {
    this.enabled = true;
    this.directory = directory;

    final Path gossipMessagesPath = directory.resolve(GOSSIP_MESSAGES_DIR);
    createDirectory(gossipMessagesPath, GOSSIP_MESSAGES_DIR, "gossip messages");
    createDirectory(
        gossipMessagesPath.resolve(DECODING_ERROR_SUB_DIR),
        DECODING_ERROR_SUB_DIR,
        "gossip messages with decoding errors");
    createDirectory(
        gossipMessagesPath.resolve(REJECTED_SUB_DIR), REJECTED_SUB_DIR, "rejected gossip messages");
    createDirectory(directory.resolve(INVALID_BLOCK_DIR), INVALID_BLOCK_DIR, "invalid blocks");
    createDirectory(
        directory.resolve(INVALID_BLOB_SIDECARS_DIR),
        INVALID_BLOB_SIDECARS_DIR,
        "invalid blob sidecars");
    createDirectory(
        directory.resolve(INVALID_DATA_COLUMN_SIDECARS_DIR),
        INVALID_DATA_COLUMN_SIDECARS_DIR,
        "invalid data column sidecars");
    createDirectory(
        directory.resolve(INVALID_EXECUTION_PAYLOAD_DIR),
        INVALID_EXECUTION_PAYLOAD_DIR,
        "invalid execution payloads");
  }

  @Override
  public void saveGossipMessageDecodingError(
      final String topic,
      final Optional<UInt64> arrivalTimestamp,
      final Supplier<Bytes> originalMessage,
      final Throwable error) {
    if (!enabled) {
      return;
    }
    final String formattedTimestamp = formatOptionalTimestamp(arrivalTimestamp);
    final String fileName = String.format("%s.ssz", formattedTimestamp);
    final Path topicPath =
        Path.of(GOSSIP_MESSAGES_DIR)
            .resolve(DECODING_ERROR_SUB_DIR)
            .resolve(topic.replaceAll("/", "_"));
    final boolean success =
        saveBytesToFile(
            "gossip message with decoding error",
            topicPath.resolve(fileName),
            originalMessage.get());
    if (success) {
      LOG.warn("Failed to decode gossip message on topic {}", topic, error);
    }
  }

  @Override
  public void saveGossipRejectedMessage(
      final String topic,
      final Optional<UInt64> arrivalTimestamp,
      final Supplier<Bytes> decodedMessage,
      final Optional<String> reason) {
    if (!enabled) {
      return;
    }
    final String formattedTimestamp = formatOptionalTimestamp(arrivalTimestamp);
    final String fileName = String.format("%s.ssz", formattedTimestamp);
    final Path topicPath =
        Path.of(GOSSIP_MESSAGES_DIR).resolve(REJECTED_SUB_DIR).resolve(topic.replaceAll("/", "_"));
    final boolean success =
        saveBytesToFile(
            "rejected gossip message", topicPath.resolve(fileName), decodedMessage.get());
    if (success) {
      LOG.warn(
          "Rejecting gossip message on topic {}, reason: {}",
          topic,
          reason.orElse("failed validation"));
    }
  }

  @Override
  public void saveInvalidBlock(
      final SignedBeaconBlock block,
      final String failureReason,
      final Optional<Throwable> failureCause) {
    if (!enabled) {
      return;
    }
    final UInt64 slot = block.getSlot();
    final Bytes32 blockRoot = block.getRoot();
    final String fileName = String.format("%s_%s.ssz", slot, blockRoot.toUnprefixedHexString());
    final boolean success =
        saveBytesToFile(
            "invalid block", Path.of(INVALID_BLOCK_DIR).resolve(fileName), block.sszSerialize());
    if (success) {
      LOG.warn(
          "Rejecting invalid block at slot {} with root {}, reason: {}, cause: {}",
          slot,
          blockRoot,
          failureReason,
          failureCause.orElse(null));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void saveInvalidSidecars(final List<?> sidecars, final SignedBeaconBlock block) {
    if (!enabled || sidecars.isEmpty()) {
      return;
    }
    if (sidecars.getFirst() instanceof BlobSidecar) {
      saveInvalidBlobSidecars((List<BlobSidecar>) sidecars, block);
    } else if (sidecars.getFirst() instanceof DataColumnSidecar) {
      saveInvalidDataColumnSidecars(
          (List<DataColumnSidecar>) sidecars,
          block.getSlotAndBlockRoot(),
          block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow());
    } else {
      throw new RuntimeException("Unknown sidecar type: " + sidecars.getFirst());
    }
  }

  @Override
  public void saveInvalidExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final String failureReason,
      final Optional<Throwable> failureCause) {
    if (!enabled) {
      return;
    }
    final UInt64 slot = signedEnvelope.getSlot();
    final Bytes32 blockRoot = signedEnvelope.getBeaconBlockRoot();
    final UInt64 builderIndex = signedEnvelope.getMessage().getBuilderIndex();
    final String fileName =
        String.format("%s_%s_%s.ssz", slot, blockRoot.toUnprefixedHexString(), builderIndex);
    final boolean success =
        saveBytesToFile(
            "invalid execution payload",
            Path.of(INVALID_EXECUTION_PAYLOAD_DIR).resolve(fileName),
            signedEnvelope.sszSerialize());
    if (success) {
      LOG.warn(
          "Rejecting invalid execution payload at slot {} with block root {} and builder index {}, reason: {}, cause: {}",
          slot,
          blockRoot,
          builderIndex,
          failureReason,
          failureCause.orElse(null));
    }
  }

  private void saveInvalidBlobSidecars(
      final List<BlobSidecar> blobSidecars, final SignedBeaconBlock block) {
    final String kzgCommitmentsFileName =
        String.format(
            "%s_%s_kzg_commitments.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    saveBytesToFile(
        "kzg commitments",
        Path.of(INVALID_BLOB_SIDECARS_DIR).resolve(kzgCommitmentsFileName),
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow().sszSerialize());

    blobSidecars.forEach(
        blobSidecar -> {
          final UInt64 slot = blobSidecar.getSlot();
          final Bytes32 blockRoot = blobSidecar.getBlockRoot();
          final UInt64 index = blobSidecar.getIndex();
          final String fileName =
              String.format("%s_%s_%s.ssz", slot, blockRoot.toUnprefixedHexString(), index);
          saveBytesToFile(
              "blob sidecar",
              Path.of(INVALID_BLOB_SIDECARS_DIR).resolve(fileName),
              blobSidecar.sszSerialize());
        });
  }

  private void saveInvalidDataColumnSidecars(
      final List<DataColumnSidecar> dataColumnSidecars,
      final SlotAndBlockRoot slotAndBlockRoot,
      final SszList<SszKZGCommitment> blobKzgCommitments) {
    final String kzgCommitmentsFileName =
        String.format(
            "%s_%s_kzg_commitments.ssz",
            slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot().toUnprefixedHexString());
    saveBytesToFile(
        "kzg commitments",
        Path.of(INVALID_DATA_COLUMN_SIDECARS_DIR).resolve(kzgCommitmentsFileName),
        blobKzgCommitments.sszSerialize());

    dataColumnSidecars.forEach(
        dataColumnSidecar -> {
          final UInt64 slot = dataColumnSidecar.getSlot();
          final Bytes32 blockRoot = dataColumnSidecar.getBeaconBlockRoot();
          final UInt64 index = dataColumnSidecar.getIndex();
          final String fileName =
              String.format("%s_%s_%s.ssz", slot, blockRoot.toUnprefixedHexString(), index);
          saveBytesToFile(
              "data column sidecar",
              Path.of(INVALID_DATA_COLUMN_SIDECARS_DIR).resolve(fileName),
              dataColumnSidecar.sszSerialize());
        });
  }

  @VisibleForTesting
  boolean saveBytesToFile(
      final String description, final Path relativeFilePath, final Bytes bytes) {
    final Path path = directory.resolve(relativeFilePath);
    try {
      Files.write(path, bytes.toArray());
    } catch (NoSuchFileException e) {
      return saveAfterCreatingTopicDirectory(description, path, relativeFilePath, bytes);
    } catch (IOException e) {
      LOG.error("Failed to save {} bytes to file.", description, e);
      return false;
    }
    return true;
  }

  private boolean saveAfterCreatingTopicDirectory(
      final String description, final Path path, final Path relativeFilePath, final Bytes bytes) {
    if (!path.getParent().toFile().mkdirs()) {
      LOG.error(
          "Failed to save {} bytes to file. No such directory {} to save file.",
          description,
          relativeFilePath.getParent());
      return false;
    }
    try {
      Files.write(path, bytes.toArray());
    } catch (IOException e) {
      LOG.error("Failed to save {} bytes to file.", description, e);
      if (!path.getParent().toFile().exists()) {
        this.enabled = false;
        LOG.error(
            "{} directory does not exist. Disabling saving debug data to file.",
            relativeFilePath.getParent());
      }
      return false;
    }
    return true;
  }

  private void createDirectory(
      final Path path, final String directoryName, final String description) {
    if (!enabled) {
      return;
    }
    if (path.toFile().mkdirs()) {
      LOG.debug("{} directory has been created to save {}.", directoryName, description);
    } else {
      if (!path.toFile().exists()) {
        this.enabled = false;
        LOG.error(
            "Unable to create {} directory to save {}. Disabling saving debug data to file.",
            directoryName,
            description);
      }
    }
  }

  private String formatOptionalTimestamp(final Optional<UInt64> maybeTimestamp) {
    return formatOptionalTimestamp(maybeTimestamp, SYSTEM_TIME_PROVIDER);
  }

  @VisibleForTesting
  String formatOptionalTimestamp(
      final Optional<UInt64> maybeTimestamp, final TimeProvider timeProvider) {
    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss.SS");
    final Date date =
        maybeTimestamp
            .map(timestamp -> new Date(timestamp.longValue()))
            .orElse(new Date(timeProvider.getTimeInMillis().longValue()));
    return df.format(date);
  }

  @VisibleForTesting
  boolean isEnabled() {
    return enabled;
  }
}
