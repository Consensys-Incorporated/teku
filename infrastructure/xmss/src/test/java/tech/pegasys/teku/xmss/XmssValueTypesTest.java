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

package tech.pegasys.teku.xmss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class XmssValueTypesTest {

  @Test
  void stubSignatureShouldCarryItsLeafIndexLittleEndian() {
    final XmssSignature stub = XmssSignature.stub(UInt64.valueOf(0x01020304L));
    assertThat(stub.toBytes().slice(0, 4)).isEqualTo(Bytes.fromHexString("0x04030201"));
    assertThat(stub.toBytes().slice(4).toArrayUnsafe()).containsOnly((byte) 0x11);
    assertThat(stub.getLeafIndex()).isEqualTo(UInt64.valueOf(0x01020304L));
    assertThat(stub.toBytes().size()).isEqualTo(XmssConstants.SIGNATURE_SIZE);
  }

  @Test
  void leafIndexShouldBeReadAsUnsigned() {
    final XmssSignature stub = XmssSignature.stub(XmssConstants.MAX_LEAF_INDEX);
    assertThat(stub.getLeafIndex()).isEqualTo(UInt64.valueOf(0xFFFFFFFFL));
  }

  @Test
  void emptySignatureShouldBeAllZero() {
    assertThat(XmssSignature.empty().isEmpty()).isTrue();
    assertThat(XmssSignature.empty().getLeafIndex()).isEqualTo(UInt64.ZERO);
    assertThat(XmssSignature.stub(UInt64.ZERO).isEmpty()).isFalse();
  }

  @Test
  void stubShouldRejectLeafIndexAboveUint32() {
    assertThatThrownBy(() -> XmssSignature.stub(XmssConstants.MAX_LEAF_INDEX.plus(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectWrongSizes() {
    assertThatThrownBy(() -> XmssPublicKey.fromBytes(Bytes.random(31)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> XmssSecretKey.fromBytes(Bytes.random(33)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> XmssSignature.fromBytes(Bytes.random(2403)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void secretKeyShouldNotPrintItsSeed() {
    final Bytes seed = Bytes.random(32);
    assertThat(XmssSecretKey.fromBytes(seed).toString())
        .doesNotContain(seed.toUnprefixedHexString());
  }

  @Test
  void signatureShouldNotAliasCallerBytes() {
    final byte[] raw = new byte[XmssConstants.SIGNATURE_SIZE];
    final XmssSignature signature = XmssSignature.fromBytes(Bytes.wrap(raw));
    raw[0] = 7;
    assertThat(signature.getLeafIndex()).isEqualTo(UInt64.ZERO);
  }
}
