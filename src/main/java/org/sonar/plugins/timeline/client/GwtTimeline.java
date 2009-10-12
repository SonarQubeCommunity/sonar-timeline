/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource SA
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.sonar.api.web.gwt.client.AbstractPage;
import org.sonar.api.web.gwt.client.ResourceDictionary;
import org.sonar.api.web.gwt.client.webservices.BaseQueryCallback;
import org.sonar.api.web.gwt.client.webservices.MetricsQuery;
import org.sonar.api.web.gwt.client.webservices.Properties;
import org.sonar.api.web.gwt.client.webservices.PropertiesQuery;
import org.sonar.api.web.gwt.client.webservices.QueryCallBack;
import org.sonar.api.web.gwt.client.webservices.SequentialQueries;
import org.sonar.api.web.gwt.client.webservices.VoidResponse;
import org.sonar.api.web.gwt.client.webservices.WSMetrics;
import org.sonar.api.web.gwt.client.webservices.WSMetrics.Metric;
import org.sonar.api.web.gwt.client.webservices.WSMetrics.MetricsList;
import org.sonar.api.web.gwt.client.webservices.WSMetrics.Metric.ValueType;
import org.sonar.api.web.gwt.client.widgets.LoadingLabel;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.visualization.client.VisualizationUtils;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine.Options;
import com.google.gwt.visualization.client.visualizations.AnnotatedTimeLine.ScaleType;

public class GwtTimeline extends AbstractPage {
  
  public static final String GWT_ID = "org.sonar.plugins.timeline.GwtTimeline";
  public static final String DEFAULT_HEIGHT = "480";
  public static final String HEIGHT_PROP = "sonar.timeline.height";
  
  private SortedSet<WSMetrics.Metric> metrics = null;
  private Properties properties = null;
  private ListBox metricsListBox1 = new ListBox();
  private ListBox metricsListBox2 = new ListBox();
  private ListBox metricsListBox3 = new ListBox();
  private List<ListBox> metricsListBoxes = null;
  private SimplePanel tlPanel = null;

  public void onModuleLoad() {
    getRootPanel().add(new LoadingLabel());
    
    PropertiesQuery propsQ = new PropertiesQuery();
    BaseQueryCallback<Properties> propsCb = new BaseQueryCallback<Properties>() {
      public void onResponse(Properties response, JavaScriptObject jsonRawResponse) {
        properties = response;
      }
    };
    MetricsQuery metricsQ = MetricsQuery.get();
    metricsQ.excludeTypes(ValueType.BOOL, ValueType.DATA, ValueType.DISTRIB, ValueType.STRING, ValueType.LEVEL);
    QueryCallBack<MetricsList> metricsListCb = new BaseQueryCallback<MetricsList>() {
      public void onResponse(MetricsList response, JavaScriptObject jsonRawResponse) {
        metrics = orderMetrics(response.getMetrics());
        metricsListBoxes = Arrays.asList(metricsListBox1, metricsListBox2, metricsListBox3);
        loadListBox(metricsListBox1, WSMetrics.NCLOC);
        loadListBox(metricsListBox2, WSMetrics.COVERAGE);
        loadListBox(metricsListBox3, WSMetrics.VIOLATIONS_DENSITY);

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
      }
      
      private void loadListBox(ListBox metricsLb, Metric selected) {
        metricsLb.setStyleName("small");
        metricsLb.addItem("<none>", "");
        int index = 1;
        for (Metric metric : metrics) {
          metricsLb.addItem(metric.getName(), metric.getKey());
          if (metric.equals(selected)) {
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
    };
    // updating the WSMetrics dictonnary on metrics list info returned response
    metricsListCb = WSMetrics.getUpdateMetricsFromServer(metricsListCb);

    BaseQueryCallback<VoidResponse> queriesCb = new BaseQueryCallback<VoidResponse>() {
      public void onResponse(VoidResponse response, JavaScriptObject jsonRawResponse) {
        Runnable onLoadCallback = new Runnable() {
          public void run() {
            render();
            loadTimeLine();
          }
        };
        VisualizationUtils.loadVisualizationApi(onLoadCallback, AnnotatedTimeLine.PACKAGE);
      }
    };
    SequentialQueries queries = SequentialQueries.get().add(propsQ, propsCb).add(metricsQ, metricsListCb);
    queries.execute(queriesCb);
  }
  
  private SortedSet<Metric> orderMetrics(List<Metric> metrics) {
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
          String height = properties.get(GwtTimeline.HEIGHT_PROP, GwtTimeline.DEFAULT_HEIGHT);
          String width = content.getClientWidth() > 0 ? Integer.toString(content.getClientWidth()) : "800";
          Widget toRender = response.getTable().getNumberOfRows() > 0 ? 
              new AnnotatedTimeLine(response.getTable(), createOptions(), width + "px", height + "px") :
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
    for ( ListBox metricLb : metricsListBoxes) {
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
    return selected.length() > 0 ? WSMetrics.get(selected) : null;
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

    options.setOption("numberFormats",getNumberFormats());
    int[] scaledCols = new int[selectedCols];
    for ( int i = 0; i < selectedCols; i++) {
      scaledCols[i] = i;
    }
    options.setScaleColumns(scaledCols);
    return options;
  }
  
  private String getNumberFormat(Metric metric) {
    return metric.getType().equals(ValueType.PERCENT) ? "0.0" : "0.##";
  }

  private native JavaScriptObject getNumberFormats() /*-{
    return this.numberFormats;
  }-*/;
  
  private native void resetNumberFormats() /*-{
    this.numberFormats = {};
  }-*/;
  
  private native void setNumberFormats(int key, String numberFormat) /*-{
    this.numberFormats[key] = numberFormat;
  }-*/;

}
