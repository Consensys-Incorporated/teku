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

package tech.pegasys.teku.infrastructure.restapi;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiDocBuilder;

public class SwaggerUIBuilder {
  // Version here MUST match `swagger-ui` library version
  private static final String SWAGGER_UI_VERSION = "5.33.1";

  private static final String SWAGGER_UI_PATH = "/swagger-ui";
  private static final String SWAGGER_HOSTED_PATH = "/webjars/swagger-ui/" + SWAGGER_UI_VERSION;
  // Be careful when modifying this, it's used in static js files for serving Swagger UI
  static final String SWAGGER_DOCS_PATH = "/swagger-docs";
  public static final String SWAGGER_INITIALIZER_JS = "/swagger-initializer.js";
  private static final Set<String> MODIFIED_FILES =
      Set.of(SWAGGER_HOSTED_PATH + SWAGGER_INITIALIZER_JS);

  public static final String RESOURCES_WEBJARS_SWAGGER_UI =
      "/META-INF/resources/webjars/swagger-ui/" + SWAGGER_UI_VERSION + "/";
  private static final String SWAGGER_UI_PATCHED = "/swagger-ui/patched";

  private static final Handler INDEX =
      (ctx) -> {
        final Map<String, Object> model = new HashMap<>();
        model.put("title", "Teku REST API");
        model.put("basePath", SWAGGER_HOSTED_PATH);
        ctx.render("index.html", model);
      };

  private final boolean enabled;

  public SwaggerUIBuilder(final boolean enabled) {
    this.enabled = enabled;
  }

  public void configureUI(final JavalinConfig config) {
    if (!enabled) {
      return;
    }
    config.staticFiles.add(
        staticFileConfig -> {
          staticFileConfig.hostedPath = SWAGGER_HOSTED_PATH;
          staticFileConfig.directory = resolveSwaggerUiDirectory();
          staticFileConfig.location = Location.EXTERNAL;
          staticFileConfig.skipFileFunction =
              httpServletRequest ->
                  httpServletRequest.getPathInfo() != null
                      && MODIFIED_FILES.contains(httpServletRequest.getPathInfo());
        });
    config.spaRoot.addFile(
        SWAGGER_HOSTED_PATH + SWAGGER_INITIALIZER_JS,
        SWAGGER_UI_PATCHED + SWAGGER_INITIALIZER_JS,
        Location.CLASSPATH);
    config.fileRenderer(createThymeleafRenderer(SWAGGER_UI_PATCHED + "/"));

    config.spaRoot.addHandler(SWAGGER_UI_PATH, INDEX);
  }

  public Optional<String> buildDocs(final OpenApiDocBuilder openApiDocBuilder) {
    if (!enabled) {
      return Optional.empty();
    }
    return Optional.of(openApiDocBuilder.build());
  }

  private JavalinThymeleaf createThymeleafRenderer(final String templatePath) {
    final TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.addTemplateResolver(templateResolver(templatePath));
    return new JavalinThymeleaf(templateEngine);
  }

  private ITemplateResolver templateResolver(final String prefix) {
    final ClassLoaderTemplateResolver templateResolver =
        new ClassLoaderTemplateResolver(Thread.currentThread().getContextClassLoader());
    templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setPrefix(prefix);
    templateResolver.setSuffix(".html");
    templateResolver.setCharacterEncoding("UTF-8");
    return templateResolver;
  }

  /**
   * Resolves the swagger-ui webjar directory to a real filesystem path. When running from an
   * unpacked directory (e.g. during development), returns the classpath directory directly. When
   * running from a JAR (e.g. in Docker), extracts the webjar contents to a temp directory to avoid
   * issues with Jetty 12's URLResourceFactory failing to verify JAR directory entries on Linux.
   */
  private static String resolveSwaggerUiDirectory() {
    final String resourcePath = "META-INF/resources/webjars/swagger-ui/" + SWAGGER_UI_VERSION + "/";
    final URL url = SwaggerUIBuilder.class.getClassLoader().getResource(resourcePath);
    if (url == null) {
      throw new IllegalStateException(
          "swagger-ui webjar not found on classpath for version: " + SWAGGER_UI_VERSION);
    }
    if ("file".equals(url.getProtocol())) {
      try {
        return Path.of(url.toURI()).toRealPath().toString();
      } catch (final URISyntaxException | IOException e) {
        throw new RuntimeException("Failed to resolve swagger-ui directory", e);
      }
    }
    // jar: protocol — extract to a temp directory to avoid URLConnection issues
    // with JAR directory entries on Linux (e.g. in Docker).
    try {
      return extractSwaggerUiToTempDirectory(url);
    } catch (final IOException | URISyntaxException e) {
      throw new RuntimeException("Failed to extract swagger-ui resources", e);
    }
  }

  private static String extractSwaggerUiToTempDirectory(final URL jarDirUrl)
      throws IOException, URISyntaxException {
    final String urlStr = jarDirUrl.toString();
    final int bangIdx = urlStr.indexOf("!/");
    final String jarFilePath = URI.create(urlStr.substring("jar:".length(), bangIdx)).getPath();
    final String entryPrefix = urlStr.substring(bangIdx + 2);

    // toRealPath() resolves symlinks (e.g. /var -> /private/var on macOS) so that Javalin's
    // alias detection does not reject files served from this directory.
    final Path tempDir = Files.createTempDirectory("teku-swagger-ui-").toRealPath();
    // Register cleanup before extraction so the temp dir is removed even if extraction fails
    Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteDirectory(tempDir)));
    try (final JarFile jar = new JarFile(jarFilePath)) {
      jar.stream()
          .filter(entry -> entry.getName().startsWith(entryPrefix) && !entry.isDirectory())
          .forEach(
              entry -> {
                final String relativeName = entry.getName().substring(entryPrefix.length());
                final Path dest = tempDir.resolve(relativeName).normalize();
                // Guard against Zip Slip: ensure destination is within the temp directory
                if (!dest.startsWith(tempDir)) {
                  throw new IllegalStateException(
                      "JAR entry path escapes target directory: " + entry.getName());
                }
                try {
                  Files.createDirectories(dest.getParent());
                  try (final InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                  }
                } catch (final IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (final UncheckedIOException e) {
      throw e.getCause();
    }
    return tempDir.toAbsolutePath().toString();
  }

  private static void deleteDirectory(final Path directory) {
    try {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (final IOException ignored) {
      // Best-effort cleanup on shutdown
    }
  }
}
