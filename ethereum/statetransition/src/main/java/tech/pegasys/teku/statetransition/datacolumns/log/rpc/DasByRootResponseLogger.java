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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

class DasByRootResponseLogger extends AbstractDasResponseLogger<List<DataColumnsByRootIdentifier>> {

  public DasByRootResponseLogger(
      final TimeProvider timeProvider,
      final Direction direction,
      final LoggingPeerId peerId,
      final List<DataColumnsByRootIdentifier> dataColumnIdentifiers) {
    super(timeProvider, direction, peerId, dataColumnIdentifiers);
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
            "data_column_sidecars_by_root",
            responseSummaries.size(),
            requestedMaxCount(),
            curTime - requestTime,
            result.isEmpty() ? "" : " with ERROR",
            peerId,
            requestToString(responseSummariesUnboxed),
            responseString(responseSummariesUnboxed, result));
  }

  @Override
  protected int requestedMaxCount() {
    return request.size();
  }

  protected String requestToString(final List<DataColumnSlotAndIdentifier> responses) {
    final Map<Bytes32, UInt64> blockRootToSlot =
        responses.stream()
            .collect(
                Collectors.toMap(
                    DataColumnSlotAndIdentifier::blockRoot,
                    DataColumnSlotAndIdentifier::slot,
                    (s1, s2) -> s1));
    final List<DataColumnSlotAndIdentifier> idsWithMaybeSlot =
        request.stream()
            .flatMap(
                it ->
                    it.getColumns().stream()
                        .map(
                            column ->
                                new DataColumnSlotAndIdentifier(
                                    blockRootToSlot.getOrDefault(it.getBlockRoot(), UNKNOWN_SLOT),
                                    it.getBlockRoot(),
                                    column)))
            .toList();

    return columnIdsToString(idsWithMaybeSlot);
  }
}
