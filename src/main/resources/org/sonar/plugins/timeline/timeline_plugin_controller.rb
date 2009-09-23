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
class Api::GwpJsonDate
  @time
  
  def initialize(time)
    @time = time
  end
  
  def to_json(options = nil)
    "new Date(#{@time.year},#{@time.month-1},#{@time.day})"
  end
end
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
  
  def rest_to_json(objects)
    metrics = objects[:metrics]
    snapshots_measures = objects[:snapshots_measures]
      
    table = {:cols => to_json_cols_header(metrics), :rows => []}
    snapshots_measures.each_pair do |snapshot, measures_by_metrics|
      table[:rows] << to_json(snapshot, measures_by_metrics, metrics)
    end
    rest_gwp_ok(table)
  end
  
  def to_json_cols_header(metrics)
    cols = [{ :id => 'd', :label => 'Date', :type => 'date'}]
    index = 1
    metrics.each do |metric|
      cols << { :id => metric.key, :label => metric.short_name, :type => 'number'}
      cols << { :id => "title#{index}", :label => "title#{index}", :type => 'string'}
      cols << { :id => "text#{index}", :label => "text#{index}", :type => 'string'}
    end
    return cols
  end
  
  def to_json(snapshot, measures_by_metrics, metrics)
    json = [{:v => Api::GwpJsonDate.new(snapshot.created_at)}]
    
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
      json << {:v => measure.nil? ? nil : measure.value}
      json << {:v => title}
      json << {:v => nil}
      if index == 0
        title = nil
      end
      index = index + 1
    end
    return {:c => json}
  end

end