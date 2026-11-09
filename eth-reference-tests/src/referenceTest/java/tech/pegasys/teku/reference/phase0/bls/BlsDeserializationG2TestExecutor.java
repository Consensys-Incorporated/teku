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

package tech.pegasys.teku.reference.phase0.bls;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class BlsDeserializationG2TestExecutor extends BlsTestExecutor {

  @Override
  protected void runTestImpl(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final String signature = data.input.getSignature();
    final boolean expectedResult = data.getOutput();

    assertThat(expectedResult).isEqualTo(validateSignature(signature));
  }

  private boolean validateSignature(final String signature) {
    try {
      return BLSSignature.fromBytesCompressed(Bytes.fromHexString(signature)).isValid();
    } catch (BlsException e) {
      return false;
    }
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private boolean output;

    public boolean getOutput() {
      return output;
    }
  }

  private static class Input {
    @JsonProperty(value = "signature", required = true)
    private String signature;

    public String getSignature() {
      return signature;
    }
  }
}
