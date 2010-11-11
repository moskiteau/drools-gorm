package org.drools.gorm.processinstance;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.common.InternalKnowledgeRuntime;
import org.drools.definition.process.Process;
import org.drools.gorm.GrailsIntegration;
import org.drools.gorm.session.ProcessInstanceInfo;
import org.drools.process.instance.ProcessInstanceManager;
import org.drools.process.instance.impl.ProcessInstanceImpl;
import org.drools.runtime.process.ProcessInstance;

public class GormProcessInstanceManager
	    implements
	    ProcessInstanceManager {

    private InternalKnowledgeRuntime kruntime;
    private transient Map<Long, ProcessInstance> processInstances = new ConcurrentHashMap<Long, ProcessInstance>();

    public GormProcessInstanceManager(InternalKnowledgeRuntime kruntime) {
    	this.kruntime = kruntime;
    }

    public void addProcessInstance(ProcessInstance processInstance) {
    	ProcessInstanceInfo pii = GrailsIntegration.getGORMDomainService().getNewProcessInstanceInfo(processInstance);
    	GrailsIntegration.getGORMDomainService().saveDomain(pii);
    	((org.drools.process.instance.ProcessInstance) processInstance).setId( pii.getId() );
        pii.updateLastReadDate();
        internalAddProcessInstance(processInstance);
    }

    public void internalAddProcessInstance(ProcessInstance processInstance) {
        processInstances.put(processInstance.getId(), processInstance);
    }

    public ProcessInstance getProcessInstance(long id) {
    	org.drools.process.instance.ProcessInstance processInstance = (org.drools.process.instance.ProcessInstance) processInstances.get(id);
    	if (processInstance != null) {
    		return processInstance;
    	}
    	
        ProcessInstanceInfo processInstanceInfo = GrailsIntegration.getGORMDomainService().getProcessInstanceInfo(id);
        if ( processInstanceInfo == null ) {
            return null;
        }
        processInstanceInfo.updateLastReadDate();
        processInstance = 
        	processInstanceInfo.getProcessInstance(kruntime, this.kruntime.getEnvironment());
        Process process = kruntime.getKnowledgeBase().getProcess( processInstance.getProcessId() );
        if ( process == null ) {
            throw new IllegalArgumentException( "Could not find process " + processInstance.getProcessId() );
        }
        processInstance.setProcess( process );
        if ( processInstance.getKnowledgeRuntime() == null ) {
            processInstance.setKnowledgeRuntime( kruntime );
            ((ProcessInstanceImpl) processInstance).reconnect();
        }
        return processInstance;
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return Collections.unmodifiableCollection(processInstances.values());
    }

    public void removeProcessInstance(ProcessInstance processInstance) {
    	ProcessInstanceInfo processInstanceInfo = GrailsIntegration
     		.getGORMDomainService().getProcessInstanceInfo(processInstance.getId());
	     if ( processInstanceInfo != null ) {
	     	GrailsIntegration.getGORMDomainService().deleteDomain(processInstanceInfo);
	     }
	     internalRemoveProcessInstance(processInstance);
    }

    public void internalRemoveProcessInstance(ProcessInstance processInstance) {
    	processInstances.remove( processInstance.getId() );
    }
    
    public void clearProcessInstances() {
    	for (ProcessInstance processInstance: processInstances.values()) {
    		((ProcessInstanceImpl) processInstance).disconnect();
    	}
    }
}
