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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.io.File;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;

class LocalValidatorVerifierTest {
  private final Spec spec = TestSpecFactory.createDefault();

  @Test
  void shouldLoadSignerSuccessfully(@TempDir final Path tempDir) throws Exception {
    ValidatorLoaderTest.writeKeystore(tempDir);
    final String key = tempDir.resolve("key.json").toString();
    final String pass = tempDir.resolve("key.txt").toString();
    final LocalValidatorVerifier verifier =
        new LocalValidatorVerifier(spec, List.of(key + File.pathSeparator + pass), SYNC_RUNNER);
    final List<Pair<Path, Path>> keys = verifier.parse();
    assertThat(keys.size()).isEqualTo(1);
    final ValidatorSource.ValidatorProvider provider =
        verifier.createValidatorProvider(keys.get(0));
    final Signer signer = provider.createSigner();
    assertThat(signer).isNotNull();
  }

  @Test
  void shouldThrowExceptionWhenPasswordFileNotFound(@TempDir final Path tempDir) throws Exception {
    ValidatorLoaderTest.writeKeystore(tempDir);
    final String key = tempDir.resolve("key.json").toString();
    final String pass = tempDir.resolve("notfound").toString();
    final LocalValidatorVerifier verifier =
        new LocalValidatorVerifier(spec, List.of(key + File.pathSeparator + pass), SYNC_RUNNER);
    assertThatThrownBy(verifier::parse)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Could not find the password file");
  }

  @Test
  void shouldThrowExceptionWhenKeystoreFileNotFound(@TempDir final Path tempDir) throws Exception {
    ValidatorLoaderTest.writeKeystore(tempDir);
    final String key = tempDir.resolve("keynotfound.json").toString();
    final String pass = tempDir.resolve("pass.txt").toString();
    final LocalValidatorVerifier verifier =
        new LocalValidatorVerifier(spec, List.of(key + File.pathSeparator + pass), SYNC_RUNNER);
    assertThatThrownBy(verifier::parse)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Could not find the key file");
  }

  @Test
  void shouldThrowExceptionWhenCannotReadPasswordFile(@TempDir final Path tempDir)
      throws Exception {
    ValidatorLoaderTest.writeKeystore(tempDir);
    Files.setPosixFilePermissions(tempDir.resolve("key.txt"), Set.of());
    final String key = tempDir.resolve("key.json").toString();
    final String pass = tempDir.resolve("key.txt").toString();
    final LocalValidatorVerifier verifier =
        new LocalValidatorVerifier(spec, List.of(key + File.pathSeparator + pass), SYNC_RUNNER);
    final List<Pair<Path, Path>> keys = verifier.parse();
    assertThat(keys.size()).isEqualTo(1);
    assertThatThrownBy(() -> verifier.createValidatorProvider(keys.get(0)))
        .hasCauseInstanceOf(AccessDeniedException.class);
  }

  @Test
  void shouldThrowExceptionWhenCannotReadKeyFile(@TempDir final Path tempDir) throws Exception {
    ValidatorLoaderTest.writeKeystore(tempDir);
    final String key = tempDir.resolve("key.json").toString();
    final String pass = tempDir.resolve("key.txt").toString();
    Files.setPosixFilePermissions(tempDir.resolve("key.json"), Set.of());
    final LocalValidatorVerifier verifier =
        new LocalValidatorVerifier(spec, List.of(key + File.pathSeparator + pass), SYNC_RUNNER);
    final List<Pair<Path, Path>> keys = verifier.parse();
    assertThat(keys.size()).isEqualTo(1);
    assertThatThrownBy(() -> verifier.createValidatorProvider(keys.get(0)))
        .hasCauseInstanceOf(KeyStoreValidationException.class);
  }
}
