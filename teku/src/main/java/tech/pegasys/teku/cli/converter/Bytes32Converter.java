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

import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class Bytes32Converter implements ITypeConverter<Bytes32> {
  @Override
  public Bytes32 convert(final String value) {
    try {
      return Bytes32.fromHexStringStrict(value);
    } catch (final NumberFormatException e) {
      throw new TypeConversionException(
          "Invalid format: must be a 32 bytes in hex format but was " + value);
    }
  }
}
