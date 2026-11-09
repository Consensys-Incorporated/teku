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

import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.units.bigints.UInt256;

public class MockDiscoveryNodeIdGenerator {

  private final AtomicReference<UInt256> nextId = new AtomicReference<>(UInt256.ZERO);

  public UInt256 next() {
    return nextId.getAndUpdate(id -> id.add(1));
  }
}
