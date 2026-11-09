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

package tech.pegasys.teku.infrastructure.io.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FallbackResourceLoaderTest {
  private static final String SOURCE = "foo";
  private final ResourceLoader loader1 = mock(ResourceLoader.class);
  private final ResourceLoader loader2 = mock(ResourceLoader.class);

  private final ResourceLoader fallbackLoader =
      new FallbackResourceLoader(__ -> true, loader1, loader2);

  @Test
  public void shouldLoadFromFirstLoaderIfBothCouldLoad() throws Exception {
    final InputStream result1 = new ByteArrayInputStream(new byte[1]);
    final InputStream result2 = new ByteArrayInputStream(new byte[2]);
    when(loader1.loadSource(SOURCE)).thenReturn(Optional.of(result1));
    when(loader2.loadSource(SOURCE)).thenReturn(Optional.of(result2));

    assertThat(fallbackLoader.load(SOURCE)).containsSame(result1);
    verifyNoInteractions(loader2);
  }

  @Test
  public void shouldLoadFromSecondLoaderIfFirstCouldNotLoad() throws Exception {
    final InputStream result2 = new ByteArrayInputStream(new byte[2]);
    when(loader2.loadSource(SOURCE)).thenReturn(Optional.of(result2));

    assertThat(fallbackLoader.load(SOURCE)).containsSame(result2);
  }

  @Test
  public void shouldReturnEmptyIfNoLoaderCanLoad() throws Exception {
    assertThat(fallbackLoader.load(SOURCE)).isEmpty();
  }
}
