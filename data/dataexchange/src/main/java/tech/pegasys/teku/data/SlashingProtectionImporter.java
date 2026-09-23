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

package tech.pegasys.teku.data;

import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.SignedAttestation;
import tech.pegasys.teku.data.slashinginterchange.SignedBlock;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionImporter {
  private final Path slashingProtectionPath;
  private List<SigningHistory> data = new ArrayList<>();
  private Metadata metadata;
  private final SyncDataAccessor syncDataAccessor;

  public SlashingProtectionImporter(final Path slashingProtectionPath) {
    this.slashingProtectionPath = slashingProtectionPath;
    syncDataAccessor = SyncDataAccessor.create(slashingProtectionPath);
  }

  public Optional<String> initialise(final File inputFile) throws IOException {
    return initialise(new FileInputStream(inputFile));
  }

  public Optional<String> initialise(final InputStream inputStream) throws IOException {
    try {
      final SlashingProtectionInterchangeFormat interchangeFormat =
          JsonUtil.parse(inputStream, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());

      metadata = interchangeFormat.metadata();
      if (metadata == null) {
        return Optional.of(
            "Import data does not appear to have metadata information, and cannot be loaded.");
      }
      if (!INTERCHANGE_VERSION.equals(UInt64.valueOf(4))
          && !INTERCHANGE_VERSION.equals(metadata.interchangeFormatVersion())) {
        return Optional.of(
            String.format(
                "Import data has unsupported format version  %s. Required version is %s",
                metadata.interchangeFormat(), INTERCHANGE_VERSION));
      }

      data = summariseCompleteInterchangeFormat(interchangeFormat.data());

    } catch (JsonMappingException | MissingRequiredFieldException e) {
      final String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      return Optional.of("Failed to load data. " + cause);

    } catch (JsonParseException | IllegalArgumentException e) {
      final String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      return Optional.of(String.format("Json does not appear valid. %s", cause));
    }
    return Optional.empty();
  }

  private List<SigningHistory> summariseCompleteInterchangeFormat(
      final List<SigningHistory> completeSigningData) {
    return completeSigningData.stream().map(this::signingHistoryConverter).toList();
  }

  private SigningHistory signingHistoryConverter(final SigningHistory signingHistory) {
    final Optional<UInt64> lastSlot =
        signingHistory.signedBlocks().stream()
            .map(SignedBlock::slot)
            .filter(Objects::nonNull)
            .max(UInt64::compareTo);
    final Optional<UInt64> sourceEpoch =
        signingHistory.signedAttestations().stream()
            .map(SignedAttestation::sourceEpoch)
            .filter(Objects::nonNull)
            .filter(epoch -> !epoch.equals(UInt64.MAX_VALUE))
            .max(UInt64::compareTo);
    final Optional<UInt64> targetEpoch =
        signingHistory.signedAttestations().stream()
            .map(SignedAttestation::targetEpoch)
            .filter(Objects::nonNull)
            .filter(epoch -> !epoch.equals(UInt64.MAX_VALUE))
            .max(UInt64::compareTo);
    final ValidatorSigningRecord record =
        new ValidatorSigningRecord(
            metadata.genesisValidatorsRoot(),
            lastSlot.orElse(UInt64.ZERO),
            sourceEpoch.orElse(ValidatorSigningRecord.NEVER_SIGNED),
            targetEpoch.orElse(ValidatorSigningRecord.NEVER_SIGNED));
    return SigningHistory.createSigningHistory(signingHistory.pubkey(), record);
  }

  /**
   * Update local slashing protection data with everything from the source data given to the
   * initialise function.
   *
   * @param statusConsumer Consumer of any status strings that are generated
   * @return Any errors for specific keys will be returned in a map, otherwise an empty map if there
   *     are no errors.
   */
  public Map<BLSPublicKey, String> updateLocalRecords(final Consumer<String> statusConsumer) {
    final Map<BLSPublicKey, String> errors = new HashMap<>();
    data.forEach(
        record -> {
          Optional<String> error = updateLocalRecord(record, statusConsumer);
          error.ifPresent(errorString -> errors.put(record.pubkey(), errorString));
        });
    statusConsumer.accept("Updated " + data.size() + " validator slashing protection records");
    if (!errors.isEmpty()) {
      statusConsumer.accept("There were " + errors.size() + " errors found during import.");
    }
    return errors;
  }

  /**
   * Update local slashing protection data for a specific public key
   *
   * @param publicKey The public key to load if present in the source data given to the initialise
   *     function.
   * @param statusConsumer Consumer of any status strings that are generated
   * @return Any error will be returned, otherwise an empty response on successful load.
   */
  public Optional<String> updateSigningRecord(
      final BLSPublicKey publicKey, final Consumer<String> statusConsumer) {
    return data.stream()
        .filter(signingHistory -> signingHistory.pubkey().equals(publicKey))
        .flatMap(record -> updateLocalRecord(record, statusConsumer).stream())
        .findFirst();
  }

  private Optional<String> updateLocalRecord(
      final SigningHistory signingHistory, final Consumer<String> statusConsumer) {
    final String validatorString =
        signingHistory
            .pubkey()
            .toBytesCompressed()
            .toUnprefixedHexString()
            .toLowerCase(Locale.ROOT);
    final String hexValidatorPubkey = signingHistory.pubkey().toHexString();

    statusConsumer.accept("Importing " + validatorString);
    final Path outputFile = slashingProtectionPath.resolve(validatorString + ".yml");
    Optional<ValidatorSigningRecord> existingRecord = Optional.empty();
    if (outputFile.toFile().exists()) {
      try {
        existingRecord = syncDataAccessor.read(outputFile).map(ValidatorSigningRecord::fromBytes);
      } catch (UncheckedIOException | IOException e) {
        statusConsumer.accept("Failed to read existing file: " + outputFile);
        return Optional.of("unable to load existing record.");
      }
    }
    if (existingRecord.isPresent()
        && existingRecord.get().genesisValidatorsRoot().isPresent()
        && metadata.genesisValidatorsRoot().isPresent()
        && metadata
                .genesisValidatorsRoot()
                .get()
                .compareTo(existingRecord.get().genesisValidatorsRoot().get())
            != 0) {
      statusConsumer.accept(
          "Validator "
              + hexValidatorPubkey
              + " has a different validators signing root to the data being imported");
      return Optional.of("Genesis validators root did not match what was expected.");
    }

    try {
      syncDataAccessor.syncedWrite(
          outputFile,
          signingHistory
              .toValidatorSigningRecord(
                  existingRecord, metadata.genesisValidatorsRoot().orElse(null))
              .toBytes());
    } catch (IOException e) {
      statusConsumer.accept("Validator " + hexValidatorPubkey + " was not updated.");
      return Optional.of("Failed to update slashing protection record");
    }
    return Optional.empty();
  }
}
