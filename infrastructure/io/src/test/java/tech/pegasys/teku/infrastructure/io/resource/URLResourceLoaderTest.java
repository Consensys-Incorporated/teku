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

import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class URLResourceLoaderTest {
  private final ResourceLoader loader = new URLResourceLoader(Optional.empty(), __ -> true);

  @Test
  public void shouldLoadContentFromURL() throws Exception {
    final String resourceName = ClasspathResourceLoaderTest.TEST_FILE_1;
    final URL resource = ClasspathResourceLoaderTest.class.getResource(resourceName);
    assertThat(loader.loadBytes(resource.toExternalForm()))
        .contains(ClasspathResourceLoaderTest.TEST_FILE_1_CONTENT);
  }

  @Test
  public void shouldNotAttemptToLoadWhenSourceDoesNotLookLikeAUrl() throws Exception {
    // Currently using the presence of a colon to suggest something is a URL.
    // Simple but very effective
    assertThat(loader.loadBytes("http//example.com/path/foo.txt")).isEmpty();
  }
}
