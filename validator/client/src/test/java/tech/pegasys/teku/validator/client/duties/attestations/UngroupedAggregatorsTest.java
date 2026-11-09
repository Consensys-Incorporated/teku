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

package tech.pegasys.teku.validator.client.duties.attestations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDutyAggregators.CommitteeAggregator;

class UngroupedAggregatorsTest extends AggregationDutyAggregatorsTest {

  @Override
  AggregationDutyAggregators getAggregator() {
    return new UngroupedAggregators();
  }

  @Test
  public void addAggregatorsForSameCommitteeDoNotGroupThem() {
    final CommitteeAggregator committeeAggregator1 = randomCommitteeAggregator(1);
    final CommitteeAggregator committeeAggregator2 = randomCommitteeAggregator(1);
    final CommitteeAggregator committeeAggregator3 = randomCommitteeAggregator(2);

    addValidator(committeeAggregator1);
    addValidator(committeeAggregator2);
    addValidator(committeeAggregator3);

    final List<CommitteeAggregator> aggregators =
        ((UngroupedAggregators) aggregationDutyAggregators).getAggregators();

    assertThat(aggregators).hasSize(3);
    assertThat(aggregators)
        .contains(committeeAggregator1, committeeAggregator2, committeeAggregator3);
  }
}
