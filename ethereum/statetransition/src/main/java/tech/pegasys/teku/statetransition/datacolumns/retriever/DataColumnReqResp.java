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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public interface DataColumnReqResp {

  SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      UInt256 nodeId, DataColumnSlotAndIdentifier columnIdentifier);

  void flush();

  int getCurrentRequestLimit(UInt256 nodeId);

  class DataColumnReqRespException extends RuntimeException {}

  class DasColumnNotAvailableException extends DataColumnReqRespException {}

  class DasPeerDisconnectedException extends DataColumnReqRespException {}
}
