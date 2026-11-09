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

package tech.pegasys.teku.validator.client.duties;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

/** Batches requests to subscribe to beacon committees. */
public class BeaconCommitteeSubscriptions {

  private static final Logger LOG = LogManager.getLogger();

  private final Queue<CommitteeSubscriptionRequest> pendingRequests = new ConcurrentLinkedQueue<>();
  private final ValidatorApiChannel validatorApiChannel;

  public BeaconCommitteeSubscriptions(final ValidatorApiChannel validatorApiChannel) {
    this.validatorApiChannel = validatorApiChannel;
  }

  public void subscribeToBeaconCommittee(final CommitteeSubscriptionRequest request) {
    pendingRequests.add(request);
  }

  public void sendRequests() {
    final List<CommitteeSubscriptionRequest> requestsToSend = new ArrayList<>();
    for (CommitteeSubscriptionRequest request = pendingRequests.poll();
        request != null;
        request = pendingRequests.poll()) {
      requestsToSend.add(request);
    }
    if (requestsToSend.isEmpty()) {
      return;
    }
    validatorApiChannel
        .subscribeToBeaconCommittee(requestsToSend)
        .finish(
            error -> LOG.error("Failed to subscribe to beacon committee for aggregation.", error));
  }
}
