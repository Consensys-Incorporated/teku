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
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

class DasByRangeResponseLogger extends AbstractDasResponseLogger<DasReqRespLogger.ByRangeRequest> {
  public DasByRangeResponseLogger(
      final TimeProvider timeProvider,
      final Direction direction,
      final LoggingPeerId peerId,
      final DasReqRespLogger.ByRangeRequest request) {
    super(timeProvider, direction, peerId, request);
  }

  @Override
  protected void responseComplete(
      final List<Timestamped<DataColumnSlotAndIdentifier>> responseSummaries,
      final Optional<Throwable> result) {

    final List<DataColumnSlotAndIdentifier> responseSummariesUnboxed =
        responseSummaries.stream().map(Timestamped::value).toList();
    final long curTime = timeProvider.getTimeInMillis().longValue();

    getLogger()
        .debug(
            "ReqResp {} {}, columns: {}/{} in {} ms{}, peer {}: request: {}, response: {}",
            direction,
            "data_column_sidecars_by_range",
            responseSummaries.size(),
            requestedMaxCount(),
            curTime - requestTime,
            result.isEmpty() ? "" : " with ERROR",
            peerId,
            requestToString(),
            responseString(responseSummariesUnboxed, result));
  }

  @Override
  protected int requestedMaxCount() {
    return request.slotCount() * request.columnIndices().size();
  }

  private String requestToString() {
    return "[startSlot = "
        + request.startSlot()
        + ", count = "
        + request.slotCount()
        + ", columns = "
        + StringifyUtil.toIntRangeString(
            request.columnIndices().stream().map(UInt64::intValue).toList())
        + "]";
  }
}
