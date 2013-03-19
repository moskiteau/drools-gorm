package org.drools.gorm.log


import org.drools.event.process.ProcessCompletedEvent
import org.drools.event.process.ProcessEventListener
import org.drools.event.process.ProcessNodeLeftEvent
import org.drools.event.process.ProcessNodeTriggeredEvent
import org.drools.event.process.ProcessStartedEvent
import org.drools.event.process.ProcessVariableChangedEvent
import org.jbpm.process.instance.ProcessInstance
import org.drools.gorm.GrailsIntegration

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.context.*
import grails.util.BuildSettingsHolder
import grails.util.GrailsUtil

/**
 *
 * @author seb
 */
class SessionListener implements ApplicationContextAware, ProcessEventListener,  Serializable {

    def applicationContext
    
    public SessionListener() {}
    
    public SessionListener(def _applicationContext) {
        this.applicationContext = _applicationContext
    }

    void setApplicationContext(ApplicationContext _applicationContext) {
        this.applicationContext = _applicationContext
    }    
    
    @Override
    public void beforeNodeTriggered(ProcessNodeTriggeredEvent arg0) {
        ProcessInstance pi = arg0.getProcessInstance()        
        println "ProcessInstance: $pi"
    }
        
    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent arg0) {
            
    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent arg0) {        
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent arg0) {
            
    }

    @Override
    public void afterProcessStarted(ProcessStartedEvent arg0) {            
            
    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent arg0) {}

    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent arg0) {
        
    }

    @Override
    public void beforeProcessCompleted(ProcessCompletedEvent arg0) {
        ProcessInstance pi = arg0.getProcessInstance()                
        if(pi.getState() == ProcessInstance.STATE_COMPLETED) {
            println "Process ${pi.id} ended with state ${getState(pi)}"
        }
    }

    @Override
    public void beforeProcessStarted(ProcessStartedEvent arg0) {        
    }

    @Override
    public void beforeVariableChanged(ProcessVariableChangedEvent arg0) {}
    
    private getState(ProcessInstance pi) {
        switch (pi.getState()) {
            case ProcessInstance.STATE_COMPLETED:
            return "STATE_COMPLETED "
            break
            case ProcessInstance.STATE_ABORTED:
            return "STATE_ABORTED"
            break
            case ProcessInstance.STATE_ACTIVE:
            return "STATE_ACTIVE"
            break
            case ProcessInstance.STATE_PENDING:
            return "STATE_PENDING"
            break
            case ProcessInstance.STATE_SUSPENDED:
            return "STATE_SUSPENDED"
            break
        }
    }
}



