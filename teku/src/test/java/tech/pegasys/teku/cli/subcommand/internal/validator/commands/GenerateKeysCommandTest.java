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

package tech.pegasys.teku.cli.subcommand.internal.validator.commands;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeyGenerationOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.VerbosityOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.KeyGenerator;

class GenerateKeysCommandTest {
  private static final Consumer<Integer> SHUTDOWN_FUNCTION = status -> {};
  private KeyGenerationOptions keyGenerationOptions;
  private KeyGenerator keyGenerator;

  @BeforeEach
  void setUp() {
    keyGenerationOptions = mock(KeyGenerationOptions.class);
    keyGenerator = mock(KeyGenerator.class);
    when(keyGenerationOptions.createKeyGenerator(anyBoolean())).thenReturn(keyGenerator);
  }

  @Test
  public void generatesKeysWithDisplayConfirmation() {
    final GenerateKeysCommand generateKeysCommand =
        new GenerateKeysCommand(
            SHUTDOWN_FUNCTION, keyGenerationOptions, new VerbosityOptions(true));

    generateKeysCommand.run();
    verify(keyGenerationOptions).createKeyGenerator(true);
    verify(keyGenerator).generateKeys();
  }

  @Test
  public void generatesKeysWithoutDisplayConfirmation() {
    final GenerateKeysCommand generateKeysCommand =
        new GenerateKeysCommand(
            SHUTDOWN_FUNCTION, keyGenerationOptions, new VerbosityOptions(false));

    generateKeysCommand.run();
    verify(keyGenerationOptions).createKeyGenerator(false);
    verify(keyGenerator).generateKeys();
  }
}
