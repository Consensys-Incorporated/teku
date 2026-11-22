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

package tech.pegasys.teku.validator.client.loader;

import java.nio.file.Path;

/**
 * Where a validator's PQ key files live. They sit next to its BLS keystore and share its name: for
 * {@code keys/validator_1.json}, the PQ keystore is {@code keys/validator_1.pq-keystore} and the
 * saved VRF tree nodes are {@code keys/validator_1.vrf-cache}. Neither name ends in {@code .json},
 * so a keystore directory scan does not mistake them for BLS keystores. The PQ keystore is
 * encrypted with the BLS keystore's password.
 */
public final class PqKeyFiles {

  public static final String KEYSTORE_SUFFIX = ".pq-keystore";
  public static final String VRF_CACHE_SUFFIX = ".vrf-cache";
  public static final String XMSS_CACHE_SUFFIX = ".xmss-cache";
  private static final String JSON_SUFFIX = ".json";

  private PqKeyFiles() {}

  /** The PQ keystore of the validator whose BLS keystore is {@code blsKeystorePath}. */
  public static Path keystorePath(final Path blsKeystorePath) {
    return blsKeystorePath.resolveSibling(baseName(blsKeystorePath) + KEYSTORE_SUFFIX);
  }

  /**
   * Where the leanXMSS trees of the key whose VRF cache is {@code vrfCachePath} are saved: next to
   * it, with its name.
   */
  public static Path xmssCachePath(final Path vrfCachePath) {
    final String name = vrfCachePath.getFileName().toString();
    final String baseName =
        name.endsWith(VRF_CACHE_SUFFIX)
            ? name.substring(0, name.length() - VRF_CACHE_SUFFIX.length())
            : name;
    return vrfCachePath.resolveSibling(baseName + XMSS_CACHE_SUFFIX);
  }

  /** The VRF cache of the validator whose BLS keystore is {@code blsKeystorePath}. */
  public static Path vrfCachePath(final Path blsKeystorePath) {
    return blsKeystorePath.resolveSibling(baseName(blsKeystorePath) + VRF_CACHE_SUFFIX);
  }

  /** The PQ keystore named {@code name} in {@code directory}, for keys without a BLS keystore. */
  public static Path keystorePath(final Path directory, final String name) {
    return directory.resolve(name + KEYSTORE_SUFFIX);
  }

  /** The VRF cache named {@code name} in {@code directory}, for keys without a BLS keystore. */
  public static Path vrfCachePath(final Path directory, final String name) {
    return directory.resolve(name + VRF_CACHE_SUFFIX);
  }

  private static String baseName(final Path blsKeystorePath) {
    final String fileName = blsKeystorePath.getFileName().toString();
    return fileName.endsWith(JSON_SUFFIX)
        ? fileName.substring(0, fileName.length() - JSON_SUFFIX.length())
        : fileName;
  }
}
