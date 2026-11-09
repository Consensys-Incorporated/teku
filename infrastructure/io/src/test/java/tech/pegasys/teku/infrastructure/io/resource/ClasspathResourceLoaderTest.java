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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class ClasspathResourceLoaderTest {

  public static final String TEST_FILE_1 = "testFile1.txt";
  public static final String TEST_FILE_2 = "testFile2.txt";
  public static final Bytes TEST_FILE_1_CONTENT =
      Bytes.wrap("Test file 1".getBytes(StandardCharsets.UTF_8));
  public static final Bytes TEST_FILE_2_CONTENT =
      Bytes.wrap("Test file 2".getBytes(StandardCharsets.UTF_8));

  private static final List<String> AVAILABLE_FILES = List.of(TEST_FILE_1, TEST_FILE_2);
  private static final Predicate<String> RESOURCE_FILTER = __ -> true;

  @Test
  public void shouldLoadAllowedResources() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, AVAILABLE_FILES, RESOURCE_FILTER);
    assertThat(loader.loadBytes(TEST_FILE_1)).contains(TEST_FILE_1_CONTENT);
    assertThat(loader.loadBytes(TEST_FILE_2)).contains(TEST_FILE_2_CONTENT);
  }

  @Test
  public void shouldNotLoadDisallowedResources() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, AVAILABLE_FILES, __ -> false);
    assertThat(loader.loadBytes(TEST_FILE_1)).isEmpty();
    assertThat(loader.loadBytes(TEST_FILE_2)).isEmpty();
  }

  @Test
  public void shouldNotLoadUnknownResources() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, List.of("other.txt"), RESOURCE_FILTER);
    assertThat(loader.loadBytes(TEST_FILE_1)).isEmpty();
    assertThat(loader.loadBytes(TEST_FILE_2)).isEmpty();
  }

  @Test
  public void shouldLoadFirstAvailableResource() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, AVAILABLE_FILES, RESOURCE_FILTER);
    assertThat(loader.loadBytes(TEST_FILE_1, TEST_FILE_2)).contains(TEST_FILE_1_CONTENT);
  }

  @Test
  public void shouldLoadFirstKnownAvailableResource() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, List.of(TEST_FILE_2), RESOURCE_FILTER);
    assertThat(loader.loadBytes(TEST_FILE_1, TEST_FILE_2)).contains(TEST_FILE_2_CONTENT);
  }

  @Test
  public void shouldLoadFirstAllowedAvailableResource() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, List.of(TEST_FILE_2), s -> s.equals(TEST_FILE_2));
    assertThat(loader.loadBytes(TEST_FILE_1, TEST_FILE_2)).contains(TEST_FILE_2_CONTENT);
  }

  @Test
  public void shouldReturnEmptyWhenResourceIsNotFound() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, AVAILABLE_FILES, RESOURCE_FILTER);

    // Because only testFile1 is allowed.
    assertThat(loader.loadBytes("foo")).isEmpty();
  }
}
