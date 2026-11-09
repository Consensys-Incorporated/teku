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

package tech.pegasys.teku.beacon.pow.api;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;

public class TrackingEth1EventsChannel implements Eth1EventsChannel {
  private final List<Object> orderedList = new ArrayList<>();
  private MinGenesisTimeBlockEvent genesis;

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    orderedList.add(event);
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    genesis = event;
    orderedList.add(event);
  }

  public MinGenesisTimeBlockEvent getGenesis() {
    return genesis;
  }

  public List<Object> getOrderedList() {
    return orderedList;
  }
}
