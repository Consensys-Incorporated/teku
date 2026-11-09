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

package tech.pegasys.teku.reference.deneb.merkle_proof;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.ethtests.finder.MerkleProofTestFinder.PROOF_DATA_FILE;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchemaFulu;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class SingleMerkleProofTestExecutor implements TestExecutor {
  private static final Pattern TEST_NAME_PATTERN = Pattern.compile("(.+)/(.+)");
  private static final String OBJECT_SSZ_FILE = "object.ssz_snappy";

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {

    final Matcher matcher = TEST_NAME_PATTERN.matcher(testDefinition.getTestName());
    if (!matcher.find()) {
      throw new RuntimeException(
          "Can't extract object and proof type from " + testDefinition.getTestName());
    }

    final String objectType = matcher.group(1);
    final String proofType = matcher.group(2);

    final Data data = loadDataFile(testDefinition, Data.class);

    switch (objectType) {
      case "BeaconBlockBody" -> runBeaconBlockBodyTest(testDefinition, proofType, data);
      default -> throw new RuntimeException("Unknown object type " + objectType);
    }
  }

  protected <T> T loadDataFile(final TestDefinition testDefinition, final Class<T> type)
      throws IOException {
    String dataFile =
        testDefinition.getTestName().endsWith(".yaml")
            ? testDefinition.getTestName()
            : PROOF_DATA_FILE;
    return TestDataUtils.loadYaml(testDefinition, dataFile, type);
  }

  private static class Data {
    @JsonProperty(value = "leaf", required = true)
    private String leaf;

    @JsonProperty(value = "leaf_index", required = true)
    private int leafIndex;

    @JsonProperty(value = "branch", required = true)
    private List<String> branch;
  }

  void runBeaconBlockBodyTest(
      final TestDefinition testDefinition, final String proofType, final Data data) {
    final BeaconBlockBody beaconBlockBody =
        TestDataUtils.loadSsz(
            testDefinition,
            OBJECT_SSZ_FILE,
            testDefinition.getSpec().getGenesisSchemaDefinitions().getBeaconBlockBodySchema());
    // Deneb
    if (proofType.startsWith("blob_kzg_commitment_merkle_proof")) {
      runBlobKzgCommitmentMerkleProofTest(testDefinition, data, beaconBlockBody);
      // Fulu
    } else if (proofType.startsWith("blob_kzg_commitments_merkle_proof")) {
      runBlobKzgCommitmentsMerkleProofTest(testDefinition, data, beaconBlockBody);
    } else {
      throw new RuntimeException("Unknown proof type " + proofType);
    }
  }

  private void runBlobKzgCommitmentMerkleProofTest(
      final TestDefinition testDefinition, final Data data, final BeaconBlockBody beaconBlockBody) {
    final Predicates predicates = new Predicates(testDefinition.getSpec().getGenesisSpecConfig());
    final Bytes32 kzgCommitmentHash = Bytes32.fromHexString(data.leaf);

    // Forward check
    assertThat(
            predicates.isValidMerkleBranch(
                kzgCommitmentHash,
                createKzgCommitmentMerkleProofBranchFromData(testDefinition, data.branch),
                getKzgCommitmentInclusionProofDepth(testDefinition),
                data.leafIndex,
                beaconBlockBody.hashTreeRoot()))
        .isTrue();

    // Find which commitment is in puzzle by hash root
    final SszList<SszKZGCommitment> sszKZGCommitments =
        beaconBlockBody.getOptionalBlobKzgCommitments().orElseThrow();
    Optional<Integer> kzgCommitmentIndexFound = Optional.empty();
    for (int i = 0; i < sszKZGCommitments.size(); ++i) {
      if (sszKZGCommitments.get(i).hashTreeRoot().equals(kzgCommitmentHash)) {
        kzgCommitmentIndexFound = Optional.of(i);
        break;
      }
    }
    assertThat(kzgCommitmentIndexFound).isPresent();
    final UInt64 kzgCommitmentIndex = UInt64.valueOf(kzgCommitmentIndexFound.get());

    // Verify 2 MiscHelpersDeneb helpers
    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(testDefinition.getSpec().getGenesisSpec().miscHelpers());
    assertThat(miscHelpersDeneb.getBlobSidecarKzgCommitmentGeneralizedIndex(kzgCommitmentIndex))
        .isEqualTo(data.leafIndex);
    assertThat(
            miscHelpersDeneb.computeBlobKzgCommitmentInclusionProof(
                kzgCommitmentIndex, beaconBlockBody))
        .isEqualTo(data.branch.stream().map(Bytes32::fromHexString).toList());
  }

  private void runBlobKzgCommitmentsMerkleProofTest(
      final TestDefinition testDefinition, final Data data, final BeaconBlockBody beaconBlockBody) {
    final Predicates predicates = new Predicates(testDefinition.getSpec().getGenesisSpecConfig());
    final Bytes32 kzgCommitmentsHash = Bytes32.fromHexString(data.leaf);

    // Forward check
    assertThat(
            predicates.isValidMerkleBranch(
                kzgCommitmentsHash,
                createKzgCommitmentsMerkleProofBranchFromData(testDefinition, data.branch),
                getKzgCommitmentsInclusionProofDepth(testDefinition),
                data.leafIndex,
                beaconBlockBody.hashTreeRoot()))
        .isTrue();

    // Verify 2 MiscHelpersFulu helpers
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(testDefinition.getSpec().getGenesisSpec().miscHelpers());
    assertThat(miscHelpersFulu.getBlockBodyKzgCommitmentsGeneralizedIndex())
        .isEqualTo(data.leafIndex);
    assertThat(miscHelpersFulu.computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody))
        .isEqualTo(data.branch.stream().map(Bytes32::fromHexString).toList());
  }

  private SszBytes32Vector createKzgCommitmentMerkleProofBranchFromData(
      final TestDefinition testDefinition, final List<String> branch) {
    final SszBytes32VectorSchema<?> kzgCommitmentInclusionProofSchema =
        testDefinition
            .getSpec()
            .getGenesisSchemaDefinitions()
            .toVersionDeneb()
            .orElseThrow()
            .getBlobSidecarSchema()
            .getKzgCommitmentInclusionProofSchema();
    return kzgCommitmentInclusionProofSchema.createFromElements(
        branch.stream().map(Bytes32::fromHexString).map(SszBytes32::of).toList());
  }

  private int getKzgCommitmentInclusionProofDepth(final TestDefinition testDefinition) {
    return SpecConfigDeneb.required(testDefinition.getSpec().getGenesisSpecConfig())
        .getKzgCommitmentInclusionProofDepth();
  }

  private SszBytes32Vector createKzgCommitmentsMerkleProofBranchFromData(
      final TestDefinition testDefinition, final List<String> branch) {
    final SszBytes32VectorSchema<?> kzgCommitmentsInclusionProofSchema =
        DataColumnSidecarSchemaFulu.required(
                SchemaDefinitionsFulu.required(
                        testDefinition.getSpec().getGenesisSchemaDefinitions())
                    .getDataColumnSidecarSchema())
            .getKzgCommitmentsInclusionProofSchema();
    return kzgCommitmentsInclusionProofSchema.createFromElements(
        branch.stream().map(Bytes32::fromHexString).map(SszBytes32::of).toList());
  }

  private int getKzgCommitmentsInclusionProofDepth(final TestDefinition testDefinition) {
    return SpecConfigFulu.required(testDefinition.getSpec().getGenesisSpecConfig())
        .getKzgCommitmentsInclusionProofDepth()
        .intValue();
  }
}
