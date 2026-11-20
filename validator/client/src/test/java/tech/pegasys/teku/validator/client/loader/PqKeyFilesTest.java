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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PqKeyFilesTest {

  @Test
  void pqFilesSitNextToTheBlsKeystoreWithItsName() {
    final Path blsKeystore = Path.of("keys", "validator_1.json");

    assertThat(PqKeyFiles.keystorePath(blsKeystore))
        .isEqualTo(Path.of("keys", "validator_1.pq-keystore"));
    assertThat(PqKeyFiles.vrfCachePath(blsKeystore))
        .isEqualTo(Path.of("keys", "validator_1.vrf-cache"));
  }

  @Test
  void keepsNamesWithoutJsonSuffix() {
    final Path blsKeystore = Path.of("keys", "validator_1.keystore");

    assertThat(PqKeyFiles.keystorePath(blsKeystore))
        .isEqualTo(Path.of("keys", "validator_1.keystore.pq-keystore"));
  }

  @Test
  void pqFileNamesAreNotPickedUpAsBlsKeystores() {
    // KeyStoreFilesLocator treats every *.json file in a key directory as a BLS keystore
    assertThat(PqKeyFiles.KEYSTORE_SUFFIX).doesNotEndWith(".json");
    assertThat(PqKeyFiles.VRF_CACHE_SUFFIX).doesNotEndWith(".json");
  }

  @Test
  void namedFilesForKeysWithoutBlsKeystore() {
    final Path dir = Path.of("keys");

    assertThat(PqKeyFiles.keystorePath(dir, "pq_ab")).isEqualTo(dir.resolve("pq_ab.pq-keystore"));
    assertThat(PqKeyFiles.vrfCachePath(dir, "pq_ab")).isEqualTo(dir.resolve("pq_ab.vrf-cache"));
  }
}
