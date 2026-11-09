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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import tech.pegasys.teku.storage.storageSystem.SupportedDatabaseVersionArgumentsProvider;

class TestDatabaseInvocationContextProvider implements TestTemplateInvocationContextProvider {
  @Override
  public boolean supportsTestTemplate(final ExtensionContext context) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    final Set<DatabaseVersion> databaseVersions =
        new HashSet<>(SupportedDatabaseVersionArgumentsProvider.supportedDatabaseVersions());
    final List<TestTemplateInvocationContext> configurations = new ArrayList<>();

    databaseVersions.stream()
        .map(databaseVersion -> generateContext(databaseVersion, true))
        .forEach(configurations::add);

    databaseVersions.stream()
        .map(databaseVersion -> generateContext(databaseVersion, false))
        .forEach(configurations::add);

    return configurations.stream();
  }

  private TestTemplateInvocationContext generateContext(
      final DatabaseVersion databaseVersion, final boolean inMemoryStorage) {
    return new TestTemplateInvocationContext() {
      @Override
      public String getDisplayName(final int invocationIndex) {
        return databaseVersion.name() + (inMemoryStorage ? " (in-memory)" : " (file-storage)");
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return List.of(
            new DatabaseVersionParameterResolver<>(
                new DatabaseContext(databaseVersion, inMemoryStorage)));
      }
    };
  }
}
