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

package tech.pegasys.teku.networking.eth2.gossip;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.networking.eth2.gossip.subnets.DataColumnSidecarSubnetSubscriptions;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;

public class DataColumnSidecarGossipManager implements GossipManager {
  private final DataColumnSidecarSubnetSubscriptions subnetSubscriptions;
  private final DasGossipLogger dasGossipLogger;
  private final Supplier<Boolean> isSuperNodeSupplier;

  public DataColumnSidecarGossipManager(
      final DataColumnSidecarSubnetSubscriptions dataColumnSidecarSubnetSubscriptions,
      final DasGossipLogger dasGossipLogger,
      final Supplier<Boolean> isSuperNodeSupplier) {
    this.subnetSubscriptions = dataColumnSidecarSubnetSubscriptions;
    this.dasGossipLogger = dasGossipLogger;
    this.isSuperNodeSupplier = isSuperNodeSupplier;
  }

  public void publish(final DataColumnSidecar dataColumnSidecar) {
    subnetSubscriptions
        .gossip(dataColumnSidecar)
        .finish(
            __ -> dasGossipLogger.onPublish(dataColumnSidecar, Optional.empty()),
            error -> dasGossipLogger.onPublish(dataColumnSidecar, Optional.of(error)));
  }

  public void subscribeToSubnetId(final int subnetId) {
    subnetSubscriptions.subscribeToSubnetId(subnetId);
    dasGossipLogger.onDataColumnSubnetSubscribe(subnetId);
  }

  public void unsubscribeFromSubnetId(final int subnetId) {
    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);
    dasGossipLogger.onDataColumnSubnetUnsubscribe(subnetId);
  }

  @Override
  public void subscribe() {
    subnetSubscriptions.subscribe();
  }

  @Override
  public void unsubscribe() {
    subnetSubscriptions.unsubscribe();
  }

  /**
   * @see tech.pegasys.teku.statetransition.datacolumns.util.SuperNodeSupplier for details on super
   *     node functionality. Data column sidecar gossip is enabled during optimistic sync if the
   *     node is a super node.
   */
  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return isSuperNodeSupplier.get();
  }
}
