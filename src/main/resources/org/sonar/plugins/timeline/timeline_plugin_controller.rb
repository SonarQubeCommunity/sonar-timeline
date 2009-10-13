#
# Sonar, open source software quality management tool.
# Copyright (C) 2009 SonarSource SA
# mailto:contact AT sonarsource DOT com
#
# Sonar is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# Sonar is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with Sonar; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
#
class Api::TimelineWebServiceController < Api::GwpResourcesController
  MAX_IN_ELEMENTS=990
  EMPTY_HASH={}
  
  private
  
  def rest_call
    metrics=Metric.by_keys(params[:metrics].split(','))
    snapshots = []
    if @resource
      snapshots=Snapshot.find(:all, :conditions => {:project_id => @resource.id, :status => Snapshot::STATUS_PROCESSED}, :include=> 'events', :order => 'created_at')
    end

    # Oracle limitation : no more than 1000 elements in IN clause
    if snapshots.length > MAX_IN_ELEMENTS
      size=snapshots.size
      snapshots=snapshots[size-MAX_IN_ELEMENTS .. size-1]
    end
      
    measures = find_measures(metrics, snapshots)
    
    snapshots_measures = {}
    if !measures.empty?  
      measures_by_sid = {}
      measures.each do |measure|
        measures_by_sid[measure.snapshot_id]||=[]
        measures_by_sid[measure.snapshot_id]<<measure
      end

      snapshots.each do |snapshot|
        measures_by_metrics = {}
        measures = measures_by_sid[snapshot.id] || []
        measures.each do |measure|
          measures_by_metrics[measure.metric_id] = measure
        end
        snapshots_measures[snapshot] = measures_by_metrics if !measures.empty?
      end

    end
    rest_render({:metrics => metrics, :snapshots_measures => snapshots_measures, :params => params})
  end
  
  
  
  def find_measures(metrics, snapshots)
    ProjectMeasure.find(:all,
          :select => 'project_measures.id,project_measures.value,project_measures.metric_id,project_measures.snapshot_id',
          :conditions => ['rules_category_id IS NULL AND rule_id IS NULL AND rule_priority IS NULL AND metric_id IN (?) AND snapshot_id IN (?)',
            metrics.select{|m| m.id}, snapshots.map{|s| s.id}])
  end
  
  def fill_gwp_data_table(objects, data_table)
    metrics = objects[:metrics]
    add_cols(data_table, metrics)
    
    objects[:snapshots_measures].each_pair do |snapshot, measures_by_metrics|
      measures_for_row(data_table, snapshot, measures_by_metrics, metrics)
    end
  end
  
  def add_cols(data_table, metrics)
    add_column(data_table, 'd', 'Date', TYPE_DATE)
    index = 1
    metrics.each do |metric|
      add_column(data_table, metric.key, metric.short_name, TYPE_NUMBER)
      add_column(data_table, "title#{index}", "title#{index}", TYPE_STRING)
      add_column(data_table, "text#{index}", "text#{index}", TYPE_STRING)
    end
  end
  
  def measures_for_row(data_table, snapshot, measures_by_metrics, metrics)
    row = new_row(data_table)
    add_row_value(row, Api::GwpJsonDate.new(snapshot.created_at))
    
    events = snapshot.events
    has_events = events.size > 0
    title = has_events ? "" : nil
    if has_events
      events.each do |event|
        title = title + event.fullname
        title = title + " : " + event.description if event.description && !event.description.empty?
        title = title + ", "
      end
      title = title[0, title.length-2]
    end
    index = 0
    metrics.each do |metric|
      measure = measures_by_metrics[metric.id]
      add_row_value(row, (measure.nil? ? nil : measure.value))
      add_row_value(row, title)
      add_row_value(row, nil)
      if index == 0
        title = nil
      end
      index = index + 1
    end
  end

  def add_row_value(row, value, formatted_value = nil)
    if value
      if formatted_value
        row[:c] << {:v => value, :f => formatted_value}
      else
        row[:c] << {:v => value}
      end
    else
      row[:c] << EMPTY_HASH
    end
  end
end