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

package tech.pegasys.teku.cli.options;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Optional;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.CheckpointConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class WeakSubjectivityOptions {
  @CommandLine.Option(
      names = {"--ws-checkpoint"},
      paramLabel = "<STRING>",
      description =
          "A recent checkpoint within the weak subjectivity period. "
              + "Should be a string containing <BLOCK_ROOT>:<EPOCH_NUMBER> "
              + "or a URL containing the field ws_checkpoint with the same information.",
      arity = "1")
  private String weakSubjectivityCheckpoint;

  @CommandLine.Option(
      names = {"--Xws-suppress-errors-until-epoch"},
      paramLabel = "<EPOCH_NUMBER>",
      description =
          "Suppress weak subjectivity finalized checkpoint errors until the supplied epoch is reached.",
      arity = "1",
      hidden = true)
  private UInt64 suppressWSPeriodChecksUntilEpoch = null;

  public TekuConfiguration.Builder configure(final TekuConfiguration.Builder builder) {
    return builder.weakSubjectivity(
        wsBuilder -> {
          getWeakSubjectivityCheckpoint().ifPresent(wsBuilder::weakSubjectivityCheckpoint);
          if (suppressWSPeriodChecksUntilEpoch != null) {
            wsBuilder.suppressWSPeriodChecksUntilEpoch(suppressWSPeriodChecksUntilEpoch);
          }
        });
  }

  private Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    if (weakSubjectivityCheckpoint != null) {
      CheckpointConverter checkpointConverter = new CheckpointConverter();
      try {
        if (weakSubjectivityCheckpoint.startsWith("0x")) {
          return Optional.of(checkpointConverter.convert(weakSubjectivityCheckpoint));
        } else {
          ObjectMapper mapper = new ObjectMapper();
          final InputStream inputStream =
              ResourceLoader.urlOrFile()
                  .load(weakSubjectivityCheckpoint)
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "Failed to load resource URL " + weakSubjectivityCheckpoint));
          final String checkpointString =
              mapper.readTree(inputStream).get("ws_checkpoint").asText();
          return Optional.of(checkpointConverter.convert(checkpointString));
        }
      } catch (JsonParseException e) {
        throw new IllegalArgumentException(
            "Invalid value for option '--ws-checkpoint': "
                + "ws_checkpoint was not found at URL "
                + weakSubjectivityCheckpoint,
            e);
      } catch (Exception ex) {
        throw new CommandLine.PicocliException(
            "Invalid value for option '--ws-checkpoint': " + ex.getMessage(), ex);
      }
    }
    return Optional.empty();
  }
}
