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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX;
import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_FILE_NAME_PREFIX;
import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_FILE_NAME_SUFFIX;

import org.junit.jupiter.api.Test;

public class LoggingConfigTest {

  private static final String FS = System.getProperty("file.separator");

  @Test
  public void shouldFailIfNoPathsSpecified() {
    assertThatThrownBy(() -> LoggingConfig.builder().build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LoggingConfig.builder().logPath("./my.log").build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LoggingConfig.builder().logPathPattern("./my_%d.log").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldConstructFromDataPath() {
    String dataDir = "." + FS + "mydata";
    LoggingConfig config = LoggingConfig.builder().dataDirectory(dataDir).build();

    assertThat(config.getLogFile())
        .isEqualTo(
            dataDir
                + FS
                + "logs"
                + FS
                + DEFAULT_LOG_FILE_NAME_PREFIX
                + DEFAULT_LOG_FILE_NAME_SUFFIX);
    assertThat(config.getLogFileNamePattern())
        .isEqualTo(
            dataDir
                + FS
                + "logs"
                + FS
                + DEFAULT_LOG_FILE_NAME_PREFIX
                + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructFromLogDirectory() {
    String logDir = "." + FS + "mylogs";
    LoggingConfig config = LoggingConfig.builder().logDirectory(logDir).build();

    assertThat(config.getLogFile())
        .isEqualTo(logDir + FS + DEFAULT_LOG_FILE_NAME_PREFIX + DEFAULT_LOG_FILE_NAME_SUFFIX);
    assertThat(config.getLogFileNamePattern())
        .isEqualTo(
            logDir + FS + DEFAULT_LOG_FILE_NAME_PREFIX + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructFromFilePrefix() {
    LoggingConfig config =
        LoggingConfig.builder().logDirectory(".").logFileNamePrefix("prefix").build();

    assertThat(config.getLogFile()).isEqualTo("." + FS + "prefix" + DEFAULT_LOG_FILE_NAME_SUFFIX);
    assertThat(config.getLogFileNamePattern())
        .isEqualTo("." + FS + "prefix" + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructWithFullPath() {
    String logDir = "." + FS + "mylogs";
    String logPath = logDir + FS + "happy.log";
    String logPathPattern = logDir + FS + "happy_%d{yyyy-MM-dd}.gzip";

    LoggingConfig config =
        LoggingConfig.builder()
            .logDirectory("ignore" + FS + "directory")
            .logFileNamePrefix("should.ignore")
            .logPath(logPath)
            .logPathPattern(logPathPattern)
            .build();

    assertThat(config.getLogFile()).isEqualTo(logPath);
    assertThat(config.getLogFileNamePattern()).isEqualTo(logPathPattern);
  }
}
