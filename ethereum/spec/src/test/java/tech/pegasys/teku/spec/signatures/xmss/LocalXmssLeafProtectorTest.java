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

package tech.pegasys.teku.spec.signatures.xmss;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssPublicKey;

class LocalXmssLeafProtectorTest {

  private static final XmssPublicKey KEY_1 =
      XmssPublicKey.fromBytes(Bytes32.fromHexStringLenient("0x01"));
  private static final XmssPublicKey KEY_2 =
      XmssPublicKey.fromBytes(Bytes32.fromHexStringLenient("0x02"));
  private static final UInt64 LEAF = UInt64.valueOf(40_000);
  private static final Bytes32 ROOT_A = Bytes32.fromHexStringLenient("0xa1");
  private static final Bytes32 ROOT_B = Bytes32.fromHexStringLenient("0xb2");

  @TempDir Path dir;

  private LocalXmssLeafProtector createProtector() {
    return new LocalXmssLeafProtector(SyncDataAccessor.create(dir), dir);
  }

  @Test
  void maySign_recordsTheLeafBeforeAllowing() throws Exception {
    final LocalXmssLeafProtector protector = createProtector();

    assertThat(safeJoin(protector.maySign(KEY_1, LEAF, ROOT_A))).isTrue();

    final Path file =
        dir.resolve(KEY_1.toBytes().toUnprefixedHexString() + LocalXmssLeafProtector.FILE_SUFFIX);
    assertThat(XmssLeafRecord.fromBytes(Bytes.wrap(Files.readAllBytes(file))).getSigningRoot(LEAF))
        .contains(ROOT_A);
  }

  @Test
  void maySign_refusesReuseAfterARestart() {
    assertThat(safeJoin(createProtector().maySign(KEY_1, LEAF, ROOT_A))).isTrue();

    final LocalXmssLeafProtector restarted = createProtector();

    assertThat(safeJoin(restarted.maySign(KEY_1, LEAF, ROOT_B))).isFalse();
    assertThat(safeJoin(restarted.maySign(KEY_1, LEAF, ROOT_A))).isTrue();
  }

  @Test
  void maySign_keepsKeysApart() {
    final LocalXmssLeafProtector protector = createProtector();

    assertThat(safeJoin(protector.maySign(KEY_1, LEAF, ROOT_A))).isTrue();
    assertThat(safeJoin(protector.maySign(KEY_2, LEAF, ROOT_B))).isTrue();
    assertThat(safeJoin(protector.maySign(KEY_1, LEAF, ROOT_B))).isFalse();
  }

  @Test
  void allocateReservedLeaf_allocatesEachLeafOnceAcrossRestarts() {
    final LocalXmssLeafProtector protector = createProtector();

    assertThat(safeJoin(protector.allocateReservedLeaf(KEY_1, ROOT_A))).contains(UInt64.ZERO);
    assertThat(safeJoin(protector.allocateReservedLeaf(KEY_1, ROOT_A))).contains(UInt64.ONE);

    final LocalXmssLeafProtector restarted = createProtector();
    assertThat(safeJoin(restarted.allocateReservedLeaf(KEY_1, ROOT_B))).contains(UInt64.valueOf(2));
    assertThat(safeJoin(restarted.maySign(KEY_1, UInt64.ONE, ROOT_B))).isFalse();
    assertThat(safeJoin(restarted.allocateReservedLeaf(KEY_2, ROOT_B))).contains(UInt64.ZERO);
  }
}
