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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class BlsSignTestExecutor extends BlsTestExecutor {

  @Override
  public void runTestImpl(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final BLSSecretKey privateKey = data.input.getPrivateKey();
    final Bytes message = data.input.getMessage();
    final BLSSignature expectedResult = data.getOutput();

    if (expectedResult == null) {
      assertThatThrownBy(() -> BLS.sign(privateKey, message))
          .isInstanceOf(IllegalArgumentException.class);
    } else {
      assertThat(BLS.sign(privateKey, message)).isEqualTo(expectedResult);
    }
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private String output;

    public BLSSignature getOutput() {
      return output != null ? BlsTests.parseSignature(output) : null;
    }
  }

  private static class Input {
    @JsonProperty(value = "privkey", required = true)
    private String privateKey;

    @JsonProperty(value = "message", required = true)
    private String message;

    public BLSSecretKey getPrivateKey() {
      return BLSSecretKey.fromBytes(Bytes32.fromHexString(privateKey));
    }

    public Bytes getMessage() {
      return Bytes.fromHexString(message);
    }
  }
}
