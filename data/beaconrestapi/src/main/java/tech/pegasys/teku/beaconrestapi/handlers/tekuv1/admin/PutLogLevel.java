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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PutLogLevel extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/admin/log_level";

  public PutLogLevel() {
    super(
        EndpointMetadata.put(ROUTE)
            .operationId("putLogLevel")
            .summary("Changes the log level without restarting.")
            .description(
                "Changes the log level without restarting. You can change the log level"
                    + " for all logs, or the log level for specific packages or classes.")
            .tags(TAG_TEKU)
            .requestBodyType(LogLevel.getJsonTypeDefinition())
            .response(SC_NO_CONTENT, "The LogLevel was accepted and applied")
            .build());
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final LogLevel requestBody = request.getRequestBody();
    final List<String> logFilters = requestBody.getLogFilter().orElse(List.of(""));

    for (final String logFilter : logFilters) {
      LoggingConfigurator.setAllLevels(logFilter, requestBody.getLevel());
    }
    request.respondWithCode(SC_NO_CONTENT);
  }

  static class LogLevel {
    private Level level;
    private Optional<List<String>> logFilter = Optional.empty();

    private static final DeserializableTypeDefinition<Level> LEVEL_TYPE =
        DeserializableTypeDefinition.string(Level.class)
            .name("Level")
            .formatter(Level::toString)
            .parser(Level::toLevel)
            .example("ERROR")
            .description("Level string")
            .format("string")
            .build();

    private LogLevel() {}

    public LogLevel(final String level) {
      this.level = Level.valueOf(level);
    }

    public LogLevel(final String level, final List<String> logFilter) {
      this.level = Level.valueOf(level);
      this.logFilter = Optional.of(logFilter);
    }

    public Level getLevel() {
      return level;
    }

    public void setLevel(final Level level) {
      this.level = level;
    }

    public Optional<List<String>> getLogFilter() {
      return logFilter;
    }

    public void setLogFilter(final Optional<List<String>> logFilter) {
      this.logFilter = logFilter;
    }

    static DeserializableTypeDefinition<LogLevel> getJsonTypeDefinition() {
      return DeserializableTypeDefinition.object(LogLevel.class)
          .name("PutLogLevelRequest")
          .initializer(LogLevel::new)
          .withField("level", LEVEL_TYPE, LogLevel::getLevel, LogLevel::setLevel)
          .withOptionalField(
              "log_filter",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              LogLevel::getLogFilter,
              LogLevel::setLogFilter)
          .build();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LogLevel logLevel = (LogLevel) o;
      return Objects.equals(level, logLevel.level) && Objects.equals(logFilter, logLevel.logFilter);
    }

    @Override
    public int hashCode() {
      return Objects.hash(level, logFilter);
    }

    @Override
    public String toString() {
      return "LogLevel{" + "level=" + level + ", logFilter=" + logFilter + '}';
    }
  }
}
