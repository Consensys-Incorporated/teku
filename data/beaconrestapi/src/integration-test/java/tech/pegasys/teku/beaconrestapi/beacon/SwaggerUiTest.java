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

package tech.pegasys.teku.beaconrestapi.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static tech.pegasys.teku.infrastructure.restapi.SwaggerUIBuilder.SWAGGER_INITIALIZER_JS;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;

public class SwaggerUiTest extends AbstractDataBackedRestAPIIntegrationTest {
  private static final String SWAGGER_PATH = "/swagger-ui";
  private static final String JSON_SCHEMA_PATH = "/swagger-docs";

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldShowSwaggerInterface() throws IOException {
    Set<String> links = findAssets(getUrl(SWAGGER_PATH));

    links.forEach(this::checkPath);
  }

  @Test
  public void shouldContainInitializerWithPatchedJsonSchemaLink() throws IOException {
    Set<String> links = findAssets(getUrl(SWAGGER_PATH));
    Optional<String> initializerPath = Optional.empty();
    for (String link : links) {
      if (link.endsWith(SWAGGER_INITIALIZER_JS)) {
        initializerPath = Optional.of(link);
        break;
      }
    }
    assertThat(initializerPath).isNotEmpty();

    Response response = getResponse(initializerPath.get());
    assertThat(response.body().string()).contains(JSON_SCHEMA_PATH);
  }

  private void checkPath(final String path) {
    try {
      Response response = getResponse(path);
      assertThat(response.code()).as("Check deep link (%s)", path).isEqualTo(200);
    } catch (IOException exception) {
      fail("failed to fetch resource");
    }
  }

  private static Set<String> findAssets(final String url) throws IOException {

    Set<String> links = new HashSet<>();

    Document doc =
        Jsoup.connect(url).data("query", "Java").userAgent("Mozilla").timeout(3000).get();

    final URI baseUrl = URI.create(url);

    // Resources
    Elements resources = doc.select("link[href]");
    for (Element element : resources) {
      links.add(baseUrl.resolve(element.attr("href")).getPath());
    }

    // Scripts
    Elements scripts = doc.select("script");
    for (Element element : scripts) {
      links.add(baseUrl.resolve(element.attr("src")).getPath());
    }

    return links;
  }
}
