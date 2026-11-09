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

import static tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.print;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;

public class DbLogger {

  public static final DbLogger DB_LOGGER = new DbLogger(LoggingConfigurator.DB_LOGGER_NAME);

  private final int dbOpAlertThresholdMillis = LoggingConfigurator.dbOpAlertThresholdMillis();

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger logger;

  public DbLogger(final String name) {
    this.logger = LogManager.getLogger(name);
  }

  public void onDbOpAlertThreshold(
      final String opName,
      final Supplier<String> additionalInfo,
      final long startTimeMillis,
      final long endTimeMillis) {
    final long duration = endTimeMillis - startTimeMillis;
    if (dbOpAlertThresholdMillis > 0 && duration >= dbOpAlertThresholdMillis) {
      logger.warn(
          print(
              String.format(
                  "DB operation %s took too long: %d ms. The alert threshold is set to: %d ms. Additional info: %s",
                  opName, duration, dbOpAlertThresholdMillis, additionalInfo.get()),
              Color.YELLOW));
    }
  }
}
