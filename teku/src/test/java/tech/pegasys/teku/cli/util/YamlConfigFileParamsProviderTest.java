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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class YamlConfigFileParamsProviderTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));

  final CommandLine commandLine = new CommandLine(TestCommand.class);

  @Test
  void parsingValidYamlFilePopulatesCommandObject(@TempDir final Path tempDir) throws IOException {
    final Path configFile = writeToYamlConfigFile(defaultOptions(), tempDir);
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());
    final Map<String, String> additionalParams =
        yamlConfigFileDefaultProvider.getAdditionalParams(commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams.get("--count")).isEqualTo("10");
    Assertions.assertThat(additionalParams.get("--names")).isEqualTo("a,b");
    Assertions.assertThat(additionalParams.get("--test-enabled")).isEqualTo("true");
  }

  @Test
  void parsingValidYamlShouldIgnoreShortParam(@TempDir final Path tempDir) throws IOException {
    final Map<String, Object> options = new HashMap<>();
    options.put("n", List.of("b", "a"));
    final Path configFile = writeToYamlConfigFile(options, tempDir);
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());
    final Map<String, String> additionalParams =
        yamlConfigFileDefaultProvider.getAdditionalParams(commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams).isEmpty();
  }

  @Test
  void parsingYamlWithDifferentOptionsForSameParamPopulatesCommandObject(
      @TempDir final Path tempDir) throws IOException {
    final Map<String, Object> options = new HashMap<>();
    options.put("names", List.of("b", "c"));
    options.put("name", List.of("b", "a"));
    final Path configFile = writeToYamlConfigFile(options, tempDir);
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());

    final Map<String, String> additionalParams =
        yamlConfigFileDefaultProvider.getAdditionalParams(commandLine.getCommandSpec().options());

    Assertions.assertThat(additionalParams.get("--names")).isEqualTo("b,a");
  }

  @Test
  void parsingYamlWithInvalidMultivaluedParamThrows(@TempDir final Path tempDir)
      throws IOException {
    final Map<String, Object> options = defaultOptions();
    options.put("count", List.of(1, 2));
    final Path configFile = writeToYamlConfigFile(options, tempDir);
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                yamlConfigFileDefaultProvider.getAdditionalParams(
                    commandLine.getCommandSpec().options()))
        .withMessage(
            "The 'count' parameter in config file is multi-valued but the corresponding teku option is single-valued");
  }

  @Test
  void parsingEmptyConfigFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path configFile = writeToYamlConfigFile(Collections.emptyMap(), tempDir);
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                yamlConfigFileDefaultProvider.getAdditionalParams(
                    commandLine.getCommandSpec().options()))
        .withMessage("Empty yaml configuration file: %s", configFile);
  }

  @Test
  void parsingNonExistingConfigFileThrowsException(@TempDir final Path tempDir) {
    final Path configFile = tempDir.resolve("config.yaml");
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                yamlConfigFileDefaultProvider.getAdditionalParams(
                    commandLine.getCommandSpec().options()))
        .withMessage("Unable to read yaml configuration. File not found: %s", configFile);
  }

  @Test
  void parsingInvalidYamlConfigFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path configFile =
        Files.writeString(tempDir.resolve("config.yaml"), "test: test\noption= True\n");
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                yamlConfigFileDefaultProvider.getAdditionalParams(
                    commandLine.getCommandSpec().options()))
        .withMessageStartingWith(
            "Unable to read yaml configuration. Invalid yaml file [%s]:", configFile);
  }

  @Test
  void parsingSequenceMappingInYamlConfigFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Path configFile =
        Files.writeString(tempDir.resolve("config.yaml"), "- test\n- test2\n- test3");
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                yamlConfigFileDefaultProvider.getAdditionalParams(
                    commandLine.getCommandSpec().options()))
        .withMessage("Unexpected yaml content, expecting block mappings.");
  }

  @Test
  void parsingUnknownOptionsInYamlConfigFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Map<String, Object> options = defaultOptions();
    options.put("additional-option-3", "x");
    options.put("additional-option-1", "x");
    options.put("additional-option-2", "y");
    final Path configFile = writeToYamlConfigFile(options, tempDir);
    final YamlConfigFileParamsProvider yamlConfigFileDefaultProvider =
        new YamlConfigFileParamsProvider(commandLine, configFile.toFile());

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                yamlConfigFileDefaultProvider.getAdditionalParams(
                    commandLine.getCommandSpec().options()))
        .withMessage(
            "Unknown options in yaml configuration file: additional-option-1, additional-option-2, additional-option-3");
  }

  private Map<String, Object> defaultOptions() {
    final Map<String, Object> options = new HashMap<>();
    options.put("count", 10);
    options.put("names", List.of("a", "b"));
    options.put("test-enabled", true);
    return options;
  }

  private Path writeToYamlConfigFile(final Map<String, Object> options, final Path tempDir)
      throws IOException {
    final Path configFile = tempDir.resolve("config.yaml");
    objectMapper.writeValue(configFile.toFile(), options);
    return configFile;
  }
}
