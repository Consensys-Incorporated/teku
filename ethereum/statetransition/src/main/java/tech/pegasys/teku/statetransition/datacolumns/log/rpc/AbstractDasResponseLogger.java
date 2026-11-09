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

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

abstract class AbstractDasResponseLogger<TRequest>
    extends AbstractResponseLogger<TRequest, DataColumnSidecar, DataColumnSlotAndIdentifier> {
  private static final Logger LOG = LogManager.getLogger(DasReqRespLogger.class);

  protected static final UInt64 UNKNOWN_SLOT = UInt64.MAX_VALUE;

  private final int columnCount = 128;
  private final int maxResponseLongStringLength = 512;

  public AbstractDasResponseLogger(
      final TimeProvider timeProvider,
      final Direction direction,
      final LoggingPeerId peerId,
      final TRequest request) {
    super(timeProvider, direction, peerId, request, DataColumnSlotAndIdentifier::fromDataColumn);
  }

  protected abstract int requestedMaxCount();

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  protected String responseString(
      final List<DataColumnSlotAndIdentifier> responses, final Optional<Throwable> result) {
    final String responsesString;
    if (responses.isEmpty()) {
      responsesString = "<empty>";
    } else if (responses.size() == requestedMaxCount()) {
      responsesString = "<all requested>";
    } else {
      responsesString = columnIdsToString(responses);
    }

    if (result.isEmpty()) {
      return responsesString;
    } else if (responses.isEmpty()) {
      return "error: " + result.get();
    } else {
      return responsesString + ", error: " + result.get();
    }
  }

  protected String columnIdsToString(final List<DataColumnSlotAndIdentifier> responses) {
    final String longString = columnIdsToStringLong(responses);
    if (longString.length() <= maxResponseLongStringLength) {
      return longString;
    } else {
      return columnIdsToStringShorter(responses);
    }
  }

  protected String columnIdsToStringLong(final List<DataColumnSlotAndIdentifier> responses) {
    return responses.size()
        + " columns: "
        + mapGroupingByBlock(
                responses,
                (blockId, columns) ->
                    blockIdString(blockId) + " colIdxs: " + blockResponsesToString(columns))
            .collect(Collectors.joining(", "));
  }

  protected String columnIdsToStringShorter(final List<DataColumnSlotAndIdentifier> responses) {

    return mapGroupingByBlock(
            responses, (blockId, columns) -> blockIdString(blockId) + ": " + columns.size())
        .collect(Collectors.joining(", "));
  }

  protected <R> Stream<R> mapGroupingByBlock(
      final List<DataColumnSlotAndIdentifier> responses,
      final BiFunction<SlotAndBlockRoot, List<DataColumnSlotAndIdentifier>, R> mapper) {
    SortedMap<SlotAndBlockRoot, List<DataColumnSlotAndIdentifier>> responsesByBlock =
        new TreeMap<>(
            responses.stream()
                .collect(Collectors.groupingBy(AbstractDasResponseLogger::blockIdFromColumnId)));
    return responsesByBlock.entrySet().stream()
        .map(entry -> mapper.apply(entry.getKey(), entry.getValue()));
  }

  protected String blockResponsesToString(final List<DataColumnSlotAndIdentifier> responses) {
    return StringifyUtil.columnIndicesToString(
        responses.stream().map(it -> it.columnIndex().intValue()).toList(), columnCount);
  }

  private static String blockIdString(final SlotAndBlockRoot blockId) {
    if (blockId.getSlot().equals(UNKNOWN_SLOT)) {
      return blockId.getBlockRoot().toHexString();
    } else {
      return "#"
          + blockId.getSlot()
          + " (0x"
          + LogFormatter.formatAbbreviatedHashRoot(blockId.getBlockRoot())
          + ")";
    }
  }

  private static SlotAndBlockRoot blockIdFromColumnId(final DataColumnSlotAndIdentifier columnId) {
    return new SlotAndBlockRoot(columnId.slot(), columnId.blockRoot());
  }
}
