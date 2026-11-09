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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class CheckpointConverter implements CommandLine.ITypeConverter<Checkpoint> {
  public static final String CHECKPOINT_ERROR =
      "Checkpoint arguments should be formatted as <BLOCK_ROOT>:<EPOCH_NUMBER> where blockRoot is a hex-encoded 32 byte value and epochNumber is a number in decimal format";

  @Override
  public Checkpoint convert(final String checkpointString) {
    try {
      checkNotNull(checkpointString);
      final String trimmed = checkpointString.trim();
      checkArgument(trimmed.length() > 0);
      List<String> parts = Splitter.on(':').splitToList(trimmed);
      checkArgument(parts.size() == 2, CHECKPOINT_ERROR);

      final Bytes32 blockRoot = parseBytes32(parts.get(0));
      final UInt64 epoch = parseNumber(parts.get(1));
      return new Checkpoint(epoch, blockRoot);
    } catch (Throwable e) {
      throw new CommandLine.TypeConversionException(CHECKPOINT_ERROR);
    }
  }

  private UInt64 parseNumber(final String value) {
    return UInt64.valueOf(value);
  }

  private Bytes32 parseBytes32(final String value) {
    return Bytes32.fromHexStringStrict(value);
  }
}
