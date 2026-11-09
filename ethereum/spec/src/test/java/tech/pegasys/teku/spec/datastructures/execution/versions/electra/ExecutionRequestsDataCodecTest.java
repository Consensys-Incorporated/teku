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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;

class ExecutionRequestsDataCodecTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final ExecutionRequestsSchemaElectra executionRequestsSchema =
      (ExecutionRequestsSchemaElectra)
          spec.forMilestone(SpecMilestone.ELECTRA)
              .getSchemaDefinitions()
              .toVersionElectra()
              .orElseThrow()
              .getExecutionRequestsSchema();
  private final ExecutionRequestsDataCodec codec =
      new ExecutionRequestsDataCodec(executionRequestsSchema);

  @Test
  public void codecRoundTrip() {
    final List<Bytes> expectedExecutionRequestsData =
        List.of(
            depositRequestListEncoded,
            withdrawalRequestsListEncoded,
            consolidationRequestsListEncoded);

    final List<Bytes> encodedExecutionRequestData =
        codec.encode(codec.decode(expectedExecutionRequestsData));
    assertThat(encodedExecutionRequestData).isEqualTo(expectedExecutionRequestsData);
  }

  @Test
  public void decodeExecutionRequestData() {
    final List<Bytes> executionRequestsData =
        List.of(
            depositRequestListEncoded,
            withdrawalRequestsListEncoded,
            consolidationRequestsListEncoded);

    final ExecutionRequests executionRequests = codec.decode(executionRequestsData);

    assertThat(executionRequests.getDeposits()).containsExactly(depositRequest1, depositRequest2);
    assertThat(executionRequests.getWithdrawals())
        .containsExactly(withdrawalRequest1, withdrawalRequest2);
    assertThat(executionRequests.getConsolidations()).containsExactly(consolidationRequest1);
  }

  @Test
  public void decodeExecutionRequestsDataWithNoRequests() {
    final ExecutionRequests executionRequests = codec.decode(List.of());

    assertThat(executionRequests.getDeposits()).isEmpty();
    assertThat(executionRequests.getWithdrawals()).isEmpty();
    assertThat(executionRequests.getConsolidations()).isEmpty();
  }

  @Test
  public void decodeExecutionRequestsDataWithOneRequestMissing() {
    final List<Bytes> executionRequestsData =
        List.of(depositRequestListEncoded, consolidationRequestsListEncoded);

    final ExecutionRequests executionRequests = codec.decode(executionRequestsData);

    assertThat(executionRequests.getDeposits()).containsExactly(depositRequest1, depositRequest2);
    assertThat(executionRequests.getWithdrawals()).isEmpty();
    assertThat(executionRequests.getConsolidations()).containsExactly(consolidationRequest1);
  }

  @Test
  public void decodeExecutionRequestsDataWithInvalidRequestType() {
    final Bytes invalidRequestType = Bytes.of(9);
    final Bytes invalidTypeEncodedList = Bytes.concatenate(invalidRequestType, Bytes.random(10));
    final List<Bytes> invalidExecutionRequestsData =
        List.of(depositRequestListEncoded, withdrawalRequestsListEncoded, invalidTypeEncodedList);

    assertThatThrownBy(() -> codec.decode(invalidExecutionRequestsData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid execution request type: " + invalidRequestType.toInt());
  }

  @Test
  public void decodeExecutionRequestDataWithRequestsNotOrderedInAscendingOrder() {
    final List<Bytes> invalidExecutionRequestsData =
        List.of(
            depositRequestListEncoded,
            consolidationRequestsListEncoded,
            withdrawalRequestsListEncoded);

    assertThatThrownBy(() -> codec.decode(invalidExecutionRequestsData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Execution requests are not in strictly ascending order");
  }

  @Test
  public void decodeExecutionRequestDataWithRepeatedRequestsOfSameType() {
    final List<Bytes> invalidExecutionRequestsData =
        List.of(
            depositRequestListEncoded,
            consolidationRequestsListEncoded,
            consolidationRequestsListEncoded);

    assertThatThrownBy(() -> codec.decode(invalidExecutionRequestsData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Execution requests are not in strictly ascending order");
  }

  @Test
  public void decodeExecutionRequestsDataWithEmptyRequestData() {
    // Element containing only the type by but no data
    final List<Bytes> invalidEmptyRequestsData = List.of(DepositRequest.REQUEST_TYPE_PREFIX);

    assertThatThrownBy(() -> codec.decode(invalidEmptyRequestsData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Empty data for request type 0");
  }

  @Test
  public void decodeExecutionRequestsDataWithOneInvalidEmptyRequestData() {
    final List<Bytes> invalidExecutionRequestsData =
        List.of(
            depositRequestListEncoded,
            withdrawalRequestsListEncoded,
            ConsolidationRequest.REQUEST_TYPE_PREFIX);

    assertThatThrownBy(() -> codec.decode(invalidExecutionRequestsData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Empty data for request type 2");
  }

  @Test
  public void encodeExecutionRequests() {
    final ExecutionRequests executionRequests =
        new ExecutionRequestsBuilderElectra(executionRequestsSchema)
            .deposits(List.of(depositRequest1, depositRequest2))
            .withdrawals(List.of(withdrawalRequest1, withdrawalRequest2))
            .consolidations(List.of(consolidationRequest1))
            .build();

    final List<Bytes> encodedRequests = codec.encode(executionRequests);

    assertThat(encodedRequests)
        .containsExactly(
            depositRequestListEncoded,
            withdrawalRequestsListEncoded,
            consolidationRequestsListEncoded);
  }

  @Test
  public void encodeWithWithOneEmptyRequestList() {
    final ExecutionRequests executionRequests =
        new ExecutionRequestsBuilderElectra(executionRequestsSchema)
            .deposits(List.of(depositRequest1, depositRequest2))
            .withdrawals(List.of())
            .consolidations(List.of(consolidationRequest1))
            .build();

    final List<Bytes> encodedRequests = codec.encode(executionRequests);

    assertThat(encodedRequests)
        .containsExactly(depositRequestListEncoded, consolidationRequestsListEncoded);
  }

  @Test
  public void encodeWithAllEmptyRequestLists() {
    final ExecutionRequests executionRequests =
        new ExecutionRequestsBuilderElectra(executionRequestsSchema)
            .deposits(List.of())
            .withdrawals(List.of())
            .consolidations(List.of())
            .build();

    final List<Bytes> encodedRequests = codec.encode(executionRequests);

    assertThat(encodedRequests).isEmpty();
  }

  // Examples taken from
  // https://github.com/ethereum/execution-apis/blob/main/src/engine/openrpc/methods/payload.yaml
  private final Bytes depositRequestListEncoded =
      Bytes.fromHexString(
          "0x0096a96086cff07df17668f35f7418ef8798079167e3f4f9b72ecde17b28226137cf454ab1dd20ef5d924786ab3483c2f9003f5102dabe0a27b1746098d1dc17a5d3fbd478759fea9287e4e419b3c3cef20100000000000000b1acdb2c4d3df3f1b8d3bfd33421660df358d84d78d16c4603551935f4b67643373e7eb63dcb16ec359be0ec41fee33b03a16e80745f2374ff1d3c352508ac5d857c6476d3c3bcf7e6ca37427c9209f17be3af5264c0e2132b3dd1156c28b4e9f000000000000000a5c85a60ba2905c215f6a12872e62b1ee037051364244043a5f639aa81b04a204c55e7cc851f29c7c183be253ea1510b001db70c485b6264692f26b8aeaab5b0c384180df8e2184a21a808a3ec8e86ca01000000000000009561731785b48cf1886412234531e4940064584463e96ac63a1a154320227e333fb51addc4a89b7e0d3f862d7c1fd4ea03bd8eb3d8806f1e7daf591cbbbb92b0beb74d13c01617f22c5026b4f9f9f294a8a7c32db895de3b01bee0132c9209e1f100000000000000");

  private final DepositRequest depositRequest1 =
      new DepositRequest(
          (DepositRequestSchema)
              executionRequestsSchema.getDepositRequestsSchema().getElementSchema(),
          BLSPublicKey.fromHexString(
              "0x96a96086cff07df17668f35f7418ef8798079167e3f4f9b72ecde17b28226137cf454ab1dd20ef5d924786ab3483c2f9"),
          Bytes32.fromHexString(
              "0x003f5102dabe0a27b1746098d1dc17a5d3fbd478759fea9287e4e419b3c3cef2"),
          UInt64.valueOf(1L),
          BLSSignature.fromBytesCompressed(
              Bytes.fromHexString(
                  "0xb1acdb2c4d3df3f1b8d3bfd33421660df358d84d78d16c4603551935f4b67643373e7eb63dcb16ec359be0ec41fee33b03a16e80745f2374ff1d3c352508ac5d857c6476d3c3bcf7e6ca37427c9209f17be3af5264c0e2132b3dd1156c28b4e9")),
          UInt64.valueOf(240L));

  private final DepositRequest depositRequest2 =
      new DepositRequest(
          (DepositRequestSchema)
              executionRequestsSchema.getDepositRequestsSchema().getElementSchema(),
          BLSPublicKey.fromHexString(
              "0xa5c85a60ba2905c215f6a12872e62b1ee037051364244043a5f639aa81b04a204c55e7cc851f29c7c183be253ea1510b"),
          Bytes32.fromHexString(
              "0x001db70c485b6264692f26b8aeaab5b0c384180df8e2184a21a808a3ec8e86ca"),
          UInt64.valueOf(1L),
          BLSSignature.fromBytesCompressed(
              Bytes.fromHexString(
                  "0x9561731785b48cf1886412234531e4940064584463e96ac63a1a154320227e333fb51addc4a89b7e0d3f862d7c1fd4ea03bd8eb3d8806f1e7daf591cbbbb92b0beb74d13c01617f22c5026b4f9f9f294a8a7c32db895de3b01bee0132c9209e1")),
          UInt64.valueOf(241L));

  private final Bytes withdrawalRequestsListEncoded =
      Bytes.fromHexString(
          "0x01a94f5374fce5edbc8e2a8697c15331677e6ebf0b85103a5617937691dfeeb89b86a80d5dc9e3c9d3a1a0e7ce311e26e0bb732eabaa47ffa288f0d54de28209a62a7d29d0000000000000000000000000000000000000000000000000000010f698daeed734da114470da559bd4b4c7259e1f7952555241dcbc90cf194a2ef676fc6005f3672fada2a3645edb297a75530100000000000000");

  private final WithdrawalRequest withdrawalRequest1 =
      new WithdrawalRequest(
          (WithdrawalRequestSchema)
              executionRequestsSchema.getWithdrawalRequestsSchema().getElementSchema(),
          Bytes20.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
          BLSPublicKey.fromHexString(
              "0x85103a5617937691dfeeb89b86a80d5dc9e3c9d3a1a0e7ce311e26e0bb732eabaa47ffa288f0d54de28209a62a7d29d0"),
          UInt64.valueOf(0L));

  private final WithdrawalRequest withdrawalRequest2 =
      new WithdrawalRequest(
          (WithdrawalRequestSchema)
              executionRequestsSchema.getWithdrawalRequestsSchema().getElementSchema(),
          Bytes20.fromHexString("0x00000000000000000000000000000000000010f6"),
          BLSPublicKey.fromHexString(
              "0x98daeed734da114470da559bd4b4c7259e1f7952555241dcbc90cf194a2ef676fc6005f3672fada2a3645edb297a7553"),
          UInt64.valueOf(1L));

  private final Bytes consolidationRequestsListEncoded =
      Bytes.fromHexString(
          "0x02a94f5374fce5edbc8e2a8697c15331677e6ebf0b85103a5617937691dfeeb89b86a80d5dc9e3c9d3a1a0e7ce311e26e0bb732eabaa47ffa288f0d54de28209a62a7d29d098daeed734da114470da559bd4b4c7259e1f7952555241dcbc90cf194a2ef676fc6005f3672fada2a3645edb297a7553");

  private final ConsolidationRequest consolidationRequest1 =
      new ConsolidationRequest(
          (ConsolidationRequestSchema)
              executionRequestsSchema.getConsolidationRequestsSchema().getElementSchema(),
          Bytes20.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b"),
          BLSPublicKey.fromHexString(
              "0x85103a5617937691dfeeb89b86a80d5dc9e3c9d3a1a0e7ce311e26e0bb732eabaa47ffa288f0d54de28209a62a7d29d0"),
          BLSPublicKey.fromHexString(
              "0x98daeed734da114470da559bd4b4c7259e1f7952555241dcbc90cf194a2ef676fc6005f3672fada2a3645edb297a7553"));
}
