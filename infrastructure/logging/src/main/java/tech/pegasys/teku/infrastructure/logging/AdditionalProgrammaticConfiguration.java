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

import java.io.IOException;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.status.StatusLogger;

public class AdditionalProgrammaticConfiguration extends XmlConfiguration {

  public AdditionalProgrammaticConfiguration(
      final LoggerContext loggerContext, final ConfigurationSource configSource) {
    super(loggerContext, configSource);
  }

  @Override
  protected void doConfigure() {
    super.doConfigure();

    LoggingConfigurator.addLoggersProgrammatically(this);
  }

  @Override
  public Configuration reconfigure() {
    final Configuration refreshedParent = super.reconfigure();

    if (refreshedParent != null
        && AbstractConfiguration.class.isAssignableFrom(refreshedParent.getClass())) {

      try {
        final AdditionalProgrammaticConfiguration refreshed =
            new AdditionalProgrammaticConfiguration(
                refreshedParent.getLoggerContext(),
                refreshedParent.getConfigurationSource().resetInputStream());
        LoggingConfigurator.addLoggersProgrammatically(refreshed);
        return refreshed;
      } catch (final IOException e) {
        StatusLogger.getLogger().error("Failed to reload the Log4j2 Xml configuration file", e);
      }
    }

    StatusLogger.getLogger().warn("Cannot programmatically reconfigure loggers");
    return refreshedParent;
  }
}
