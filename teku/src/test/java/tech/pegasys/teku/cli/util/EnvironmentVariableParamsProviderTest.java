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

package tech.pegasys.teku.cli.util;

import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class EnvironmentVariableParamsProviderTest {
  final CommandLine commandLine = new CommandLine(TestCommand.class);

  @Test
  void shouldReturnAdditionalParams() {
    final Map<String, String> environment =
        Map.of(
            "TEKU_unknown",
            "test",
            "TEKU_COUNT",
            "10",
            "TEKU_NAMES",
            "a,b",
            "TEKU_TEST_ENABLED",
            "true");

    final EnvironmentVariableParamsProvider environmentVariableParamsProvider =
        new EnvironmentVariableParamsProvider(environment);

    final Map<String, String> additionalParams =
        environmentVariableParamsProvider.getAdditionalParams(
            commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams.get("--count")).isEqualTo("10");
    Assertions.assertThat(additionalParams.get("--names")).isEqualTo("a,b");
    Assertions.assertThat(additionalParams.get("--test-enabled")).isEqualTo("true");
  }

  @Test
  void shouldIgnoreShortNames() {
    final Map<String, String> environment = Map.of("TEKU_N", "a,b");

    final EnvironmentVariableParamsProvider environmentVariableParamsProvider =
        new EnvironmentVariableParamsProvider(environment);

    final Map<String, String> additionalParams =
        environmentVariableParamsProvider.getAdditionalParams(
            commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams).isEmpty();
  }

  @Test
  void shouldIgnoreEnvWithNonMatchingPrefix() {
    final Map<String, String> environment = Map.of("TEST_ENABLED", "false", "1234_NAME", "a,b");

    final EnvironmentVariableParamsProvider environmentVariableParamsProvider =
        new EnvironmentVariableParamsProvider(environment);

    final Map<String, String> additionalParams =
        environmentVariableParamsProvider.getAdditionalParams(
            commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams).isEmpty();
  }

  @Test
  void shouldGiveCorrectPrecedenceWhenDuplicates() {
    final Map<String, String> environment =
        Map.of("TEKU_unknown", "test", "TEKU_NAME", "a,b", "TEKU_NAMES", "c,d");

    final EnvironmentVariableParamsProvider environmentVariableParamsProvider =
        new EnvironmentVariableParamsProvider(environment);

    final Map<String, String> additionalParams =
        environmentVariableParamsProvider.getAdditionalParams(
            commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams.get("--names")).isEqualTo("a,b");
  }
}
