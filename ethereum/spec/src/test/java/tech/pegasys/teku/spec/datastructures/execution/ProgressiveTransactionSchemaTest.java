/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;

class ProgressiveTransactionSchemaTest {

  private static final SpecConfigBellatrix CONFIG =
      SpecConfigBellatrix.required(TestSpecFactory.createMinimalGloas().getGenesisSpecConfig());
  private static final ProgressiveTransactionSchema SCHEMA =
      new ProgressiveTransactionSchema(CONFIG);

  @Test
  void fromBytes_shouldProduceTransactionSubtype() {
    final Transaction transaction = SCHEMA.fromBytes(Bytes.fromHexString("0x010203"));
    assertThat(transaction.getClass()).isEqualTo(Transaction.class);
    assertThat(transaction.getBytes()).isEqualTo(Bytes.fromHexString("0x010203"));
  }

  @Test
  void sszRoundTrip_shouldPreserveBytesAndRoot() {
    final Bytes bytes = Bytes.fromHexString("0xdeadbeefcafe");
    final Transaction transaction = SCHEMA.fromBytes(bytes);

    final Transaction deserialized = SCHEMA.sszDeserialize(transaction.sszSerialize());

    assertThat(deserialized.getBytes()).isEqualTo(bytes);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(transaction.hashTreeRoot());
  }

  @Test
  void createFromBackingNode_shouldReturnTransaction() {
    final Transaction transaction = SCHEMA.fromBytes(Bytes.fromHexString("0x0102"));
    assertThat(SCHEMA.createFromBackingNode(transaction.getBackingNode()))
        .isInstanceOf(Transaction.class);
  }
}
