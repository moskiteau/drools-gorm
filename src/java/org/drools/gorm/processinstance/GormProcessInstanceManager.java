package org.drools.gorm.processinstance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.drools.common.InternalKnowledgeRuntime;
import org.drools.runtime.process.InternalProcessRuntime;
import org.drools.gorm.GrailsIntegration;
import org.drools.gorm.session.ProcessInstanceInfo;
import org.drools.runtime.process.ProcessInstance;
import org.jbpm.process.instance.ProcessInstanceManager;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.node.StateBasedNodeInstance;

public class GormProcessInstanceManager implements ProcessInstanceManager {

    private InternalKnowledgeRuntime kruntime;
    private transient Map<Long, ProcessInstance> processInstances = new ConcurrentHashMap<Long, ProcessInstance>();

    public GormProcessInstanceManager(InternalKnowledgeRuntime kruntime) {
        this.kruntime = kruntime;
    }

    public void addProcessInstance(ProcessInstance processInstance) {
        ProcessInstanceInfo pii = GrailsIntegration.getGormDomainService().getNewProcessInstanceInfo((org.jbpm.process.instance.ProcessInstance) processInstance, kruntime.getEnvironment());
        GrailsIntegration.getGormDomainService().saveDomain(pii);
        ((org.jbpm.process.instance.ProcessInstance) processInstance).setId(pii.getId());
        pii.updateLastReadDate();
        internalAddProcessInstance(processInstance);
    }

    @Override
    public void internalAddProcessInstance(ProcessInstance processInstance) {
        processInstances.put(processInstance.getId(), processInstance);
    }

    @Override
    public ProcessInstance getProcessInstance(long id) {
        System.out.println("GETTING Process instance for " + id);
        org.jbpm.process.instance.ProcessInstance processInstance =
                (org.jbpm.process.instance.ProcessInstance) processInstances.get(id);
        if (processInstance != null) {
            return processInstance;
        }

        System.out.println("Process instance is not null...");
        ProcessInstanceInfo processInstanceInfo = GrailsIntegration.getGormDomainService().getProcessInstanceInfo(id, this.kruntime.getEnvironment());
        if (processInstanceInfo == null) {
            return null;
        }
        processInstanceInfo.updateLastReadDate();
        processInstance =
                processInstanceInfo.getProcessInstance(kruntime, this.kruntime.getEnvironment());
        org.drools.definition.process.Process process = kruntime.getKnowledgeBase().getProcess(processInstance.getProcessId());
        if (process == null) {
            throw new IllegalArgumentException("Could not find process " + processInstance.getProcessId());
        }
        processInstance.setProcess(process);
        if (processInstance.getKnowledgeRuntime() == null) {
            processInstance.setKnowledgeRuntime(kruntime);
            ((ProcessInstanceImpl) processInstance).reconnect();
        }
        return processInstance;
    }

    @Override
    public Collection<ProcessInstance> getProcessInstances() {
        return Collections.unmodifiableCollection(processInstances.values());
    }

    @Override
    public void removeProcessInstance(ProcessInstance processInstance) {
        ProcessInstanceInfo processInstanceInfo = GrailsIntegration.getGormDomainService().getProcessInstanceInfo(processInstance.getId(), this.kruntime.getEnvironment());
        if (processInstanceInfo != null) {
            GrailsIntegration.getGormDomainService().deleteDomain(processInstanceInfo);
        }
        internalRemoveProcessInstance(processInstance);
    }

    @Override
    public void internalRemoveProcessInstance(ProcessInstance processInstance) {
        processInstances.remove(processInstance.getId());
    }

    @Override
    public void clearProcessInstances() {
        for (ProcessInstance processInstance : processInstances.values()) {
            ((ProcessInstanceImpl) processInstance).disconnect();
        }
    }

    @Override
    public void clearProcessInstancesState() {
        // at this point only timers are considered as state that needs to be cleared
        //TimerManager timerManager = ((InternalProcessRuntime) kruntime.getProcessRuntime()).getTimerManager();

        for (ProcessInstance processInstance : new ArrayList<ProcessInstance>(processInstances.values())) {
            //WorkflowProcessInstance pi = ((WorkflowProcessInstance) processInstance);
            ((ProcessInstanceImpl) processInstance).disconnect();

//            for (org.drools.runtime.process.NodeInstance nodeInstance : pi.getNodeInstances()) {
//                if (nodeInstance instanceof StateBasedNodeInstance) {
//                    List<Long> timerIds = ((StateBasedNodeInstance) nodeInstance).getTimerInstances();
//                    if (timerIds != null) {
//                        for (Long id : timerIds) {
//                            timerManager.cancelTimer(id);
//                        }
//                    }
//                }
//            }

        }
    }
}
