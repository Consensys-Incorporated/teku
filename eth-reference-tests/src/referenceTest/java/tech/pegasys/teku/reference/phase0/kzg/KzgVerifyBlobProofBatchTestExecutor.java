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
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;

public class KzgVerifyBlobProofBatchTestExecutor extends KzgTestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition, final KZG kzg) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final Boolean expectedVerificationResult = data.getOutput();
    Boolean actualVerificationResult;
    try {
      final List<Bytes> blobs = data.getInput().getBlobs();
      final List<KZGCommitment> commitments = data.getInput().getCommitments();
      final List<KZGProof> proofs = data.getInput().getProofs();
      actualVerificationResult = kzg.verifyBlobKzgProofBatch(blobs, commitments, proofs);
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
      @JsonProperty(value = "blobs", required = true)
      private List<String> blobs;

      @JsonProperty(value = "commitments", required = true)
      private List<String> commitments;

      @JsonProperty(value = "proofs", required = true)
      private List<String> proofs;

      public List<Bytes> getBlobs() {
        return blobs.stream().map(Bytes::fromHexString).toList();
      }

      public List<KZGCommitment> getCommitments() {
        return commitments.stream().map(KZGCommitment::fromHexString).toList();
      }

      public List<KZGProof> getProofs() {
        return proofs.stream().map(KZGProof::fromHexString).toList();
      }
    }
  }
}
