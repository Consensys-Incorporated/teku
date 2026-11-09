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

package tech.pegasys.teku.reference;

public enum BlsSetting {
  DEFAULT,
  REQUIRED,
  IGNORED;

  public static BlsSetting forCode(final int code) {
    return switch (code) {
      case 0 -> DEFAULT;
      case 1 -> REQUIRED;
      case 2 -> IGNORED;
      default -> throw new IllegalArgumentException("Unsupported bls setting: " + code);
    };
  }
}
