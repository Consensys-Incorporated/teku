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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.STORAGE_MODE_FILENAME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseStorageModeFileHelperTest {

  @Test
  public void shouldReadValidStorageModeFile(@TempDir final Path tempDir) {
    final Path dbStorageModeFile =
        createDatabaseStorageModeFile(tempDir, StateStorageMode.MINIMAL.toString());
    assertThat(DatabaseStorageModeFileHelper.readStateStorageMode(dbStorageModeFile))
        .hasValue(StateStorageMode.MINIMAL);
  }

  @Test
  public void shouldReadEmptyWhenFileDoesNotExist(@TempDir final Path tempDir) {
    final Path dbStorageModeFile = tempDir.resolve("foo");
    assertThat(DatabaseStorageModeFileHelper.readStateStorageMode(dbStorageModeFile)).isEmpty();
  }

  @Test
  public void shouldThrowErrorIfFileHasInvalidValue(@TempDir final Path tempDir) {
    final Path dbStorageModeFile = createDatabaseStorageModeFile(tempDir, "hello");
    assertThatThrownBy(() -> DatabaseStorageModeFileHelper.readStateStorageMode(dbStorageModeFile))
        .isInstanceOf(DatabaseStorageException.class);
  }

  @Test
  public void shouldThrowErrorIfFileIsEmpty(@TempDir final Path tempDir) {
    final Path dbStorageModeFile = createDatabaseStorageModeFile(tempDir, "");
    assertThatThrownBy(() -> DatabaseStorageModeFileHelper.readStateStorageMode(dbStorageModeFile))
        .isInstanceOf(DatabaseStorageException.class);
  }

  private Path createDatabaseStorageModeFile(final Path path, final String value) {
    try {
      return Files.writeString(path.resolve(STORAGE_MODE_FILENAME), value);
    } catch (IOException e) {
      fail(e);
      return null;
    }
  }
}
