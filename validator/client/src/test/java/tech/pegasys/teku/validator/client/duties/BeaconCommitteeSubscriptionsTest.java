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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

class BeaconCommitteeSubscriptionsTest {

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final BeaconCommitteeSubscriptions subscriptions =
      new BeaconCommitteeSubscriptions(validatorApiChannel);

  @Test
  void shouldNotSendRequestWhenThereAreNoRequestsToSend() {
    subscriptions.sendRequests();
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldSendPendingSubscriptions() {
    final List<CommitteeSubscriptionRequest> requests =
        List.of(
            new CommitteeSubscriptionRequest(1, 2, UInt64.valueOf(3), UInt64.valueOf(5), true),
            new CommitteeSubscriptionRequest(6, 7, UInt64.valueOf(8), UInt64.valueOf(9), false));
    requests.forEach(subscriptions::subscribeToBeaconCommittee);
    when(validatorApiChannel.subscribeToBeaconCommittee(requests)).thenReturn(SafeFuture.COMPLETE);

    subscriptions.sendRequests();

    verify(validatorApiChannel).subscribeToBeaconCommittee(requests);
    verifyNoMoreInteractions(validatorApiChannel);
  }
}
