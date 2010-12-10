/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.timeline.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.visualization.client.VisualizationUtils;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine.Options;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine.ScaleType;
import org.sonar.api.web.gwt.client.ResourceDictionary;
import org.sonar.api.web.gwt.client.webservices.BaseQueryCallback;
import org.sonar.api.web.gwt.client.webservices.WSMetrics.Metric.ValueType;
import org.sonar.api.web.gwt.client.widgets.LoadingLabel;
import org.sonar.gwt.ui.Page;
import org.sonar.wsclient.gwt.AbstractCallback;
import org.sonar.wsclient.gwt.AbstractListCallback;
import org.sonar.wsclient.gwt.Sonar;
import org.sonar.wsclient.services.*;

import java.util.*;

public class GwtTimeline extends Page {

  public static final String GWT_ID = "org.sonar.plugins.timeline.GwtTimeline";

  public static final int DEFAULT_HEIGHT = 480;

  public static final String DEFAULT_METRICS_KEY = "sonar.timeline.defaultmetrics";
  public static final String DEFAULT_METRICS_VALUE = "ncloc,violations_density,coverage";

  private SortedSet<Metric> metrics = null;
  private String[] defaultMetrics = null;
  private ListBox metricsListBox1 = new ListBox();
  private ListBox metricsListBox2 = new ListBox();
  private ListBox metricsListBox3 = new ListBox();
  private List<ListBox> metricsListBoxes = null;
  private SimplePanel tlPanel = null;

  private VerticalPanel panel;

  private Map<String, Metric> loadedMetrics = new HashMap<String, Metric>();

  @Override
  protected Widget doOnResourceLoad(Resource resource) {
    panel = new VerticalPanel();
    panel.add(new LoadingLabel());
    load();
    return panel;
  }

  private void load() {
    Sonar.getInstance().find(PropertyQuery.createForKey(DEFAULT_METRICS_KEY), new AbstractCallback<Property>() {
      @Override
      protected void doOnResponse(Property result) {
        String value = result.getValue();
        defaultMetrics = value.split(",");
        loadMetrics();
      }

      @Override
      protected void doOnError(int errorCode, String errorMessage) {
        defaultMetrics = DEFAULT_METRICS_VALUE.split(",");
        loadMetrics();
      }
    });
  }

  private void loadMetrics() {
    final List<String> excludedTypes = Arrays.asList("BOOL", "DATA", "DISTRIB", "STRING", "LEVEL");

    Sonar.getInstance().findAll(MetricQuery.all(), new AbstractListCallback<Metric>() {
      @Override
      protected void doOnResponse(List<Metric> result) {
        for (Metric metric : result) {
          if (!excludedTypes.contains(metric.getType())) {
            loadedMetrics.put(metric.getKey(), metric);
          }
        }

        metrics = orderMetrics(result);
        metricsListBoxes = Arrays.asList(metricsListBox1, metricsListBox2, metricsListBox3);
        loadListBox(metricsListBox1, defaultMetrics.length > 0 ? defaultMetrics[0] : null);
        loadListBox(metricsListBox2, defaultMetrics.length > 1 ? defaultMetrics[1] : null);
        loadListBox(metricsListBox3, defaultMetrics.length > 2 ? defaultMetrics[2] : null);

        ChangeHandler metricSelection = new ChangeHandler() {
          public void onChange(ChangeEvent event) {
            if (!allMetricsUnSelected() && !sameMetricsSelection()) {
              loadTimeLine();
            }
          }
        };
        for (ListBox metricLb : metricsListBoxes) {
          metricLb.addChangeHandler(metricSelection);
        }

        loadVisualizationApi();
      }

      private void loadListBox(ListBox metricsLb, String selectedKey) {
        metricsLb.setStyleName("small");
        metricsLb.addItem("<none>", "");
        int index = 1;
        for (Metric metric : metrics) {
          metricsLb.addItem(metric.getName(), metric.getKey());
          if (selectedKey != null && metric.getKey().equals(selectedKey.trim())) {
            metricsLb.setSelectedIndex(index);
          }
          index++;
        }
      }

      private Boolean allMetricsUnSelected() {
        for (ListBox metricLb : metricsListBoxes) {
          if (getSelectedMetric(metricLb) != null) {
            return false;
          }
        }
        return true;
      }

      private boolean sameMetricsSelection() {
        List<Metric> selected = new ArrayList<Metric>();
        for (ListBox metricLb : metricsListBoxes) {
          Metric metric = getSelectedMetric(metricLb);
          if (metric != null) {
            if (selected.contains(metric)) {
              return true;
            }
            selected.add(metric);
          }
        }
        return false;
      }
    });
  }

  private void loadVisualizationApi() {
    Runnable onLoadCallback = new Runnable() {
      public void run() {
        render();
        loadTimeLine();
      }
    };
    VisualizationUtils.loadVisualizationApi(onLoadCallback, AnnotatedTimeLine.PACKAGE);
  }

  private SortedSet<Metric> orderMetrics(Collection<Metric> metrics) {
    TreeSet<Metric> ordered = new TreeSet<Metric>(new Comparator<Metric>() {
      public int compare(Metric o1, Metric o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    ordered.addAll(metrics);
    return ordered;
  }

  private void loadTimeLine() {
    lockMetricsList(true);
    tlPanel.clear();
    final LoadingLabel loading = new LoadingLabel();
    tlPanel.add(loading);

    TimelineQuery.get(ResourceDictionary.getResourceKey())
        .setMetrics(getSelectedMetrics())
        .execute(new BaseQueryCallback<DataTable>(loading) {
          public void onResponse(DataTable response, JavaScriptObject jsonRawResponse) {
            Element content = DOM.getElementById("content");
            int width = content.getClientWidth() > 0 ? content.getClientWidth() : 800;
            Widget toRender = response.getTable().getNumberOfRows() > 0 ?
                new AnnotatedTimeLine(response.getTable(), createOptions(), width + "px", GwtTimeline.DEFAULT_HEIGHT + "px") :
                new HTML("<p>No data</p>");
            loading.removeFromParent();
            lockMetricsList(false);
            tlPanel.add(toRender);
          }

          @Override
          public void onError(int errorCode, String errorMessage) {
            lockMetricsList(false);
            super.onError(errorCode, errorMessage);
          }

          @Override
          public void onTimeout() {
            lockMetricsList(false);
            super.onTimeout();
          }
        });
  }

  private void lockMetricsList(boolean locked) {
    for (ListBox metricLb : metricsListBoxes) {
      metricLb.setEnabled(!locked);
    }
  }

  private List<Metric> getSelectedMetrics() {
    List<Metric> metrics = new ArrayList<Metric>();
    for (ListBox metricLb : metricsListBoxes) {
      Metric metric = getSelectedMetric(metricLb);
      if (metric != null) {
        // inverting metrics
        metrics.add(0, metric);
      }
    }
    return metrics;
  }

  private Metric getSelectedMetric(ListBox metricsLb) {
    String selected = metricsLb.getValue(metricsLb.getSelectedIndex());
    return selected.length() > 0 ? loadedMetrics.get(selected) : null;
  }

  private void render() {
    HorizontalPanel hPanel = new HorizontalPanel();
    Label label = new Label("Metrics:");
    label.setStyleName("note");

    hPanel.add(label);
    for (ListBox metricLb : metricsListBoxes) {
      hPanel.add(new HTML("&nbsp;"));
      hPanel.add(metricLb);
    }

    VerticalPanel vPanel = new VerticalPanel();
    vPanel.add(hPanel);
    tlPanel = new SimplePanel();
    vPanel.add(tlPanel);
    displayView(vPanel);
  }

  private void displayView(Widget widget) {
    panel.clear();
    panel.add(widget);
  }

  private Options createOptions() {
    Options options = Options.create();
    options.setAllowHtml(true);
    options.setDisplayAnnotations(true);
    options.setDisplayAnnotationsFilter(true);
    options.setAnnotationsWidth(15);
    options.setOption("fill", 15);
    options.setOption("thickness", 2);
    options.setScaleType(ScaleType.ALLFIXED);

    resetNumberFormats();
    int selectedCols = 0;
    for (ListBox metricLb : metricsListBoxes) {
      if (getSelectedMetric(metricLb) != null) {
        setNumberFormats(selectedCols++, getNumberFormat(getSelectedMetric(metricLb)));
      }
    }

    options.setOption("numberFormats", getNumberFormats());
    int[] scaledCols = new int[selectedCols];
    for (int i = 0; i < selectedCols; i++) {
      scaledCols[i] = i;
    }
    options.setScaleColumns(scaledCols);
    return options;
  }

  private String getNumberFormat(Metric metric) {
    return metric.getType().equals(ValueType.PERCENT) ? "0.0" : "0.##";
  }

  private native JavaScriptObject getNumberFormats()
  /*-{
  return this.numberFormats;
  }-*/;

  private native void resetNumberFormats()
  /*-{
  this.numberFormats = {};
  }-*/;

  private native void setNumberFormats(int key, String numberFormat)
  /*-{
  this.numberFormats[key] = numberFormat;
  }-*/;

}
