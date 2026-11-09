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

import org.apache.commons.lang3.StringUtils;

public class SubCommandLogger {

  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  static {
    LoggingConfigurator.setColorEnabled(true);
  }

  public void exit(final int exitCode, final String message) {
    if (!StringUtils.isEmpty(message)) {
      if (exitCode == 0) {
        display(message);
      } else {
        error(message);
      }
    }

    System.exit(exitCode);
  }

  public void commandIsNotSafeForProduction() {
    error(
        print(
            "WARNING: This command is not safe for production use.",
            ColorConsolePrinter.Color.RED));
  }

  public void displayDeprecationWarning(final String message) {
    error(
        print(String.format("DEPRECATION WARNING: %s", message), ColorConsolePrinter.Color.YELLOW));
  }

  public void exit(final int exitCode, final String message, final Exception exception) {
    error(message, exception);
    System.exit(exitCode);
  }

  public void error(final String message) {
    System.err.println(message);
  }

  public void error(final String message, final Exception cause) {
    System.err.println(message);
    cause.printStackTrace();
  }

  public void display(final String message) {
    System.out.println(message);
  }

  public void generatingMockGenesis(final int validatorCount, final long genesisTime) {
    System.out.printf(
        "Generating mock genesis state for %s validators at genesis time %s %n",
        validatorCount, genesisTime);
  }

  public void storingGenesis(final String outputFile, final boolean isComplete) {
    if (isComplete) {
      System.out.println("Genesis state file saved: " + outputFile);
    } else {
      System.out.println("Saving genesis state to file: " + outputFile);
    }
  }

  public void sendDepositFailure(final Throwable cause) {
    System.err.printf(
        "Failed to send deposit transaction: %s : %s %n", cause.getClass(), cause.getMessage());
  }
}
