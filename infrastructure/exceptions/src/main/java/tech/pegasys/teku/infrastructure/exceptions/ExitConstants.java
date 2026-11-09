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

package tech.pegasys.teku.infrastructure.exceptions;

/**
 * Constants to be used as a parameter for {@link System#exit(int)} to perform shutdown of the Teku,
 * not for use in commands
 */
public class ExitConstants {
  public static final int SUCCESS_EXIT_CODE = 0;
  // restart could help
  public static final int ERROR_EXIT_CODE = 1;
  // restart will not help
  public static final int FATAL_EXIT_CODE = 2;
}
