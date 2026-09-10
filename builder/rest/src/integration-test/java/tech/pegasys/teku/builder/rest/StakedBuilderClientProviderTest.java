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

package tech.pegasys.teku.builder.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;

class StakedBuilderClientProviderTest {

  private final Spec spec = mock(Spec.class);
  private final AsyncRunner asyncRunner = mock(AsyncRunner.class);

  private final StakedBuilderClientProvider provider =
      new StakedBuilderClientProvider(spec, asyncRunner);

  @Test
  void returnsSameInstanceForSameUrl() {
    final StakedBuilderClient client1 = provider.getClient("http://example.com");
    final StakedBuilderClient client2 = provider.getClient("http://example.com");

    assertThat(client1).isSameAs(client2);
  }

  @Test
  void returnsDifferentInstancesForDifferentUrls() {
    final StakedBuilderClient client1 = provider.getClient("http://example.com");
    final StakedBuilderClient client2 = provider.getClient("http://example.com/other");

    assertThat(client1).isNotSameAs(client2);
  }

  @Test
  void sameHostDifferentPathCreatesDifferentClients() {
    final StakedBuilderClient client1 = provider.getClient("http://example.com/path1");
    final StakedBuilderClient client2 = provider.getClient("http://example.com/path2");

    assertThat(client1).isNotSameAs(client2);
  }
}
