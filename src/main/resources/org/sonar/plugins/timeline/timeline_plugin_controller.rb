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
class Api::RubyTimelineWebServiceController < Api::GwpResourcesController

  private
  
  def rest_call
    metrics=Metric.by_keys(params[:metrics].split(','))
    snapshots = []
    if @resource
      snapshots=Snapshot.find(:all, :conditions => {:project_id => @resource.id, :status => Snapshot::STATUS_PROCESSED}, :include=> 'events', :order => 'created_at')
    end

    # temporary fix for SONAR-1098
    if snapshots.length > 999
      loops_count = snapshots.length / 999
      loops_count = loops_count + 1 if snapshots.length % 999 > 0
      measures = []
      loops_count.times do |i|
        start_index = i * 999
        end_index = (i+1) * 999
        measures.concat(get_measures(metrics, snapshots[start_index...end_index]))
      end
    else
      measures = get_measures(metrics, snapshots)
    end
    
    snapshots_measures_by_resource = {}

    # ---------- SORT SNAPSHOTS
    if not measures.empty?  
      measures_by_sid = {}
      measures.each do |measure|
        measures_by_sid[measure.snapshot_id]||=[]
        measures_by_sid[measure.snapshot_id]<<measure
      end

      snapshots_measures = {}
      snapshots.each do |snapshot|
        measures_by_metrics = {}
        measures = measures_by_sid[snapshot.id] || []
        measures.each do |measure|
          measures_by_metrics[measure.metric_id] = measure
        end
        snapshots_measures[snapshot] = measures_by_metrics if not measures.empty?
      end

    end
    # ---------- FORMAT RESPONSE
    rest_render({ :metrics => metrics, :snapshots_measures => snapshots_measures, :params => params})
  end
  
  def select_columns_for_measures
    'project_measures.id,project_measures.value,project_measures.metric_id,project_measures.snapshot_id'
  end
  
  def get_measures(metrics, snapshots)
    ProjectMeasure.find(:all,
          :select => select_columns_for_measures,
          :conditions => ['rules_category_id IS NULL and rule_id IS NULL and rule_priority IS NULL and metric_id IN (?) and snapshot_id IN (?)',
            metrics.select{|m| m.id}, snapshots.map{|s| s.id}], :order => "project_measures.value")
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

end