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

package tech.pegasys.teku.networking.p2p.mock;

import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class MockNodeIdGenerator {
  private final AtomicInteger nextId = new AtomicInteger(0);

  public NodeId next() {
    return new MockNodeId(nextId.incrementAndGet());
  }
}
