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

package org.sonar.plugins.timeline.client;

import org.sonar.wsclient.gwt.AbstractCallback;
import org.sonar.wsclient.gwt.AbstractListCallback;
import org.sonar.wsclient.gwt.Sonar;
import org.sonar.wsclient.services.Event;
import org.sonar.wsclient.services.EventQuery;
import org.sonar.wsclient.services.TimeMachineData;
import org.sonar.wsclient.services.TimeMachineQuery;

import java.util.Date;
import java.util.List;

public abstract class TimelineLoader {

  private String resourceKey;
  private String[] metricsToLoad;
  private Date date;
  private List<Event> events;

  public TimelineLoader(String resourceKey, Date date, String[] metrics) {
    this.resourceKey = resourceKey;
    this.date = date;
    this.metricsToLoad = metrics;

    loadEventsData();
  }

  private void loadEventsData() {
    EventQuery eventQuery = new EventQuery(resourceKey)
        .setFrom(date, true);
    Sonar.getInstance().findAll(eventQuery, new AbstractListCallback<Event>() {
      @Override
      protected void doOnResponse(List<Event> result) {
        events = result;
        loadTimemachineData();
      }

      @Override
      protected void doOnError(int errorCode, String errorMessage) {
        noData();
      }

      @Override
      protected void doOnTimeout() {
        noData();
      }
    });
  }

  private void loadTimemachineData() {
    TimeMachineQuery query = new TimeMachineQuery(resourceKey)
        .setMetrics(metricsToLoad)
        .setFrom(date);
    Sonar.getInstance().find(query, new AbstractCallback<TimeMachineData>() {
      @Override
      protected void doOnResponse(TimeMachineData result) {
        data(metricsToLoad, result, events);
      }

      @Override
      protected void doOnError(int errorCode, String errorMessage) {
        noData();
      }

      @Override
      protected void doOnTimeout() {
        noData();
      }
    });
  }

  abstract void data(String[] metrics, TimeMachineData timemachine, List<Event> events);

  abstract void noData();

}
