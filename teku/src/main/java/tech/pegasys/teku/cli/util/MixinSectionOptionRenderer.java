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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Help;
import picocli.CommandLine.IHelpSectionRenderer;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

public class MixinSectionOptionRenderer implements IHelpSectionRenderer {

  public static final String STANDARD_HELP_OPTIONS_MIXIN_NAME = "mixinStandardHelpOptions";

  @Override
  public String render(final Help help) {
    final StringBuilder builder = new StringBuilder();
    final List<OptionSpec> ungroupedOptions = new ArrayList<>(help.commandSpec().options());

    final LinkedHashMap<String, CommandSpec> mixinsToGroup =
        new LinkedHashMap<>(help.commandSpec().mixins());
    mixinsToGroup.remove(STANDARD_HELP_OPTIONS_MIXIN_NAME);
    mixinsToGroup.values().forEach(mixin -> ungroupedOptions.removeAll(mixin.options()));

    builder.append(help.optionListExcludingGroups(ungroupedOptions));

    for (Map.Entry<String, CommandSpec> mixin : mixinsToGroup.entrySet()) {
      ungroupedOptions.removeAll(mixin.getValue().options());
      final List<OptionSpec> visibleOptions =
          mixin.getValue().options().stream().filter(option -> !option.hidden()).toList();
      if (visibleOptions.isEmpty()) {
        continue;
      }
      builder
          .append(help.createHeading("%n%s%n", mixin.getKey()))
          .append(help.optionListExcludingGroups(visibleOptions));
    }

    builder.append(help.optionListGroupSections());
    return builder.toString();
  }
}
