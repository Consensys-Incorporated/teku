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

package tech.pegasys.teku.data;

import static tech.pegasys.teku.data.SlashingProtectionRepairer.parsePublicKey;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.api.exceptions.PublicKeyException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class SlashingProtectionExporter {
  private final List<SigningHistory> signingHistoryList = new ArrayList<>();
  private Optional<Bytes32> genesisValidatorsRoot = Optional.empty();
  private final SyncDataAccessor syncDataAccessor;
  protected final Path slashProtectionPath;

  public SlashingProtectionExporter(final Path slashProtectionPath) {
    this.slashProtectionPath = slashProtectionPath;
    this.syncDataAccessor = SyncDataAccessor.create(slashProtectionPath);
  }

  // returns a map of errors and the associated keys.
  public Map<BLSPublicKey, String> initialise(final Consumer<String> infoLogger) {
    final File slashingProtectionRecords = slashProtectionPath.toFile();
    final Map<BLSPublicKey, String> importErrors = new HashMap<>();
    for (File currentFile : slashingProtectionRecords.listFiles()) {
      final Optional<String> maybeError = readSlashProtectionFile(currentFile, infoLogger);
      maybeError.ifPresent(
          error -> {
            final BLSPublicKey key =
                BLSPublicKey.fromBytesCompressed(
                    Bytes48.fromHexString(currentFile.getName().replace(".yml", "")));
            importErrors.put(key, error);
          });
    }
    return importErrors;
  }

  // returns an error if there was one
  Optional<String> readSlashProtectionFile(final File file, final Consumer<String> infoLogger) {
    try {
      final Optional<ValidatorSigningRecord> maybeRecord =
          syncDataAccessor.read(file.toPath()).map(ValidatorSigningRecord::fromBytes);
      if (maybeRecord.isEmpty()) {
        return Optional.of("Failed to read from file " + file.getName());
      }
      final ValidatorSigningRecord validatorSigningRecord = maybeRecord.get();

      if (validatorSigningRecord.genesisValidatorsRoot().isPresent()) {
        if (genesisValidatorsRoot.isEmpty()) {
          this.genesisValidatorsRoot = validatorSigningRecord.genesisValidatorsRoot();
        } else if (!genesisValidatorsRoot
            .get()
            .equals(validatorSigningRecord.genesisValidatorsRoot().get())) {
          return Optional.of(
              "The genesisValidatorsRoot of "
                  + file.getName()
                  + " does not match the expected "
                  + genesisValidatorsRoot.get().toHexString());
        }
      }

      final String pubkey = file.getName().substring(0, file.getName().length() - ".yml".length());
      infoLogger.accept("Exporting " + pubkey);
      signingHistoryList.add(
          SigningHistory.createSigningHistory(parsePublicKey(pubkey), validatorSigningRecord));
      return Optional.empty();
    } catch (UncheckedIOException | IOException e) {
      return Optional.of("Failed to read from file " + file);
    } catch (PublicKeyException e) {
      return Optional.of("Public key in file " + file.getName() + " does not appear valid.");
    }
  }

  public void saveToFile(final String toFileName, final Consumer<String> infoLogger)
      throws IOException {
    syncDataAccessor.syncedWrite(Path.of(toFileName), getJsonByteData());
    infoLogger.accept(
        "Wrote "
            + signingHistoryList.size()
            + " validator slashing protection records to "
            + toFileName);
  }

  private Bytes getJsonByteData() throws JsonProcessingException {
    return Bytes.of(getPrettyJson().getBytes(StandardCharsets.UTF_8));
  }

  String getPrettyJson() throws JsonProcessingException {
    final SlashingProtectionInterchangeFormat data =
        new SlashingProtectionInterchangeFormat(
            new Metadata(Optional.empty(), INTERCHANGE_VERSION, genesisValidatorsRoot),
            signingHistoryList);
    return JsonUtil.prettySerialize(
        data, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());
  }

  String getJson() throws JsonProcessingException {
    final SlashingProtectionInterchangeFormat data =
        new SlashingProtectionInterchangeFormat(
            new Metadata(Optional.empty(), INTERCHANGE_VERSION, genesisValidatorsRoot),
            signingHistoryList);
    return JsonUtil.serialize(data, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());
  }
}
