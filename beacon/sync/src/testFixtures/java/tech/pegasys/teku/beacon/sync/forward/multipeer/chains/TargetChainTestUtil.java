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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import java.util.stream.Stream;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public class TargetChainTestUtil {
  public static TargetChain chainWith(final SlotAndBlockRoot chainHead, final SyncSource... peers) {
    final TargetChain chain = new TargetChain(chainHead);
    Stream.of(peers).forEach(chain::addPeer);
    return chain;
  }
}
