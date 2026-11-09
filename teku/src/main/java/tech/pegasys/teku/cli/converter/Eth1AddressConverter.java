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
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;

public class Eth1AddressConverter implements CommandLine.ITypeConverter<Eth1Address> {
  @Override
  public Eth1Address convert(final String value) throws Exception {
    try {
      return Eth1Address.fromHexString(value);
    } catch (Exception e) {
      throw new TypeConversionException("Invalid format: " + e.getMessage());
    }
  }
}
