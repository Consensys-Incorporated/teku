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

package tech.pegasys.teku.reference.phase0.kzg;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;

public class KzgVerifyBlobProofTestExecutor extends KzgTestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition, final KZG kzg) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);

    final Boolean expectedVerificationResult = data.getOutput();
    Boolean actualVerificationResult;
    try {
      final Bytes blob = data.getInput().getBlob();
      final KZGCommitment commitment = data.getInput().getCommitment();
      final KZGProof proof = data.getInput().getProof();
      actualVerificationResult = kzg.verifyBlobKzgProof(blob, commitment, proof);
    } catch (final RuntimeException ex) {
      actualVerificationResult = null;
    }
    assertThat(actualVerificationResult).isEqualTo(expectedVerificationResult);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private Boolean output;

    public Input getInput() {
      return input;
    }

    public Boolean getOutput() {
      return output;
    }

    private static class Input {
      @JsonProperty(value = "blob", required = true)
      private String blob;

      @JsonProperty(value = "commitment", required = true)
      private String commitment;

      @JsonProperty(value = "proof", required = true)
      private String proof;

      public Bytes getBlob() {
        return Bytes.fromHexString(blob);
      }

      public KZGCommitment getCommitment() {
        return KZGCommitment.fromHexString(commitment);
      }

      public KZGProof getProof() {
        return KZGProof.fromHexString(proof);
      }
    }
  }
}
