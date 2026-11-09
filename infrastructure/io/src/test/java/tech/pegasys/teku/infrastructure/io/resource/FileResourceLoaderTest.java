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

package tech.pegasys.teku.infrastructure.io.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileResourceLoaderTest {

  public static final byte[] MESSAGE = "Hello".getBytes(StandardCharsets.UTF_8);
  private final FileResourceLoader loader = new FileResourceLoader(__ -> true);

  @Test
  public void shouldLoadFile(@TempDir final Path tempDir) throws Exception {
    final Path file = tempDir.resolve("test.txt");
    Files.write(file, MESSAGE);

    assertThat(loader.loadBytes(file.toAbsolutePath().toString())).contains(Bytes.wrap(MESSAGE));
  }

  @Test
  public void shouldReturnEmptyWhenFileDoesNotExist(@TempDir final Path tempDir) throws Exception {
    assertThat(loader.load(tempDir.resolve("test.txt").toAbsolutePath().toString())).isEmpty();
  }

  @Test
  public void shouldThrowWhenPathIsADirectory(@TempDir final Path tempDir) throws Exception {

    // should throw either on opening InputStream from directory or reading from created InputStream
    assertThatThrownBy(
            () -> {
              Optional<InputStream> inputStream = loader.load(tempDir.toAbsolutePath().toString());

              // if load() call didn't cause exception then InputStream should exist
              // We could potentially return empty for directories, but it is going to be confusing
              // for users
              // to say we couldn't find something that exists. We should report that we couldn't
              // read it.
              assertThat(inputStream).isNotEmpty();

              // At least the read() call should throw
              inputStream.get().read();
            })
        .isInstanceOf(IOException.class);

    assertThatThrownBy(() -> loader.loadBytes(tempDir.toAbsolutePath().toString()))
        .isInstanceOf(IOException.class);
  }
}
