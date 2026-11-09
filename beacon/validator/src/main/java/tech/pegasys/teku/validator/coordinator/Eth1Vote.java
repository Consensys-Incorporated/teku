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

package tech.pegasys.teku.validator.coordinator;

import java.util.Objects;

public class Eth1Vote implements Comparable<Eth1Vote> {

  private int vote = 0;
  private final int index;

  public Eth1Vote(final int index) {
    this.index = index;
  }

  public void incrementVotes() {
    vote++;
  }

  public int getVoteCount() {
    return vote;
  }

  // Greater vote number, or in case of a tie,
  // smallest index number wins
  @Override
  public int compareTo(final Eth1Vote eth1Vote) {
    if (this.vote > eth1Vote.vote) {
      return 1;
    } else if (this.vote < eth1Vote.vote) {
      return -1;
    } else {
      return Integer.compare(eth1Vote.index, this.index);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Eth1Vote eth1Vote = (Eth1Vote) o;
    return vote == eth1Vote.vote && index == eth1Vote.index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(vote, index);
  }
}
