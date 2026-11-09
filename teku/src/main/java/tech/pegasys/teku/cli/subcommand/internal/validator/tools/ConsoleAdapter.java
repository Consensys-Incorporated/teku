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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import com.google.errorprone.annotations.FormatMethod;
import java.io.Console;

public class ConsoleAdapter {
  private final Console console = System.console();

  @SuppressWarnings("SystemConsoleNull") // https://errorprone.info/bugpattern/SystemConsoleNull
  public boolean isConsoleAvailable() {
    if (Runtime.version().feature() < 22) {
      return console != null;
    }
    try {
      return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  @FormatMethod
  public char[] readPassword(final String fmt, final Object... args) {
    return console.readPassword(fmt, args);
  }

  @FormatMethod
  public String readLine(final String fmt, final Object... args) {
    return console.readLine(fmt, args);
  }
}
