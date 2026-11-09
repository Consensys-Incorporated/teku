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

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.validator.client.loader.KeystoreLocker.longPidToNativeByteArray;
import static tech.pegasys.teku.validator.client.loader.KeystoreLocker.nativeByteArrayToLong;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class KeystoreLockerTest {

  private final KeystoreLocker keystoreLocker = new KeystoreLocker();

  @Test
  void shouldLockKeystoreFileAndFailWhenTryingCreateLockForLockedFile(
      final @TempDir Path keystoreFile) throws Exception {
    Assertions.assertThatCode(() -> keystoreLocker.lockKeystore(keystoreFile))
        .doesNotThrowAnyException();
    Assertions.assertThatThrownBy(() -> keystoreLocker.lockKeystore(keystoreFile))
        .isExactlyInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  void deleteLockfileIfTheProcessIsNotAlive(final @TempDir Path keystoreFile) throws Exception {
    // Assuming there won't be any live process with pid: Long.MAX_VALUE
    long pid = Long.MAX_VALUE;
    createLockfileWithContent(keystoreFile, longPidToNativeByteArray(pid));
    Assertions.assertThatCode(() -> keystoreLocker.lockKeystore(keystoreFile))
        .doesNotThrowAnyException();
    long lockfilePid = readLockfileContent(keystoreFile);
    assertThat(lockfilePid).isNotEqualTo(pid);
    assertThat(lockfilePid).isEqualTo(ProcessHandle.current().pid());
  }

  @Test
  void doNotDeleteIfLockfileIsEmpty(final @TempDir Path keystoreFile) throws Exception {
    createLockfileWithContent(keystoreFile, new byte[0]);
    Assertions.assertThatThrownBy(() -> keystoreLocker.lockKeystore(keystoreFile))
        .isExactlyInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  void doNotDeleteIfLockfileContainsSomethingThatIsNotLong(final @TempDir Path keystoreFile)
      throws Exception {
    createLockfileWithContent(keystoreFile, new byte[Long.BYTES + 1]);
    Assertions.assertThatThrownBy(() -> keystoreLocker.lockKeystore(keystoreFile))
        .isExactlyInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  void throwIOExceptionWhenTryingToLockNonExistentKeystoreInNonExistingDirectory(
      final @TempDir Path keystoreFile) {
    Path nonExistingKeystoreFile = Path.of(keystoreFile.toString() + "/yo/keystore.json");
    Assertions.assertThatThrownBy(() -> keystoreLocker.lockKeystore(nonExistingKeystoreFile))
        .isExactlyInstanceOf(UncheckedIOException.class);
  }

  private void createLockfileWithContent(final Path keystorePath, final byte[] content)
      throws IOException {
    Files.write(getLockfilePath(keystorePath), content, CREATE_NEW);
  }

  private long readLockfileContent(final Path keystorePath) throws Exception {
    byte[] pidInBytes = Files.readAllBytes(getLockfilePath(keystorePath));
    return nativeByteArrayToLong(pidInBytes);
  }

  private Path getLockfilePath(final Path keystorePath) {
    return Path.of(keystorePath.toString() + ".lock");
  }
}
