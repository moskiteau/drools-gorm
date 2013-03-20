package drools.gorm

import org.springframework.context.ApplicationContext
import org.drools.KnowledgeBase
import org.drools.KnowledgeBaseFactory
import org.drools.builder.KnowledgeBuilder
import org.drools.builder.KnowledgeBuilderFactory
import org.drools.builder.ResourceType
import org.drools.event.process.ProcessStartedEvent 
import org.drools.gorm.impl.ProcessEventListenerAdapter 
import org.drools.gorm.test.DroolsTest
import org.drools.io.ResourceFactory
import org.drools.runtime.Environment
import org.drools.runtime.StatefulKnowledgeSession
import org.drools.runtime.process.ProcessInstance
import org.drools.runtime.process.WorkItem
import org.jbpm.process.core.context.variable.VariableScope
import org.jbpm.process.instance.ContextInstance

import org.drools.gorm.processinstance.GormSignalManagerFactory;
import org.drools.gorm.processinstance.GormWorkItemManagerFactory;
import org.drools.gorm.processinstance.GormProcessInstanceManagerFactory;
import org.drools.gorm.session.SingleSessionCommandService;
import org.drools.gorm.test.DroolsTest;
import org.drools.io.ResourceFactory
import org.drools.builder.KnowledgeBuilder
import org.drools.builder.KnowledgeBuilderFactory
import org.drools.builder.ResourceType 
import org.drools.SessionConfiguration
import org.drools.KnowledgeBase
import org.drools.KnowledgeBaseFactory
import org.drools.runtime.Environment
import org.drools.runtime.StatefulKnowledgeSession
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.orm.hibernate3.SessionHolder
import org.springframework.orm.hibernate3.SessionFactoryUtils 

import org.hibernate.FlushMode

class TestingController {
    
    def grailsApplication
    def sessionFactory
    def kstore

    def index() {
        def str = """
			package org.drools.test
			import org.drools.gorm.test.DroolsTest
			rule rule1
			when
				\$droolsTest : DroolsTest(value == 1)
			then
				modify(\$droolsTest) {
					setValue(2L)
			    }
			end
		"""
		
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder()
        kbuilder.add(ResourceFactory.newByteArrayResource(str.getBytes()), ResourceType.DRL)
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase()

        if (kbuilder.hasErrors()) {
            throw new IllegalStateException("error compiling '${it.file}':\n"
                + kbuilder.errors)
        }

        kbase.addKnowledgePackages(kbuilder.getKnowledgePackages())

        Environment env = KnowledgeBaseFactory.newEnvironment()

        StatefulKnowledgeSession ksession = kstore.newStatefulKnowledgeSession(kbase, null, env)
        def sessionId = ksession.id

        def fact1 = new DroolsTest(name:"fact1", value:1)
        fact1.save(flush:true)
        def fact1Id = fact1.id
        def fact1Handle = ksession.insert(fact1)
        def fact2 = new DroolsTest(name:"fact2", value:1)
        fact2.save()  // without flush
        def fact2Id = fact2.id
        def fact2Handle = ksession.insert(fact2)

        ksession.fireAllRules()

        if(ksession) {
            ksession.dispose()
        }        

        restartDbSession()
        
        ksession = kstore.loadStatefulKnowledgeSession((int)sessionId, kbase, null, env)
        ksession.fireAllRules()
        
        assert 2 == ksession.objects.size()
        
        def fact1A = DroolsTest.get(fact1Id)
        def factA2 = DroolsTest.get(fact2Id)
        
        assert 2 == fact1A.value
        assert 2 == factA2.value

        if(ksession) {
            ksession.dispose()
        }
    }
    
    def test() {
        	
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder()
        def file = grailsApplication.parentContext.getResource("bpmn/test.bpmn2").file
        kbuilder.add(
            ResourceFactory.newUrlResource(file.toURL()),
            ResourceType.BPMN2
        )      
        
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase()
        if (kbuilder.hasErrors()) {
            throw new IllegalStateException("error compiling '${file}':\n"
                + kbuilder.errors)
        }

        kbase.addKnowledgePackages(kbuilder.getKnowledgePackages())
        Environment env = KnowledgeBaseFactory.newEnvironment()
        StatefulKnowledgeSession ksession = kstore.newStatefulKnowledgeSession(kbase, getGORMSessionConfig(), env)
        def sessionId = ksession.id
        println "created session $sessionId"
        
        println "adding eventListener..."
        //ksession.addEventListener(new org.drools.gorm.log.SessionListener(grailsApplication.mainContext as ApplicationContext));
                        
        Map<String, Object> mapping = new HashMap<String, Object>()
        ProcessInstance pi = ksession.createProcessInstance("com.bauna.droolsjbpm.gorm.test", null)
        
        println "created process instance: $pi"
        
        //ksession.startProcess("com.bauna.droolsjbpm.gorm.test", null)
        ksession.startProcessInstance(pi.id)
        //pi.start();
        
        if(ksession) {
            ksession.dispose()
        }        
        
        println "done executing bpmn2 workflow..."
    }
    
    def restartDbSession() {
        System.out.println("closing DB Session... ")
        this.unbindSession()
        this.bindSession()
        System.out.println("... DB Session restarted")
    }
    
    def getGORMSessionConfig() {
        Properties properties = new Properties();
        
        properties.setProperty( "drools.commandService",
            SingleSessionCommandService.class.getName() );
        properties.setProperty( "drools.processInstanceManagerFactory", 
            GormProcessInstanceManagerFactory.class.getName() );
        properties.setProperty( "drools.workItemManagerFactory", 
            GormWorkItemManagerFactory.class.getName() );
        properties.setProperty( "drools.processSignalManagerFactory",
            GormSignalManagerFactory.class.getName() );
        
        return new SessionConfiguration(properties);
    }    
    
    /**
     * Bind hibernate session to current thread
     */
    private boolean bindSession() {
        if(sessionFactory == null) {
            throw new IllegalStateException("No sessionFactory property provided")
        }
        final Object inStorage = TransactionSynchronizationManager.getResource(sessionFactory)
        if(inStorage != null) {
            ((SessionHolder)inStorage).getSession().flush()
            return false
        } else {
            def session = SessionFactoryUtils.getSession(sessionFactory, true)
            session.setFlushMode(FlushMode.AUTO)
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))
            return true
        }
    }
    
    /**
     * Bind hibernate session to current thread
     */
    private void unbindSession() {
        if(sessionFactory == null) {
            throw new IllegalStateException("No sessionFactory property provided")
        }
        try {
            final SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.unbindResource(sessionFactory)
            if(!FlushMode.MANUAL.equals(sessionHolder.getSession().getFlushMode())) {
                sessionHolder.getSession().flush()
            }
            SessionFactoryUtils.closeSession(sessionHolder.getSession())
        } catch(Exception e) {
            //todo the catch clause here might not be necessary as the only call to unbindSession() is wrapped in a try block already
            fireThreadException(e)
        }
    }         
}
