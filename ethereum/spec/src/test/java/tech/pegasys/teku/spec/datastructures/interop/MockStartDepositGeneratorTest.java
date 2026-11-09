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

package tech.pegasys.teku.spec.datastructures.interop;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;

class MockStartDepositGeneratorTest {

  private static final String[] PRIVATE_KEYS = {
    "0x25295F0D1D592A90B333E26E85149708208E9F8E8BC18F6C77BD62F8AD7A6866",
    "0x51D0B65185DB6989AB0B560D6DEED19C7EAD0E24B9B6372CBECB1F26BDFAD000",
    "0x315ED405FAFE339603932EEBE8DBFD650CE5DAFA561F6928664C75DB85F97857",
    "0x25B1166A43C109CB330AF8945D364722757C65ED2BFED5444B5A2F057F82D391",
    "0x3F5615898238C4C4F906B507EE917E9EA1BB69B93F1DBD11A34D229C3B06784B",
    "0x055794614BC85ED5436C1F5CAB586AAB6CA84835788621091F4F3B813761E7A8",
    "0x1023C68852075965E0F7352DEE3F76A84A83E7582C181C10179936C6D6348893",
    "0x3A941600DC41E5D20E818473B817A28507C23CDFDB4B659C15461EE5C71E41F5",
    "0x066E3BDC0415530E5C7FED6382D5C822C192B620203CF669903E1810A8C67D06",
    "0x2B3B88A041168A1C4CD04BDD8DE7964FD35238F95442DC678514F9DADB81EC34"
  };

  private static final String[] EXPECTED_DEPOSITS = {
    "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c00fad2a6bfb0e7f1f0f45460944fbd8dfa7f37da06a4d13b3983cc90bb46963b0040597307000000a95af8ff0f8c06af4d29aef05ce865f85f82df42b606008ec5b1bcb42b17ae47f4b78cdce1db31ce32d18f42a6b296b4014a2164981780e56b5a40d7723c27b8423173e58fa36f075078b177634f66351412b867c103f532aedd50bcd9b98446",
    "0xb89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b00ec7ef7780c9d151597924036262dd28dc60e1228f4da6fecf9d402cb3f3594004059730700000086a284728a10808b672dce8781ca2e21d91ab26f543edabc6728a136d6713b14cef605c877d8e635d014f8a43ca7588110a04450e6caead84b8eb86021cd6bd84fe2fb8007fdf1e53e7e4cb385a80e9836c2598403b21d67b062890d5a0b7027",
    "0xa3a32b0f8b4ddb83f1a0a853d81dd725dfe577d4f4c3db8ece52ce2b026eca84815c1a7e8e92a4de3d755733bf7e4a9b0036085c6c608e6d048505b04402568c36cce1e025722de44f9c3685a5c80fa60040597307000000abc2de7bc1a8d8493bef819c5f59f077adb5709573259db8f7853ff4d644efd6acb7f970a2cbe4207a8fab16b0824d6410a8070f8fd51b910b18958603e696536b65ffc6af9dcdab2a965557c18ce808dd1aa9ebf59cba186034b994160e99ff",
    "0x88c141df77cd9d8d7a71a75c826c41a9c9f03c6ee1b180f3e7852f6a280099ded351b58d66e653af8e42816a4d8f532e005a7de495bcec04d3b5e74ae09ffe493a9dd06d7dcbf18c78455571e87d901a0040597307000000a0c6d456b1f0bb64d283aaddf219b9ee2fcf936726d4783be1392d94cee96b2c6e0d63639b83948f3df333958d9058540495dee22cdcf68e9f392d060c209534058cab43ee4fec8e042dcf5b59a849f124c23eb34fab6262f7c262e740eb09b7",
    "0x81283b7a20e1ca460ebd9bbd77005d557370cabb1f9a44f530c4c4c66230f675f8df8b4c2818851aa7d77a80ca5a4a5e004a28c193c65c91b7ebb5b5d14ffa7f75dc48ad4bc66de82f70fc55a2df12150040597307000000a5afbe3e9ab0b55ebd169de3341983fa79547afdbf23eb0be4abc6afd3a5e7d42f87041f8b5f4db941c5f322aa24e4bd00d072a07f7acdeafae63e4575a93a68a2056acca638bba4b59d004c564d11ea6e7d60cb2ea81914be2d52c4a7968d13",
    "0xab0bdda0f85f842f431beaccf1250bf1fd7ba51b4100fd64364b6401fda85bb0069b3e715b58819684e7fc0b10a72a34005856ab195b61df2ff5d6ab2fa36f30dab45e42cfa1aaef3ffd899f29bd86410040597307000000afa027f3d436a8c238d4250527025dcf77c01f9bc58acece1388e554a105c74b41f3d01b3487edfeec61624097c615490e0db2768fff4f6d4a7aaa2a3efb1fb3c425db1623f5443a61546c668c4e4ecf3d1c9af5d689fc9e4cba0a8139fded3b",
    "0x9977f1c8b731a8d5558146bfb86caea26434f3c5878b589bf280a42c9159e700e9df0e4086296c20b011d2e78c27d373001c5d9bedbad1b7aff3b80e887e65b3357a695b70b6ee0625c2b2f6f86449f80040597307000000a5a23f9bd92aabfc19a35033a4b88e7fc9b04e4e01410a5d6d83d95a8e4292772ea3289403fe0f7d87d6a836fb0d110e12353c625b9ded281b18c033145b9bb6365847220412a9569f5ecca58b30aa10bc3426fe1006f3aebb55bc3e67de4a4c",
    "0xa8d4c7c27795a725961317ef5953a7032ed6d83739db8b0e8a72353d1b8b4439427f7efa2c89caa03cc9f28f8cbab8ac001414bfc6dacca55f974ec910893c8617f9c99da897534c637b50e9fc6953230040597307000000a32405e761000e9e4461b89007afe2cd71b19e7938bb27a567254b89d62c6289a294f5d04b534e15d5b0174cea4b8ae31350d98ec48225d8abcb68353df01ca77fc69fcc2357e19ad10f1e2e41dad8339c9558fc35f1c633b170c2fa01b8b04b",
    "0xa6d310dbbfab9a22450f59993f87a4ce5db6223f3b5f1f30d2c4ec718922d400e0b3c7741de8e59960f72411a0ee10a700ed09b6181e6f97365e221e70aeebcb2604011d8c4326f3b98ce8d79b031ae80040597307000000893809b5c778e69b0d5e7a344a2688b105de5fc025c0b98167d06a41ab00a1ca88ec3e9b4f4d14b33f0cd61d6722242f091a77ed487a36dbc4f74729fdeba240b6e2c151b007d7c324ce00db32f00c8632e11744fd721b2f3543d14c4638beb8",
    "0x9893413c00283a3f9ed9fd9845dda1cea38228d22567f9541dccc357e54a2d6a6e204103c92564cbc05f4905ac7c493a001fe05baa70dd29ce85f694898bb6de3bcde158a825db56906b54141b2a728d0040597307000000b94dcd09d218d8b0caca57cd43d3a94d62ba16f3c46ec329e925a8d13e08e4e27b1af19a9a09f84af45b44a4c05e77900035386199297397b3bab1914047a1dfe8db70263a021b0cb178c5ffbd91e7ea441c5cd42da7b75eb2ff8fe74703bbfb"
  };

  private final Spec spec = TestSpecFactory.createDefault();
  private final MockStartDepositGenerator generator = new MockStartDepositGenerator(spec);

  @Test
  public void shouldGenerateDepositData() {
    final List<BLSKeyPair> keyPairs =
        Arrays.stream(PRIVATE_KEYS)
            .map(Bytes32::fromHexString)
            .map(BLSSecretKey::fromBytes)
            .map(BLSKeyPair::new)
            .collect(toList());

    final List<DepositData> expectedDeposits =
        Arrays.stream(EXPECTED_DEPOSITS)
            .map(Bytes::fromHexString)
            .map(DepositData.SSZ_SCHEMA::sszDeserialize)
            .collect(toList());

    final List<DepositData> actualDeposits = generator.createDeposits(keyPairs);
    assertEquals(expectedDeposits, actualDeposits);
  }
}
