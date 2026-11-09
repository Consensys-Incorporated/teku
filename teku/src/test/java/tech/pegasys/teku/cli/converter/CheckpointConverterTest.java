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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class CheckpointConverterTest {
  private final CheckpointConverter parser = new CheckpointConverter();

  @Test
  public void convert_valid() {
    final Bytes32 blockRoot = Bytes32.fromHexStringLenient("0x0102");
    final String blockRootStr =
        "0x0000000000000000000000000000000000000000000000000000000000000102";
    final UInt64 epoch = UInt64.valueOf(11);
    final String epochStr = "11";
    final String input = blockRootStr + ":" + epochStr;

    Checkpoint result = parser.convert(input);
    assertThat(result.getRoot()).isEqualTo(blockRoot);
    assertThat(result.getEpoch()).isEqualTo(epoch);
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void convert_valid(final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.convert(input);
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void convert_valid_withLeadingWhitespace(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.convert("  " + input);
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void convert_valid_withTrailingWhitespace(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.convert(input + " ");
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void convert_valid_withLeadingAndTrailingWhitespace(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.convert(" " + input + "   ");
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getInvalidCheckpointArguments")
  public void convert_invalid(final String input) {
    assertThatThrownBy(() -> parser.convert(input))
        .isInstanceOf(CommandLine.TypeConversionException.class)
        .hasMessageContaining(CheckpointConverter.CHECKPOINT_ERROR);
  }

  public static Stream<Arguments> getValidCheckpointArguments() {
    List<String> blockRoots = validBlockRootValues();
    List<String> epochs = validEpochNumbers();

    final Stream.Builder<Arguments> builder = Stream.builder();
    for (String blockRoot : blockRoots) {
      for (String epoch : epochs) {
        final String input = blockRoot + ":" + epoch;
        builder.add(Arguments.of(input, blockRoot, epoch));
      }
    }

    return builder.build();
  }

  public static Stream<Arguments> getInvalidCheckpointArguments() {
    List<String> validBlockRoots = validBlockRootValues();
    List<String> validEpochs = validEpochNumbers();
    List<String> invalidBlockRoots = invalidBlockRootValues();
    List<String> invalidEpochs = invalidEpochNumbers();
    final Stream.Builder<Arguments> builder = Stream.builder();

    // Use valid args in reverse order
    for (String blockRoot : validBlockRoots) {
      for (String epoch : validEpochs) {
        builder.add(Arguments.of(epoch + ":" + blockRoot));
      }
    }
    // Valid epoch and invalid block root
    for (String blockRoot : invalidBlockRoots) {
      for (String epoch : validEpochs) {
        builder.add(Arguments.of(blockRoot + ":" + epoch));
      }
    }
    // Valid block root and invalid epoch
    for (String blockRoot : validBlockRoots) {
      for (String epoch : invalidEpochs) {
        builder.add(Arguments.of(blockRoot + ":" + epoch));
      }
    }
    // Invalid block root and epoch
    for (String blockRoot : invalidBlockRoots) {
      for (String epoch : invalidEpochs) {
        builder.add(Arguments.of(blockRoot + ":" + epoch));
      }
    }

    return builder.build();
  }

  private static List<String> validBlockRootValues() {
    return List.of(
        Bytes32.fromHexStringLenient("0x0102").toUnprefixedHexString(),
        Bytes32.fromHexStringLenient("0x0102").toHexString());
  }

  private static List<String> validEpochNumbers() {
    return List.of("11", "12345678");
  }

  private static List<String> invalidBlockRootValues() {
    return List.of(
        Bytes32.fromHexStringLenient("0x0102").toUnprefixedHexString().substring(0, 30),
        Bytes32.fromHexStringLenient("0x0102").toHexString().substring(0, 30),
        "");
  }

  private static List<String> invalidEpochNumbers() {
    return List.of("0xFF00", "");
  }
}
