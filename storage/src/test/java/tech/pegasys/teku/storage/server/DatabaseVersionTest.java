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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DatabaseVersionTest {
  @Test
  void defaultVersion() {
    assertThat(DatabaseVersion.DEFAULT_VERSION).isEqualTo(DatabaseVersion.V6);
  }

  @ParameterizedTest
  @MethodSource("databaseVersions")
  void shouldAcceptVersionAsString(final String input, final DatabaseVersion expected) {
    final Optional<DatabaseVersion> result = DatabaseVersion.fromString(input);
    assertThat(result).contains(expected);
  }

  static Stream<Arguments> databaseVersions() {
    return Stream.of(
        Arguments.of("4", DatabaseVersion.V4),
        Arguments.of("5", DatabaseVersion.V5),
        Arguments.of("6", DatabaseVersion.V6),
        Arguments.of("rocksdb-tree", DatabaseVersion.ROCKSDB_TREE),
        Arguments.of("leveldb1", DatabaseVersion.LEVELDB1),
        Arguments.of("leveldb2", DatabaseVersion.LEVELDB2),
        Arguments.of("leveldb-tree", DatabaseVersion.LEVELDB_TREE));
  }
}
