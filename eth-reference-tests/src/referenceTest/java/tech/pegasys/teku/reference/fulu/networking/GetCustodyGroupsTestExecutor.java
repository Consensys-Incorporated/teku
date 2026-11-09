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

package tech.pegasys.teku.reference.fulu.networking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

public class GetCustodyGroupsTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(testDefinition.getSpec().getGenesisSpec().miscHelpers());
    final Data data = loadDataFile(testDefinition, Data.class);

    final List<UInt64> calculatedCustodyGroups =
        miscHelpersFulu.getCustodyGroups(data.nodeId(), data.custodyGroupCount);

    assertThat(calculatedCustodyGroups).isEqualTo(data.expectedCustodyGroups());
  }

  private static class Data {

    @JsonProperty(value = "node_id", required = true)
    private String nodeId;

    @JsonProperty(value = "custody_group_count", required = true)
    private Integer custodyGroupCount;

    @JsonProperty(value = "result", required = true)
    private List<Integer> result;

    public UInt256 nodeId() {
      return UInt256.valueOf(new BigInteger(this.nodeId, 10));
    }

    public List<UInt64> expectedCustodyGroups() {
      return result.stream().map(UInt64::valueOf).collect(Collectors.toList());
    }
  }

  protected <T> T loadDataFile(final TestDefinition testDefinition, final Class<T> type)
      throws IOException {
    return TestDataUtils.loadYaml(testDefinition, "meta.yaml", type);
  }
}
