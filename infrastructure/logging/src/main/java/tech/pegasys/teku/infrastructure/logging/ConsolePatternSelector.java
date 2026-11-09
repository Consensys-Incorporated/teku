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

package tech.pegasys.teku.infrastructure.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.layout.PatternSelector;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;

public class ConsolePatternSelector implements PatternSelector {
  private static final String DATE_CONSOLE_FORMAT = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level - %msg%n";
  private static final String DATE_CONSOLE_EXCEPTION_FORMAT =
      "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level - %msg %throwable{1} (See log file for full stack trace)%n";
  private static final String CONSOLE_FORMAT = "%d{HH:mm:ss.SSS} %-5level - %msg%n";
  private static final String CONSOLE_EXCEPTION_FORMAT =
      "%d{HH:mm:ss.SSS} %-5level - %msg %throwable{1} (See log file for full stack trace)%n";

  private final boolean omitStackTraces;
  private final PatternFormatter[] omitStackTraceFormat;
  private final PatternFormatter[] defaultFormat;

  public ConsolePatternSelector(
      final AbstractConfiguration configuration,
      final boolean omitStackTraces,
      final boolean prependDateFormat) {
    this.omitStackTraces = omitStackTraces;

    final PatternParser patternParser = PatternLayout.createPatternParser(configuration);
    omitStackTraceFormat =
        patternParser
            .parse(
                prependDateFormat ? DATE_CONSOLE_EXCEPTION_FORMAT : CONSOLE_EXCEPTION_FORMAT,
                false,
                true)
            .toArray(PatternFormatter[]::new);
    defaultFormat =
        patternParser
            .parse(prependDateFormat ? DATE_CONSOLE_FORMAT : CONSOLE_FORMAT, true, true)
            .toArray(PatternFormatter[]::new);
  }

  @Override
  public PatternFormatter[] getFormatters(final LogEvent event) {
    return omitStackTraces && event.getThrownProxy() != null ? omitStackTraceFormat : defaultFormat;
  }
}
