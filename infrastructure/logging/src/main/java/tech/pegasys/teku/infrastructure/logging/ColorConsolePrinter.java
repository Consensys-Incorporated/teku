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

public class ColorConsolePrinter {

  private static final String RESET_CODE = "\u001B[0m";

  public enum Color {
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    PURPLE,
    CYAN,
    WHITE,
    GRAY
  }

  public static String print(final String message, final Color color) {
    return LoggingConfigurator.isColorEnabled() ? colorCode(color) + message + RESET_CODE : message;
  }

  private static String colorCode(final Color color) {
    return switch (color) {
      case BLACK -> "\u001b[30m";
      case RED -> "\u001b[31m";
      case GREEN -> "\u001b[32m";
      case YELLOW -> "\u001b[33m";
      case BLUE -> "\u001b[34m";
      case PURPLE -> "\u001b[35m";
      case CYAN -> "\u001b[36m";
      case WHITE -> "\u001b[37m";
      case GRAY -> "\u001b[90m";
    };
  }
}
