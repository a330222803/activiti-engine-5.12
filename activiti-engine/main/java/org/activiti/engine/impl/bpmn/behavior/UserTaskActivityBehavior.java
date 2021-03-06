/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.impl.bpmn.behavior;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.KPI;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.impl.TaskContext;
import org.activiti.engine.impl.calendar.DueDateBusinessCalendar;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskRejectLog;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.impl.task.TaskDefinition;
import org.apache.log4j.Logger;

import com.frameworkset.common.poolman.ConfigSQLExecutor;
import com.frameworkset.util.StringUtil;

/**
 * activity implementation for the user task.
 * 
 * @author Joram Barrez
 */
public class UserTaskActivityBehavior extends TaskActivityBehavior {
  private static Logger log = Logger.getLogger(UserTaskActivityBehavior.class);


  public UserTaskActivityBehavior(TaskDefinition taskDefinition) {
    this.taskDefinition = taskDefinition;
  }
  public List<String> getAssignee(TaskEntity task, ActivityExecution execution)
  {
	  List<String> assignees = new ArrayList<String>();
	  String assign = null;
	  if (taskDefinition.getAssigneeExpression() != null)
	  {
	      assign = (String) taskDefinition.getAssigneeExpression().getValue(execution);
	      if(!StringUtil.isEmpty(assign))
	      {
		      List<String> candiates = extractCandidates(assign);
		      assignees.addAll(candiates);
	      }
	  }
	  else if (!taskDefinition.getCandidateGroupIdExpressions().isEmpty())
	  {
		   
	      for (Expression groupIdExpr : taskDefinition.getCandidateGroupIdExpressions()) {
	        Object value = groupIdExpr.getValue(execution);
	        if (value instanceof String) {
	          List<String> candiates = extractCandidates((String) value);
	          assignees.addAll(candiates);
	        } else if (value instanceof Collection) {
	        	  assignees.addAll((Collection) value);
	        } else {
	          throw new ActivitiIllegalArgumentException("Expression did not resolve to a string or collection of strings");
	        }
	      }
			    
	  }
	  else if (!taskDefinition.getCandidateUserIdExpressions().isEmpty()) {
		    
	      for (Expression userIdExpr : taskDefinition.getCandidateUserIdExpressions()) {
	        Object value = userIdExpr.getValue(execution);
	        
	        if (value instanceof String) {
	          List<String> candiates = extractCandidates((String) value);
	          assignees.addAll(candiates);
	          
	          
	        } else if (value instanceof Collection) {
	        	 assignees.addAll((Collection) value);
	        }
	      }
	  }
		  
	  
	    return assignees;
  }
  private void recoredrejectedlog(ActivityExecution execution,TaskEntity newtask,boolean fromsequnce ) throws Exception
  {
	  TaskContext taskContext = execution.getTaskContext();
	  if(taskContext != null )
	  {
		  
		  if(taskContext.isIsrejected() )
		  {
			  if(taskContext.isReturntoreject())
			  {
				  ConfigSQLExecutor executor = Context.getProcessEngineConfiguration().getExtendExecutor();
				  executor.insert("recoredrejectedlog", taskContext.getRejectednode(),taskContext.getRejectedtaskid(),newtask.getId(),TaskService.op_returntorejected,execution.getProcessInstanceId());//rejectnode,rejecttaskid,newtaskid
			  }
			  else
			  {
				  ConfigSQLExecutor executor = Context.getProcessEngineConfiguration().getExtendExecutor();
				  executor.insert("recoredrejectedlog", taskContext.getRejectednode(),taskContext.getRejectedtaskid(),newtask.getId(),taskContext.getOp(),execution.getProcessInstanceId());//rejectnode,rejecttaskid,newtaskid
			  }
		  }
		  else if(taskContext.isIswithdraw())
		  {
			  ConfigSQLExecutor executor = Context.getProcessEngineConfiguration().getExtendExecutor();
			  executor.insert("recoredrejectedlog", 
					  taskContext.getRejectednode(),
					  taskContext.getRejectedtaskid(),
					  newtask.getId(),
					  taskContext.getOp(),execution.getProcessInstanceId());//rejectnode,rejecttaskid,newtaskid
		  }
		  else if(taskContext.isIsjump())
		  {
			  ConfigSQLExecutor executor = Context.getProcessEngineConfiguration().getExtendExecutor();
			  executor.insert("recoredrejectedlog", 
					  taskContext.getRejectednode(),
					  taskContext.getRejectedtaskid(),
					  newtask.getId(),
					  taskContext.getOp(),execution.getProcessInstanceId());//rejectnode,rejecttaskid,newtaskid
		  }
		  else if(fromsequnce)
		  {
			  TaskRejectLog taskRejectLog = taskContext.getTaskRejectLog();//串行多实例任务，后续任务记录驳回点轨迹记录（从前面的的任务复制驳回点轨迹）
			  if(taskRejectLog != null)
			  {
				  ConfigSQLExecutor executor = Context.getProcessEngineConfiguration().getExtendExecutor();
				  executor.insert("recoredrejectedlog", taskRejectLog.getREJECTNODE(),taskRejectLog.getREJECTTASKID(),newtask.getId(),taskRejectLog.getOPTYPE(),execution.getProcessInstanceId());//rejectnode,rejecttaskid,newtaskid
			  }
		  }
			  
	  }
  }
  public void execute(ActivityExecution execution) throws Exception {
	  execute( execution,false);
	  
  }
  
  public void execute(ActivityExecution execution,boolean fromsequence) throws Exception {
	  if(execution.getTaskContext().isCOPY() || execution.getTaskContext().isNotify())
	  {
		  Context.getCommandContext().getHistoryManager()
	      .recordCopyUseTaskActivityComplete((ExecutionEntity) execution);
			String BUSSINESSCONTROLCLASS = TaskContext.CopyTaskBehavior;
			JavaDelegate javaDelegate = Context.getJavaDelegate(BUSSINESSCONTROLCLASS);
			super.execute(execution, javaDelegate);
			
			super.leave(execution);
	  }
	  else if(execution.getTaskContext().isHasassignee())
		{
		  
		  	_execute(execution,fromsequence);
		  
		}
		else
		{
			
			 Context.getCommandContext().getHistoryManager()
		      .recordUseTaskActivityAutoComplete((ExecutionEntity) execution);
			String BUSSINESSCONTROLCLASS = execution.getTaskContext().getBUSSINESSCONTROLCLASS();
			if(StringUtil.isNotEmpty(BUSSINESSCONTROLCLASS))
			{
				JavaDelegate javaDelegate = Context.getJavaDelegate(BUSSINESSCONTROLCLASS);
				super.execute(execution, javaDelegate);
			}
			super.leave(execution);
			
		}
	  
  }
  private void _execute(ActivityExecution execution,boolean fromsequnce) throws Exception {
    TaskEntity task = TaskEntity.createAndInsert(execution);
    
    recoredrejectedlog( execution, task ,  fromsequnce);
    task.setExecution(execution);
    task.setTaskDefinition(taskDefinition);

    if (taskDefinition.getNameExpression() != null) {
      String name = (String) taskDefinition.getNameExpression().getValue(execution);
      task.setName(name);
    }

    if (taskDefinition.getDescriptionExpression() != null) {
      String description = (String) taskDefinition.getDescriptionExpression().getValue(execution);
      task.setDescription(description);
    }
    
    if(taskDefinition.getDueDateExpression() != null) {
      Object dueDate = taskDefinition.getDueDateExpression().getValue(execution);
      if(dueDate != null) {
        if (dueDate instanceof Date) {
          task.setDueDate((Date) dueDate);
        } else if (dueDate instanceof String) {
          task.setDueDate(new DueDateBusinessCalendar().resolveDuedate((String) dueDate)); 
        } else {
          throw new ActivitiIllegalArgumentException("Due date expression does not resolve to a Date or Date string: " + 
              taskDefinition.getDueDateExpression().getExpressionText());
        }
      }
    }

    if (taskDefinition.getPriorityExpression() != null) {
      final Object priority = taskDefinition.getPriorityExpression().getValue(execution);
      if (priority != null) {
        if (priority instanceof String) {
          try {
            task.setPriority(Integer.valueOf((String) priority));
          } catch (NumberFormatException e) {
            throw new ActivitiIllegalArgumentException("Priority does not resolve to a number: " + priority, e);
          }
        } else if (priority instanceof Number) {
          task.setPriority(((Number) priority).intValue());
        } else {
          throw new ActivitiIllegalArgumentException("Priority expression does not resolve to a number: " + 
                  taskDefinition.getPriorityExpression().getExpressionText());
        }
      }
    }
    
    handleAssignments(task, execution);
   
    // All properties set, now firing 'create' event
    task.fireEvent(TaskListener.EVENTNAME_CREATE);
  }

  public void signal(ActivityExecution execution, String signalName, Object signalData) throws Exception {
    leave(execution);
  }
  
//  public void signal(ActivityExecution execution, String signalName, Object signalData,TaskContext taskContext) throws Exception {
//	    leave(execution, taskContext);
//  }
  
  

  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected void handleAssignments(TaskEntity task, ActivityExecution execution) {
	  boolean parserkpi = false;
    if (taskDefinition.getAssigneeExpression() != null) {
    	  String assignee = null;
    
    	if(this.isUseMixUsetask())
    	{
    		if(execution.getTaskContext().isIsmulti())
    		{
    			assignee = (String)execution.getVariable(this.getCollectionElementVariable());
    		}
    		else
    		{
    			assignee = (String) taskDefinition.getAssigneeExpression().getValue(execution);
    		}
    	}
    	else
    	{    	
    		assignee = (String) taskDefinition.getAssigneeExpression().getValue(execution);
    	}
      List<String> candiates = new ArrayList<String>();
      if(assignee != null )      
      {
    	  if(assignee.indexOf(",") >0)
    	  {
    		 String[] ases = assignee.split("\\,"); 
    		 candiates = Arrays.asList(ases);
    		 task.addCandidateUsers(candiates);
    	  }
    	  else
    	  {
	    	  candiates.add(assignee);
	          task.setAssignee(assignee);
    	  }
    	  
      }
	  
      if(!parserkpi)//设置流程kpi指标
      {
    	  
    	
    	  KPI kpi = null;
    	  try
    	  {
    		  kpi = Context.getProcessEngineConfiguration().getKPIService().buildKPI(execution, candiates,task.getCreateTime());
    	  }
    	  catch(Exception e)
    	  {
    		  log.warn("BuildKPI failed:",e);
    	  }
          if(kpi != null)
          {
        	  task.setALERTTIME(kpi.getALERTTIME());
        	  task.setOVERTIME(kpi.getOVERTIME());
        	  task.setIS_CONTAIN_HOLIDAY(kpi.getIS_CONTAIN_HOLIDAY());
        	  task.setDURATION_NODE(kpi.getDURATION_NODE());
        	  task.setNOTICERATE(kpi.getNOTICERATE());
        	  task.synstatetoHistory();
          }
          parserkpi = true;
      }
    }

    if (!taskDefinition.getCandidateGroupIdExpressions().isEmpty()) {
      for (Expression groupIdExpr : taskDefinition.getCandidateGroupIdExpressions()) {
        Object value = groupIdExpr.getValue(execution);
        if (value instanceof String) {
          List<String> candiates = extractCandidates((String) value);
          task.addCandidateGroups(candiates);
        } else if (value instanceof Collection) {
          task.addCandidateGroups((Collection) value);
        } else {
          throw new ActivitiIllegalArgumentException("Expression did not resolve to a string or collection of strings");
        }
      }
    }

    if (!taskDefinition.getCandidateUserIdExpressions().isEmpty()) {
    
      for (Expression userIdExpr : taskDefinition.getCandidateUserIdExpressions()) {
        Object value = null;
        if(this.isUseMixUsetask())
    	{
    		if(execution.getTaskContext().isIsmulti())
    		{
    			value = (String)execution.getVariable(this.getCollectionElementVariable());
    		}
    		else
    		{
    			value = userIdExpr.getValue(execution);
    		}
    	}
    	else
    	{    	
    		value = userIdExpr.getValue(execution);
    	}
        if (value instanceof String) {
          List<String> candiates = extractCandidates((String) value);
          if(candiates.size() == 1)
          {
//        	  task.addCandidateUsers(candiates);
        	  task.setAssignee(candiates.get(0));
          }
          else
          {
        	  task.addCandidateUsers(candiates);
          }
          
          if(!parserkpi)//设置流程kpi指标
          {
        	  KPI kpi = null;
        	  try
        	  {
        		  kpi = Context.getProcessEngineConfiguration().getKPIService().buildKPI(execution, candiates,task.getCreateTime());
        	  }
        	  catch(Exception e)
        	  {
        		  log.warn("BuildKPI Service failed:",e);
        	  }
              if(kpi != null)
              {
            	  task.setALERTTIME(kpi.getALERTTIME());
            	  task.setOVERTIME(kpi.getOVERTIME());
            	  task.setIS_CONTAIN_HOLIDAY(kpi.getIS_CONTAIN_HOLIDAY());
            	  task.setDURATION_NODE(kpi.getDURATION_NODE());
            	  task.setNOTICERATE(kpi.getNOTICERATE());
            	  task.synstatetoHistory();
              }
              parserkpi = true;
          }
        } else if (value instanceof Collection) {
        	Collection c = (Collection)value;
        	 if(c.size() == 1)
             {
//           	  task.addCandidateUsers(candiates);
           	  	task.setAssignee(String.valueOf(c.iterator().next()));
             }
             else
             {
            	 task.addCandidateUsers(c);
             }	
          
          if(!parserkpi)
          {
        	  KPI kpi = null;
        	  try
        	  {
        		  kpi = Context.getProcessEngineConfiguration().getKPIService().buildKPI(execution, (Collection) value,task.getCreateTime());
        	  }
        	  catch(Exception e)
        	  {
        		  log.warn("BuildKPI Service failed:",e);
        	  }
        	
              if(kpi != null)
              {
            	  task.setALERTTIME(kpi.getALERTTIME());
            	  task.setOVERTIME(kpi.getOVERTIME());
            	  task.setIS_CONTAIN_HOLIDAY(kpi.getIS_CONTAIN_HOLIDAY());
            	  task.setDURATION_NODE(kpi.getDURATION_NODE());
            	  task.setNOTICERATE(kpi.getNOTICERATE());
            	  task.synstatetoHistory();
              }
              parserkpi = true;
          }
        } else {
          throw new ActivitiException("Expression did not resolve to a string or collection of strings");
        }
      }
    }
  }

  /**
   * Extract a candidate list from a string. 
   * 
   * @param str
   * @return 
   */
  protected List<String> extractCandidates(String str) {
    return Arrays.asList(str.split("[\\s]*,[\\s]*"));
  }
  
  // getters and setters //////////////////////////////////////////////////////
  
  public TaskDefinition getTaskDefinition() {
    return taskDefinition;
  }

  
}
