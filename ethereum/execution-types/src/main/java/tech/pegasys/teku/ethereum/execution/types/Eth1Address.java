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

package tech.pegasys.teku.ethereum.execution.types;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isMixedCase;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class Eth1Address extends Bytes20 {
  public static final DeserializableTypeDefinition<Eth1Address> ETH1ADDRESS_TYPE =
      DeserializableTypeDefinition.string(Eth1Address.class)
          .formatter(Eth1Address::toHexString)
          .parser(Eth1Address::fromHexString)
          .example("0x1Db3439a222C519ab44bb1144fC28167b4Fa6EE6")
          .description("Hex encoded deposit contract address with 0x prefix")
          .format("byte")
          .build();
  private static final String ZERO_ADDRESS_STRING = "0x0000000000000000000000000000000000000000";
  private static final int HEX_ADDRESS_STRING_LENGTH = ZERO_ADDRESS_STRING.length();

  public static final Eth1Address ZERO = new Eth1Address(ZERO_ADDRESS_STRING);

  private final String encodedAddress;

  private Eth1Address(final String value) {
    super(Bytes.fromHexString(value));
    String valueWithPrefix = value;
    if (!value.startsWith("0x")) {
      valueWithPrefix = "0x" + value;
    }
    this.encodedAddress = toChecksumAddress(valueWithPrefix);
    validate(valueWithPrefix);
  }

  private Eth1Address(final Bytes bytes) {
    super(bytes);
    final String value = bytes.toHexString();
    this.encodedAddress = toChecksumAddress(value);
    validate(value);
  }

  private void validate(final String value) {
    if (isMixedCase(value.substring("0x".length()))) {
      checkArgument(
          value.equals(encodedAddress),
          "Eth1Address fails checksum:\n got: %s\n exp: %s",
          value,
          encodedAddress);
    }
  }

  public static Eth1Address fromBytes(final Bytes value) {
    return new Eth1Address(value);
  }

  public static Eth1Address fromHexString(final String value) {
    try {
      return new Eth1Address(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid Ethereum Address: " + ex.getMessage(), ex);
    }
  }

  /**
   * Produce an address with a mixed-case checksum as defined by <a
   * href="https://eips.ethereum.org/EIPS/eip-55">EIP-55</a>.
   *
   * @param value The string representation of an Ethereum address.
   * @return The encoded address with mixed-case checksum.
   */
  private static String toChecksumAddress(final String value) {
    final String address = value.replace("0x", "").toLowerCase(Locale.ROOT);
    final String hashString =
        Hash.keccak256(Bytes.wrap(address.getBytes(StandardCharsets.US_ASCII)))
            .toString()
            .replace("0x", "");
    final StringBuilder ret = new StringBuilder(HEX_ADDRESS_STRING_LENGTH).append("0x");
    for (int i = 0; i < address.length(); i++) {
      final String letter = String.valueOf(hashString.charAt(i));
      if (Integer.parseInt(letter, 16) >= 8) {
        ret.append(Character.toUpperCase(address.charAt(i)));
      } else {
        ret.append(address.charAt(i));
      }
    }
    return ret.toString();
  }

  @Override
  public String toString() {
    return encodedAddress;
  }

  @Override
  public String toHexString() {
    return encodedAddress;
  }

  @Override
  public String toUnprefixedHexString() {
    return encodedAddress.substring(2);
  }
}
