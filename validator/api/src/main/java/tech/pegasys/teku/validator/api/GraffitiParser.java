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

package tech.pegasys.teku.validator.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes32;

public class GraffitiParser {

  /*
   * Because we're parsing text from a file here we allow as many as 40 bytes to be read from the file.
   * We allow the extra bytes (above 32) to accommodate for leading or trailing whitespace, including new line
   * characters, which users might not be aware of. We still keep this very number low to minimise bytes read.
   */
  private static final int MAX_FILE_SIZE = 40;

  public static Bytes32 loadFromFile(final Path graffitiFile) throws GraffitiLoaderException {
    try {
      checkNotNull(graffitiFile, "GraffitiFile path cannot be null");
      if (Files.size(graffitiFile) > MAX_FILE_SIZE) {
        throw new GraffitiLoaderException(
            "Graffiti file size is too big. The maximum size for graffiti is 32 bytes. The graffiti file "
                + "can be as many as 40 bytes because we strip leading and trailing whitespace.");
      }
      try (final InputStream graffitiFileInputStream = Files.newInputStream(graffitiFile)) {
        return Bytes32Parser.toBytes32(strip(graffitiFileInputStream.readNBytes(MAX_FILE_SIZE)));
      }
    } catch (final FileNotFoundException e) {
      throw new GraffitiLoaderException("GraffitiFile file not found: " + graffitiFile, e);
    } catch (final IOException e) {
      throw new GraffitiLoaderException(
          "Unexpected IO error while reading GraffitiFile: " + e.getMessage(), e);
    }
  }

  @VisibleForTesting
  static String strip(final byte[] value) {
    return new String(value, StandardCharsets.UTF_8).strip();
  }
}
