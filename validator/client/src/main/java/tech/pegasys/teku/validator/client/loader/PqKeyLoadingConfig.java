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

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import tech.pegasys.teku.spec.signatures.xmss.XmssLeafProtector;

/**
 * How local validators' PQ keys are loaded.
 *
 * @param leafProtector guards the leaves of the validators' leanXMSS keys
 * @param derivedKeysVrfCacheDirectory when present, a validator with no PQ keystore gets PQ keys
 *     derived from its BLS secret key, and the VRF tree (or VRF state) of each derived key is saved
 *     in this directory; when empty, such a validator has no PQ keys
 * @param vrfStateUrl where derived keys can find precomputed VRF states (a devnet {@code
 *     vrf_state_url} file) instead of deriving each VRF tree in full
 * @param xmssCacheUrl where devnet genesis keys can find pregenerated leanXMSS trees (a devnet
 *     {@code xmss_cache_url} file) instead of building them on their first start
 * @param genesisState the network's genesis state, whose validators find their records in the XMSS
 *     cache when it has no key list
 */
public record PqKeyLoadingConfig(
    XmssLeafProtector leafProtector,
    Optional<Path> derivedKeysVrfCacheDirectory,
    Optional<URI> vrfStateUrl,
    Optional<URI> xmssCacheUrl,
    Optional<String> genesisState) {

  public PqKeyLoadingConfig(
      final XmssLeafProtector leafProtector,
      final Optional<Path> derivedKeysVrfCacheDirectory,
      final Optional<URI> vrfStateUrl,
      final Optional<URI> xmssCacheUrl) {
    this(leafProtector, derivedKeysVrfCacheDirectory, vrfStateUrl, xmssCacheUrl, Optional.empty());
  }

  public PqKeyLoadingConfig(
      final XmssLeafProtector leafProtector,
      final Optional<Path> derivedKeysVrfCacheDirectory,
      final Optional<URI> vrfStateUrl) {
    this(leafProtector, derivedKeysVrfCacheDirectory, vrfStateUrl, Optional.empty());
  }

  public PqKeyLoadingConfig(
      final XmssLeafProtector leafProtector, final Optional<Path> derivedKeysVrfCacheDirectory) {
    this(leafProtector, derivedKeysVrfCacheDirectory, Optional.empty());
  }

  /** Loads only PQ keystores; nothing is derived. */
  public static PqKeyLoadingConfig keystoresOnly(final XmssLeafProtector leafProtector) {
    return new PqKeyLoadingConfig(leafProtector, Optional.empty());
  }
}
