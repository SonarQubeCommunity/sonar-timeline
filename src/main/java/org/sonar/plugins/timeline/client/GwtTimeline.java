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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.visualization.client.AbstractDataTable.ColumnType;
import com.google.gwt.visualization.client.DataTable;
import com.google.gwt.visualization.client.VisualizationUtils;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine.Options;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine.ScaleType;
import org.sonar.api.web.gwt.client.widgets.LoadingLabel;
import org.sonar.gwt.ui.Page;
import org.sonar.wsclient.gwt.AbstractCallback;
import org.sonar.wsclient.gwt.AbstractListCallback;
import org.sonar.wsclient.gwt.Sonar;
import org.sonar.wsclient.services.*;

import java.util.*;

public class GwtTimeline extends Page {

  public static final String GWT_ID = "org.sonar.plugins.timeline.GwtTimeline";

  // TODO RATING - see http://jira.codehaus.org/browse/SONARPLUGINS-843
  public static final List<String> SUPPORTED_METRIC_TYPES = Arrays.asList("INT", "FLOAT", "PERCENT", "MILLISEC");

  public static final int DEFAULT_HEIGHT = 480;

  public static final String DEFAULT_METRICS_KEY = "sonar.timeline.defaultmetrics";
  public static final String DEFAULT_METRICS_VALUE = "ncloc,violations_density,coverage";

  private SortedSet<Metric> metrics = null;
  private String[] defaultMetrics = null;
  private ListBox metricsListBox1 = new ListBox();
  private ListBox metricsListBox2 = new ListBox();
  private ListBox metricsListBox3 = new ListBox();
  private List<ListBox> metricsListBoxes = null;
  private CheckBox singleScaleCheckBox = new CheckBox("single scale");
  private SimplePanel tlPanel = null;

  private VerticalPanel panel;

  private Map<String, Metric> loadedMetrics = new HashMap<String, Metric>();

  private Resource resource;

  private DataTable dataTable;

  @Override
  protected Widget doOnResourceLoad(Resource resource) {
    panel = new VerticalPanel();
    panel.add(new LoadingLabel());
    this.resource = resource;
    load();
    return panel;
  }

  private void load() {
    Sonar.getInstance().find(PropertyQuery.createForKey(DEFAULT_METRICS_KEY), new AbstractCallback<Property>() {
      @Override
      protected void doOnResponse(Property result) {
        defaultMetrics = result.getValue().split(",");
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
    Sonar.getInstance().findAll(MetricQuery.all(), new AbstractListCallback<Metric>() {
      @Override
      protected void doOnResponse(List<Metric> result) {
        for (Metric metric : result) {
          if (SUPPORTED_METRIC_TYPES.contains(metric.getType())) {
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
              loadTimeline();
            }
          }
        };
        for (ListBox metricLb : metricsListBoxes) {
          metricLb.addChangeHandler(metricSelection);
        }
        singleScaleCheckBox.addClickHandler(new ClickHandler() {
          public void onClick(ClickEvent event) {
            renderDataTable(dataTable);
          }
        });

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
        loadTimeline();
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

  private void loadTimeline() {
    lockMetricsList(true);
    tlPanel.clear();
    tlPanel.add(new LoadingLabel());

    new TimelineLoader(resource.getKey(), getSelectedMetrics()) {
      @Override
      void noData() {
        renderNoData();
      }

      @Override
      void data(String[] metrics, TimeMachine timemachine, List<Event> events) {
        dataTable = getDataTable(metrics, timemachine, events);
        renderDataTable(dataTable);
      }
    };
  }

  private void renderDataTable(DataTable table) {
    if (table != null && table.getNumberOfRows() > 0) {
      Element content = DOM.getElementById("content");
      int width = content.getClientWidth() > 0 ? content.getClientWidth() : 800;
      Widget toRender = new AnnotatedTimeLine(table, createOptions(), width + "px", GwtTimeline.DEFAULT_HEIGHT + "px");
      renderTimeline(toRender);
    } else {
      renderNoData();
    }
  }

  private void renderNoData() {
    renderTimeline(new HTML("<p>No data</p>"));
  }

  private void renderTimeline(Widget toRender) {
    lockMetricsList(false);
    tlPanel.clear();
    tlPanel.add(toRender);
  }

  private DataTable getDataTable(String[] metrics, TimeMachine timeMachine, List<Event> events) {
    DataTable table = DataTable.create();
    table.addColumn(ColumnType.DATE, "d", "Date");
    for (String metric : metrics) {
      table.addColumn(ColumnType.NUMBER, loadedMetrics.get(metric).getName(), metric);
    }
    table.addColumn(ColumnType.STRING, "e", "Event");

    for (TimeMachineCell cell : timeMachine.getCells()) {
      int rowIndex = table.addRow();
      table.setValue(rowIndex, 0, cell.getDate());
      for (int i = 0; i < metrics.length; i++) {
        JSONNumber value = (JSONNumber)cell.getValues()[i];
        if (value != null) {
          table.setValue(rowIndex, i + 1, value.doubleValue());
        }
      }
    }
    for (Event event : events) {
      int rowIndex = table.addRow();
      String eventStr = event.getName();
      if (event.getDescription() != null) {
        eventStr += " : " + event.getDescription();
      }
      table.setValue(rowIndex, 0, event.getDate());
      table.setValue(rowIndex, metrics.length + 1, eventStr);
    }
    return table;
  }

  private void lockMetricsList(boolean locked) {
    for (ListBox metricLb : metricsListBoxes) {
      metricLb.setEnabled(!locked);
    }
  }

  private String[] getSelectedMetrics() {
    List<String> metrics = new ArrayList<String>();
    for (ListBox metricLb : metricsListBoxes) {
      Metric metric = getSelectedMetric(metricLb);
      if (metric != null) {
        // inverting metrics
        metrics.add(0, metric.getKey());
      }
    }
    return metrics.toArray(new String[metrics.size()]);
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
    hPanel.add(singleScaleCheckBox);

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

    resetNumberFormats();
    int selectedCols = 0;
    for (ListBox metricLb : metricsListBoxes) {
      if (getSelectedMetric(metricLb) != null) {
        setNumberFormats(selectedCols++, getNumberFormat(getSelectedMetric(metricLb)));
      }
    }
    options.setOption("numberFormats", getNumberFormats());

    if (!singleScaleCheckBox.getValue()) {
      int[] scaledCols = new int[selectedCols];
      for (int i = 0; i < selectedCols; i++) {
        scaledCols[i] = i;
      }
      options.setScaleType(ScaleType.ALLFIXED);
      options.setScaleColumns(scaledCols);
    }
    return options;
  }

  private String getNumberFormat(Metric metric) {
    return metric.getType().equals("PERCENT") ? "0.0" : "0.##";
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
