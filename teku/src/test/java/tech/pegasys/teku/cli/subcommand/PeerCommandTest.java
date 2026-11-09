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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class PeerCommandTest {

  @Test
  public void peerGenerate_shouldDisplayErrorIfFileNotFound() {
    PeerCommand peerCommand = new PeerCommand();
    final String fileWhichDoesNotExist = "directory-does-not-exist/config.dat";

    assertThatThrownBy(
            () -> {
              peerCommand.validateParamsAndGenerate(fileWhichDoesNotExist, 3);
            })
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("existing directory");
  }

  @Test
  public void peerGenerate_shouldDisplayErrorIfFileAlreadyExists() throws IOException {
    final Path peerIdsFile = Files.createTempFile("config", ".dat");
    peerIdsFile.toFile().deleteOnExit();
    PeerCommand peerCommand = new PeerCommand();

    assertThatThrownBy(
            () -> {
              peerCommand.validateParamsAndGenerate(peerIdsFile.toAbsolutePath().toString(), 3);
              peerCommand.validateParamsAndGenerate(peerIdsFile.toAbsolutePath().toString(), 3);
            })
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Not overwriting existing file");
  }
}
