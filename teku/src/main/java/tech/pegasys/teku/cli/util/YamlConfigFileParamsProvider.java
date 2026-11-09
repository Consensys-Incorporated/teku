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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

public class YamlConfigFileParamsProvider extends AbstractParamsProvider<Object>
    implements AdditionalParamsProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final CommandLine commandLine;
  private final File configFile;
  // this will be initialized on fist call of defaultValue by PicoCLI parseArgs
  private Map<String, Object> result;

  public YamlConfigFileParamsProvider(final CommandLine commandLine, final File configFile) {
    this.commandLine = commandLine;
    this.configFile = configFile;
  }

  @Override
  public Map<String, String> getAdditionalParams(final List<OptionSpec> potentialParams) {
    if (result == null) {
      result = loadConfigurationFromFile();
      checkConfigurationValidity(result == null || result.isEmpty());
      checkUnknownOptions(result);
    }

    return getAdditionalParams(potentialParams, result);
  }

  @Override
  protected String onConflictingParams(
      final Entry<String, String> conflictingParam,
      final String conflictingValue1,
      final String conflictingValue2) {
    LOG.warn(
        "Multiple yaml options referring to the same configuration '{}'. Keeping {} and ignoring {}",
        conflictingParam.getKey().replaceFirst("^-+", ""),
        conflictingValue1,
        conflictingValue2);
    return conflictingValue1;
  }

  @Override
  protected Optional<Entry<String, Object>> translateEntry(
      final Entry<String, Object> configEntry) {
    return Optional.of(configEntry);
  }

  @Override
  protected Map.Entry<String, String> translateToArg(
      final OptionSpec matchedOption, final Map.Entry<String, Object> yamlEntry) {
    final Object value = yamlEntry.getValue();

    final String translatedValue;

    if (value instanceof Collection) {
      if (!matchedOption.isMultiValue()) {
        throwParameterException(
            new IllegalArgumentException(),
            String.format(
                "The '%s' parameter in config file is multi-valued but the corresponding teku option is single-valued",
                yamlEntry.getKey()));
      }
      translatedValue =
          ((Collection<?>) value).stream().map(String::valueOf).collect(Collectors.joining(","));
    } else {
      translatedValue = String.valueOf(value);
    }

    return Map.entry(matchedOption.longestName(), translatedValue);
  }

  private Map<String, Object> loadConfigurationFromFile() {
    final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    try {
      return objectMapper.readValue(configFile, new TypeReference<>() {});
    } catch (final FileNotFoundException | NoSuchFileException e) {
      throwParameterException(
          e, "Unable to read yaml configuration. File not found: " + configFile);
    } catch (final JsonMappingException e) {
      throwParameterException(e, "Unexpected yaml content, expecting block mappings.");
    } catch (final JsonParseException e) {
      throwParameterException(
          e,
          String.format(
              "Unable to read yaml configuration. Invalid yaml file [%s]: %s",
              configFile, e.getMessage()));
    } catch (final IOException e) {
      throwParameterException(
          e,
          String.format(
              "Unexpected IO error while reading yaml configuration file [%s]: %s",
              configFile, e.getMessage()));
    }
    return Collections.emptyMap(); // unreachable
  }

  private void throwParameterException(final Throwable cause, final String message) {
    throw new ParameterException(commandLine, message, cause);
  }

  private void checkUnknownOptions(final Map<String, Object> result) {
    final CommandSpec commandSpec = commandLine.getCommandSpec();

    final Set<String> validOptions = new HashSet<>(commandSpec.optionsMap().keySet());
    commandSpec.subcommands().values().stream()
        .flatMap(subcommand -> subcommand.getCommandSpec().optionsMap().keySet().stream())
        .forEach(validOptions::add);

    final Set<String> unknownOptionsList =
        result.keySet().stream()
            .filter(option -> !validOptions.contains("--" + option))
            .filter(option -> !validOptions.contains("-" + option))
            .collect(Collectors.toCollection(TreeSet::new));

    if (!unknownOptionsList.isEmpty()) {
      final String options = unknownOptionsList.size() > 1 ? "options" : "option";
      final String csvUnknownOptions = String.join(", ", unknownOptionsList);
      throw new ParameterException(
          commandLine,
          String.format("Unknown %s in yaml configuration file: %s", options, csvUnknownOptions));
    }
  }

  private void checkConfigurationValidity(final boolean isEmpty) {
    if (isEmpty) {
      throw new ParameterException(
          commandLine, String.format("Empty yaml configuration file: %s", configFile));
    }
  }
}
