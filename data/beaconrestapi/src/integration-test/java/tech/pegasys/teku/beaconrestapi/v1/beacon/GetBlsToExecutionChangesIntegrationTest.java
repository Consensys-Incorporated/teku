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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlsToExecutionChanges;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class GetBlsToExecutionChangesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void beforeEach() {
    spec = TestSpecFactory.createMinimalCapella();
    dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.CAPELLA);
  }

  @Test
  void getBlsToExecutionChangesFromPoolReturnsOk() throws IOException {
    final List<SignedBlsToExecutionChange> expectedOperations =
        List.of(
            dataStructureUtil.randomSignedBlsToExecutionChange(),
            dataStructureUtil.randomSignedBlsToExecutionChange(),
            dataStructureUtil.randomSignedBlsToExecutionChange());
    when(validator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    for (SignedBlsToExecutionChange operation : expectedOperations) {
      assertThat(blsToExecutionChangePool.addRemote(operation, Optional.empty())).isCompleted();
    }

    Response response = getResponse(GetBlsToExecutionChanges.ROUTE);

    assertThat(response.code()).isEqualTo(SC_OK);
    checkResponseContainsExactly(getResponseData(response), expectedOperations);
  }

  @Test
  void getLocalBlsToExecutionChangesFromPool() throws IOException {
    final SignedBlsToExecutionChange localChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final SignedBlsToExecutionChange remoteChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    assertThat(blsToExecutionChangePool.addLocal(localChange)).isCompleted();
    assertThat(blsToExecutionChangePool.addRemote(remoteChange, Optional.empty())).isCompleted();

    Response response =
        getResponse(GetBlsToExecutionChanges.ROUTE, Map.of("locally_submitted", "true"));

    assertThat(response.code()).isEqualTo(SC_OK);
    checkResponseContainsExactly(getResponseData(response), List.of(localChange));
  }

  private void checkResponseContainsExactly(
      final JsonNode data, final List<SignedBlsToExecutionChange> blsChanges) {
    assertThat(data.size()).isEqualTo(blsChanges.size());

    final BlsToExecutionChangeSchema blsToExecutionChangeSchema =
        SchemaDefinitionsCapella.required(spec.getGenesisSchemaDefinitions())
            .getBlsToExecutionChangeSchema();
    final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema =
        SchemaDefinitionsCapella.required(spec.getGenesisSchemaDefinitions())
            .getSignedBlsToExecutionChangeSchema();
    final List<SignedBlsToExecutionChange> response = new ArrayList<>();
    for (int i = 0; i < blsChanges.size(); i++) {
      final BlsToExecutionChange message =
          blsToExecutionChangeSchema.create(
              UInt64.valueOf(data.get(i).get("message").get("validator_index").asText()),
              BLSPublicKey.fromHexString(
                  data.get(i).get("message").get("from_bls_pubkey").asText()),
              Bytes20.fromHexString(
                  data.get(i).get("message").get("to_execution_address").asText()));
      response.add(
          signedBlsToExecutionChangeSchema.create(
              message,
              BLSSignature.fromBytesCompressed(
                  Bytes.fromHexString(data.get(i).get("signature").asText()))));
    }
    assertThat(response).hasSameElementsAs(blsChanges);
  }
}
