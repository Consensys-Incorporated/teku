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

package tech.pegasys.teku.infrastructure.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.restapi.SwaggerUIBuilder.RESOURCES_WEBJARS_SWAGGER_UI;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Test;

class SwaggerWebjarIntegrityTest {
  private static final String VENDOR_COPY_PATH = "/swagger-ui/vendor";

  @Test
  public void swaggerUiBuilderShouldReferenceAvailableSwaggerUiWebjarVersion() {
    assertThat(SwaggerUIBuilder.class.getResource(RESOURCES_WEBJARS_SWAGGER_UI + "swagger-ui.css"))
        .as("SwaggerUIBuilder swagger-ui version should match org.webjars:swagger-ui")
        .isNotNull();
  }

  /**
   * `infrastructure/restapi/src/main/resources/swagger-ui/vendor` contains several files which
   * should match files with the same names in 'org.webjars:swagger-ui'.
   *
   * <p>NOTICE: If it doesn't match due to swagger version update, both
   * `infrastructure/restapi/src/main/resources/swagger-ui/vendor` (just jar files copy) and
   * `infrastructure/restapi/src/main/resources/swagger-ui/patched` (up-to-date vendor files with
   * changed links similar to the current modifications) should be updated
   */
  @Test
  public void shouldHaveTheSameVersionOfFilesInVendorFolderAndSwaggerJar() throws Exception {
    final List<String> vendorFiles = listClasspathDir(VENDOR_COPY_PATH, SwaggerUIBuilder.class);
    assertThat(vendorFiles).isNotEmpty();
    for (String filePath : vendorFiles) {
      final byte[] resourceFileContents = getClasspathFile(filePath);
      final byte[] jarFileContents =
          getClasspathFile(RESOURCES_WEBJARS_SWAGGER_UI + FilenameUtils.getName(filePath));

      assertThat(jarFileContents).isEqualTo(resourceFileContents);
    }
  }

  private byte[] getClasspathFile(final String path) throws IOException {
    final InputStream in = getClass().getResourceAsStream(path);
    assertThat(in).as("Classpath resource %s", path).isNotNull();
    try (in) {
      return in.readAllBytes();
    }
  }

  private static List<String> listClasspathDir(final String path, final Class<?> baseClass)
      throws IOException, URISyntaxException {
    final URI uri = baseClass.getResource(path).toURI();
    if (uri.getScheme().equals("jar")) {
      try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
        return listPath(fileSystem.getPath(path));
      }
    } else {
      return listPath(Paths.get(uri));
    }
  }

  private static List<String> listPath(final Path path) throws IOException {
    try (Stream<Path> stream = Files.list(path)) {
      return stream.map(Path::toString).collect(Collectors.toList());
    }
  }
}
