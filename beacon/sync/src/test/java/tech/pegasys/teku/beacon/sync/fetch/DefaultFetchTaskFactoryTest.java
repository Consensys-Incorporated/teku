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

package tech.pegasys.teku.beacon.sync.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class DefaultFetchTaskFactoryTest {

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Eth2Peer> eth2Network = mock(P2PNetwork.class);

  private final DefaultFetchTaskFactory fetchTaskFactory = new DefaultFetchTaskFactory(eth2Network);

  @Test
  public void createsFetchBlockTask() {
    final FetchBlockTask task = fetchTaskFactory.createFetchBlockTask(Bytes32.ZERO);
    assertThat(task).isNotNull();
  }

  @Test
  public void createsFetchBlobSidecarTask() {
    final FetchBlobSidecarTask task =
        fetchTaskFactory.createFetchBlobSidecarTask(new BlobIdentifier(Bytes32.ZERO, UInt64.ZERO));
    assertThat(task).isNotNull();
  }

  @Test
  public void createsFetchExecutionPayloadTask() {
    final FetchExecutionPayloadTask task =
        fetchTaskFactory.createFetchExecutionPayloadTask(Bytes32.ZERO);
    assertThat(task).isNotNull();
  }
}
