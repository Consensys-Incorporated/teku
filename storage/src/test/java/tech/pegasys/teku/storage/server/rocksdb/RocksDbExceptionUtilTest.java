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

package tech.pegasys.teku.storage.server.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.Status.Code;
import org.rocksdb.Status.SubCode;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

class RocksDbExceptionUtilTest {

  @ParameterizedTest
  @MethodSource("recoverableErrorCodes")
  void shouldIdentifyRecoverableExceptions(final Code code) {
    final RocksDBException exception = new RocksDBException(new Status(code, SubCode.None, null));
    final DatabaseStorageException result = RocksDbExceptionUtil.wrapException("Test", exception);
    assertThat(result).hasMessage("Test");
    assertThat(result.isUnrecoverable()).isFalse();
  }

  @Test
  void shouldBeUnrecoverableWhenNoStatusIsSet() {
    final RocksDBException exception = new RocksDBException("Oh dear");
    final DatabaseStorageException result = RocksDbExceptionUtil.wrapException("Test", exception);
    assertThat(result.isUnrecoverable()).isTrue();
  }

  @Test
  void shouldBeUnrecoverableWhenCodeIsNotRecoverable() {
    final RocksDBException exception =
        new RocksDBException(new Status(Code.IOError, SubCode.None, null));
    final DatabaseStorageException result = RocksDbExceptionUtil.wrapException("Test", exception);
    assertThat(result.isUnrecoverable()).isTrue();
  }

  static Stream<Arguments> recoverableErrorCodes() {
    return Stream.of(
        Arguments.of(Code.TimedOut),
        Arguments.of(Code.TryAgain),
        Arguments.of(Code.MergeInProgress),
        Arguments.of(Code.Incomplete),
        Arguments.of(Code.Busy),
        Arguments.of(Code.Expired));
  }
}
