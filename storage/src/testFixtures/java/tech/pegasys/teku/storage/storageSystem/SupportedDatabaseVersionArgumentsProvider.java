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

package tech.pegasys.teku.storage.storageSystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;
import tech.pegasys.teku.storage.server.DatabaseVersion;

public class SupportedDatabaseVersionArgumentsProvider implements ArgumentsProvider {

  public static Collection<DatabaseVersion> supportedDatabaseVersions() {
    final List<DatabaseVersion> supportedVersions = new ArrayList<>();
    if (DatabaseVersion.tryLoadRocksdbLibrary()) {
      supportedVersions.add(DatabaseVersion.V4);
      supportedVersions.add(DatabaseVersion.V5);
      supportedVersions.add(DatabaseVersion.V6);
      supportedVersions.add(DatabaseVersion.ROCKSDB_TREE);
    }
    if (DatabaseVersion.tryLoadLeveldbNativeLibrary()) {
      supportedVersions.add(DatabaseVersion.LEVELDB1);
      supportedVersions.add(DatabaseVersion.LEVELDB2);
      supportedVersions.add(DatabaseVersion.LEVELDB_TREE);
    }

    assertThat(supportedVersions)
        .withFailMessage(
            "No database systems were supported on this platform. Unable to run database tests.")
        .isNotEmpty();
    return supportedVersions;
  }

  @Override
  public Stream<? extends Arguments> provideArguments(
      final ParameterDeclarations parameters, final ExtensionContext context) {
    return supportedDatabaseVersions().stream().map(Arguments::of);
  }
}
