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
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRangeReqResp;

public class LoggingBatchDataColumnsByRangeReqResp implements BatchDataColumnsByRangeReqResp {

  private final BatchDataColumnsByRangeReqResp delegate;
  private final DasReqRespLogger logger;

  public LoggingBatchDataColumnsByRangeReqResp(
      final BatchDataColumnsByRangeReqResp delegate, final DasReqRespLogger logger) {
    this.delegate = delegate;
    this.logger = logger;
  }

  @Override
  public AsyncStream<DataColumnSidecar> requestDataColumnSidecarsByRange(
      final UInt256 nodeId,
      final UInt64 startSlot,
      final int slotCount,
      final List<UInt64> columnIndices) {
    final ReqRespResponseLogger<DataColumnSidecar> responseLogger =
        logger
            .getDataColumnSidecarsByRangeLogger()
            .onOutboundRequest(
                LoggingPeerId.fromNodeId(nodeId),
                new DasReqRespLogger.ByRangeRequest(startSlot, slotCount, columnIndices));
    return delegate
        .requestDataColumnSidecarsByRange(nodeId, startSlot, slotCount, columnIndices)
        .peek(responseLogger.asAsyncStreamVisitor());
  }

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    return delegate.getCurrentRequestLimit(nodeId);
  }
}
