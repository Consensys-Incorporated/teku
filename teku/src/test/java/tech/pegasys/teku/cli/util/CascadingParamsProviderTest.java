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

package tech.pegasys.teku.cli.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CascadingParamsProviderTest {
  private final AdditionalParamsProvider provider1 = mock(AdditionalParamsProvider.class);
  private final AdditionalParamsProvider provider2 = mock(AdditionalParamsProvider.class);

  @Test
  void shouldCorrectlyGivePrecedence() {
    final CascadingParamsProvider cascadingParamsProvider =
        new CascadingParamsProvider(provider1, provider2);

    final Map<String, String> additionalParams1 = Map.of("--a", "a1", "--b", "b1");
    final Map<String, String> additionalParams2 = Map.of("--b", "b2", "--c", "c2");

    final Map<String, String> expectedResult = Map.of("--a", "a1", "--b", "b1", "--c", "c2");

    when(provider1.getAdditionalParams(any())).thenReturn(additionalParams1);
    when(provider2.getAdditionalParams(any())).thenReturn(additionalParams2);

    assertThat(cascadingParamsProvider.getAdditionalParams(List.of())).isEqualTo(expectedResult);
  }
}
