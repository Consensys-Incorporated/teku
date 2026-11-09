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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;

public interface DasReqRespLogger {

  record ByRangeRequest(UInt64 startSlot, int slotCount, List<UInt64> columnIndices) {}

  static DasReqRespLogger create(final TimeProvider timeProvider) {
    return new DasReqRespLoggerImpl(timeProvider);
  }

  DasReqRespLogger NOOP =
      new DasReqRespLogger() {
        @Override
        public ReqRespMethodLogger<List<DataColumnsByRootIdentifier>, DataColumnSidecar>
            getDataColumnSidecarsByRootLogger() {
          return new NoopReqRespMethodLogger<>();
        }

        @Override
        public ReqRespMethodLogger<ByRangeRequest, DataColumnSidecar>
            getDataColumnSidecarsByRangeLogger() {
          return new NoopReqRespMethodLogger<>();
        }
      };

  ReqRespMethodLogger<List<DataColumnsByRootIdentifier>, DataColumnSidecar>
      getDataColumnSidecarsByRootLogger();

  ReqRespMethodLogger<ByRangeRequest, DataColumnSidecar> getDataColumnSidecarsByRangeLogger();
}
