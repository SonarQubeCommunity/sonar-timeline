/*
 * Sonar Timeline plugin
 * Copyright (C) 2009 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.timeline.widget;

import org.sonar.api.web.AbstractRubyTemplate;
import org.sonar.api.web.RubyRailsWidget;
import org.sonar.api.web.WidgetCategory;
import org.sonar.api.web.WidgetProperties;
import org.sonar.api.web.WidgetProperty;
import org.sonar.api.web.WidgetPropertyType;

@WidgetCategory({"History"})
@WidgetProperties(
{
  @WidgetProperty(key = "metric1", type = WidgetPropertyType.METRIC, defaultValue = "ncloc", optional = false),
  @WidgetProperty(key = "metric2", type = WidgetPropertyType.METRIC),
  @WidgetProperty(key = "metric3", type = WidgetPropertyType.METRIC),
  @WidgetProperty(key = "singleScale", type = WidgetPropertyType.BOOLEAN, defaultValue = "false"),
  @WidgetProperty(key = "height", type = WidgetPropertyType.INTEGER, defaultValue = "400")
})
public class AdvancedTimelineWidget extends AbstractRubyTemplate implements RubyRailsWidget {
  public String getId() {
    return "advanced_timeline";
  }

  public String getTitle() {
    return "Advanced Timeline Chart";
  }

  @Override
  protected String getTemplatePath() {
    return "/org/sonar/plugins/timeline/widget/advanced_timeline.html.erb";
  }
}
