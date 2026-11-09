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

package tech.pegasys.teku.spec.config;

import java.io.InputStream;
import java.util.Map;
import java.util.NoSuchElementException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

public class YamlConfigReader {
  private final Yaml yaml;

  public YamlConfigReader() {
    final Resolver resolver = new CustomResolver();
    yaml =
        new Yaml(
            new Constructor(new LoaderOptions()),
            new Representer(new DumperOptions()),
            new DumperOptions(),
            resolver);
  }

  public Map<String, Object> readValues(final InputStream source) {
    final Map<String, Object> values = yaml.load(source);
    if (values == null) {
      throw new NoSuchElementException();
    }
    return values;
  }

  /*
   * For the purposes of reading config, we just want strings, we don't want numeric values.
   * This will ensure that we get hex values through as unparsed, and ensure we're getting
   * things such as CRC checks through without them being altered.
   * The implicit resolver below is basically saying just match numerics as string.
   */
  public static class CustomResolver extends Resolver {
    @Override
    protected void addImplicitResolvers() {
      addImplicitResolver(Tag.STR, INT, "+-0123456789.");
      super.addImplicitResolvers();
    }
  }
}
