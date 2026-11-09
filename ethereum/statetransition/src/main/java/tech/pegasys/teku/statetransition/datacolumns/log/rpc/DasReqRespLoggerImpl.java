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
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;

class DasReqRespLoggerImpl implements DasReqRespLogger {

  private final TimeProvider timeProvider;

  private final ReqRespMethodLogger<List<DataColumnsByRootIdentifier>, DataColumnSidecar>
      byRootMethodLogger =
          new ReqRespMethodLogger<>() {
            @Override
            public ReqRespResponseLogger<DataColumnSidecar> onInboundRequest(
                final LoggingPeerId fromPeer, final List<DataColumnsByRootIdentifier> request) {
              return new DasByRootResponseLogger(
                  timeProvider, AbstractResponseLogger.Direction.INBOUND, fromPeer, request);
            }

            @Override
            public ReqRespResponseLogger<DataColumnSidecar> onOutboundRequest(
                final LoggingPeerId toPeer, final List<DataColumnsByRootIdentifier> request) {
              return new DasByRootResponseLogger(
                  timeProvider, AbstractResponseLogger.Direction.OUTBOUND, toPeer, request);
            }
          };

  private final ReqRespMethodLogger<ByRangeRequest, DataColumnSidecar> byRangeMethodLogger =
      new ReqRespMethodLogger<>() {
        @Override
        public ReqRespResponseLogger<DataColumnSidecar> onInboundRequest(
            final LoggingPeerId fromPeer, final ByRangeRequest request) {
          return new DasByRangeResponseLogger(
              timeProvider, AbstractResponseLogger.Direction.INBOUND, fromPeer, request);
        }

        @Override
        public ReqRespResponseLogger<DataColumnSidecar> onOutboundRequest(
            final LoggingPeerId toPeer, final ByRangeRequest request) {
          return new DasByRangeResponseLogger(
              timeProvider, AbstractResponseLogger.Direction.OUTBOUND, toPeer, request);
        }
      };

  public DasReqRespLoggerImpl(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  @Override
  public ReqRespMethodLogger<List<DataColumnsByRootIdentifier>, DataColumnSidecar>
      getDataColumnSidecarsByRootLogger() {
    return byRootMethodLogger;
  }

  @Override
  public ReqRespMethodLogger<ByRangeRequest, DataColumnSidecar>
      getDataColumnSidecarsByRangeLogger() {
    return byRangeMethodLogger;
  }
}
