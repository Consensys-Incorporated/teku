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

package tech.pegasys.teku.cli.converter;

import picocli.CommandLine;
import picocli.CommandLine.TypeConversionException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class UInt64Converter implements CommandLine.ITypeConverter<UInt64> {
  @Override
  public UInt64 convert(final String value) {
    try {
      return UInt64.valueOf(value);
    } catch (final NumberFormatException e) {
      throw new TypeConversionException("Invalid format: must be a numeric value but was " + value);
    }
  }
}
