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
package org.activiti.engine.impl.pvm.runtime;

import java.util.List;

import org.activiti.engine.delegate.ExecutionListener;
import org.activiti.engine.impl.TaskContext;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.pvm.PvmException;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.ScopeImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Tom Baeyens
 */
public class AtomicOperationTransitionNotifyListenerTake implements AtomicOperation {
  
  private static Logger log = LoggerFactory.getLogger(AtomicOperationTransitionNotifyListenerTake.class);
  
  public boolean isAsync(InterpretableExecution execution) {
    return false;
  }

  public void execute(InterpretableExecution execution) {
    TransitionImpl transition = execution.getTransition();
    
    List<ExecutionListener> executionListeners = transition.getExecutionListeners();
    int executionListenerIndex = execution.getExecutionListenerIndex();
    
    if (executionListeners.size()>executionListenerIndex) {
      execution.setEventName(org.activiti.engine.impl.pvm.PvmEvent.EVENTNAME_TAKE);
      execution.setEventSource(transition);
      ExecutionListener listener = executionListeners.get(executionListenerIndex);
      try {
        listener.notify(execution);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new PvmException("couldn't execute event listener : "+e.getMessage(), e);
      }
      execution.setExecutionListenerIndex(executionListenerIndex+1);
      execution.performOperation(this);

    } else {
      log.debug("{} takes transition {}", execution, transition);
      execution.setExecutionListenerIndex(0);
      execution.setEventName(null);
      execution.setEventSource(null);

      ActivityImpl activity = (ActivityImpl) execution.getActivity();
      ActivityImpl nextScope = findNextScope(activity.getParent(), transition.getDestination());
      
      execution.setActivity(nextScope);
      execution.removeBussinessLog();
      if(nextScope.isUserTask())
      {
    	  TaskContext oldTaskContext = execution.getTaskContext();
    	  TaskContext newTaskContext = Context.createTaskContext((ExecutionEntity)execution, nextScope.getId());
    	  if(oldTaskContext != null )
		  {
    		  
//			  newTaskContext.setIsrejected(oldTaskContext.isIsrejected());
//			  newTaskContext.setReturntoreject(oldTaskContext.isReturntoreject());
//			  newTaskContext.setRejecttype(oldTaskContext.getRejecttype());
//			  newTaskContext.setRejectedtaskid(oldTaskContext.getRejectedtaskid());
//			  newTaskContext.setRejectednode(oldTaskContext.getRejectednode());
    		  if(oldTaskContext.isIsrejected() || oldTaskContext.isIsjump() || oldTaskContext.isIswithdraw())
    		  {
			  
				  newTaskContext.setIsrejected(oldTaskContext.isIsrejected());
				  newTaskContext.setIswithdraw(oldTaskContext.isIswithdraw());
				  newTaskContext.setIsjump(oldTaskContext.isIsjump());
				  newTaskContext.setOp(oldTaskContext.getOp());
				  newTaskContext.setReturntoreject(oldTaskContext.isReturntoreject());
				  newTaskContext.setRejecttype(oldTaskContext.getRejecttype());
				  newTaskContext.setRejectedtaskid(oldTaskContext.getRejectedtaskid());
				  newTaskContext.setRejectednode(oldTaskContext.getRejectednode());
    		  }
		  }
      }
      else
      {
    	  execution.setTaskContext(null); 
      }
      execution.performOperation(TRANSITION_CREATE_SCOPE);
    }
  }

  /** finds the next scope to enter.  the most outer scope is found first */
  public static ActivityImpl findNextScope(ScopeImpl outerScopeElement, ActivityImpl destination) {
    ActivityImpl nextScope = destination;
    while( (nextScope.getParent() instanceof ActivityImpl)
           && (nextScope.getParent() != outerScopeElement)
         ) {
      nextScope = (ActivityImpl) nextScope.getParent();
    }
    return nextScope;
  }
}
