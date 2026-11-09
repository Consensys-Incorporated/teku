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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.bls.BLSPublicKey;

public class SlashingProtectionIncrementalExporter extends SlashingProtectionExporter {
  public SlashingProtectionIncrementalExporter(final Path slashProtectionPath) {
    super(slashProtectionPath);
  }

  public boolean haveSlashingProtectionData(final BLSPublicKey publicKey) {
    return getSlashingProtectionFileForKey(publicKey).exists();
  }

  // returns an error on failure to read, otherwise empty string.
  public Optional<String> addPublicKeyToExport(
      final BLSPublicKey publicKey, final Consumer<String> infoLogger) {
    final File slashingProtectionFile = getSlashingProtectionFileForKey(publicKey);
    if (slashingProtectionFile.exists()) {
      return readSlashProtectionFile(slashingProtectionFile, infoLogger);
    }
    return Optional.empty();
  }

  public String finalise() throws JsonProcessingException {
    return getJson();
  }

  private File getSlashingProtectionFileForKey(final BLSPublicKey publicKey) {
    return slashProtectionPath.resolve(slashingFileNameForKey(publicKey)).toFile();
  }

  private String slashingFileNameForKey(final BLSPublicKey publicKey) {
    return publicKey.toBytesCompressed().toUnprefixedHexString() + ".yml";
  }
}
