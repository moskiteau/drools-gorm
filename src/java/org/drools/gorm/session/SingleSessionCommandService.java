package org.drools.gorm.session;

import org.drools.gorm.processinstance.*;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


import org.drools.KnowledgeBase;
import org.drools.RuleBase;
import org.drools.SessionConfiguration;
import org.drools.command.Context;
import org.drools.command.Command;
import org.drools.command.impl.DefaultCommandService;
import org.drools.command.impl.GenericCommand;
import org.drools.command.impl.KnowledgeCommandContext;
import org.drools.command.impl.FixedKnowledgeCommandContext;
import org.drools.impl.StatefulKnowledgeSessionImpl;
import org.drools.command.impl.ContextImpl;
import org.drools.command.runtime.DisposeCommand;
import org.drools.common.EndOperationListener;
import org.drools.common.InternalKnowledgeRuntime;
import org.drools.gorm.GrailsIntegration;
import org.drools.process.instance.WorkItemManager;
import org.drools.marshalling.impl.MarshallingConfigurationImpl;
import org.drools.gorm.session.marshalling.GormSessionMarshallingHelper;
import org.drools.impl.KnowledgeBaseImpl;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.KnowledgeSessionConfiguration;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.InternalProcessRuntime;
//import org.drools.persistence.jpa.JpaJDKTimerService;
import org.drools.time.AcceptsTimerJobFactoryManager;

import org.drools.command.CommandService;
import org.drools.persistence.jpa.JpaPersistenceContextManager;
import org.drools.persistence.jta.JtaTransactionManager;
import org.drools.persistence.PersistenceContext;
import org.drools.persistence.PersistenceContextManager;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.jdbc.Work;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.SingleTableEntityPersister;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class SingleSessionCommandService implements org.drools.command.SingleSessionCommandService {

    private final static Map<Class<?>, String> entitiesTablenames = new ConcurrentHashMap<Class<?>, String>();
    
    private SessionInfo sessionInfo;
    private GormSessionMarshallingHelper marshallingHelper;
    
    private StatefulKnowledgeSession ksession;
    private Environment env;
    private KnowledgeCommandContext kContext;
    private CommandService commandService;    
    private volatile boolean doRollback;    
    
    public void checkEnvironment(Environment env) {
        configureEnvironment();
    }

    private void configureEnvironment() {
        env.set(HasBlob.GORM_UPDATE_SET, new CopyOnWriteArraySet<HasBlob<?>>());
    }

    public SingleSessionCommandService(RuleBase ruleBase,
            SessionConfiguration conf,
            Environment env) {
        this(new KnowledgeBaseImpl(ruleBase),
                conf,
                env);
    }

    public SingleSessionCommandService(int sessionId,
            RuleBase ruleBase,
            SessionConfiguration conf,
            Environment env) {
        this(sessionId,
                new KnowledgeBaseImpl(ruleBase),
                conf,
                env);
    }

    public SingleSessionCommandService(KnowledgeBase kbase,
            KnowledgeSessionConfiguration conf,
            Environment env) {

        if (conf == null) {
            conf = new SessionConfiguration();
        }
        
        this.env = env;
        checkEnvironment(this.env);

        this.sessionInfo = GrailsIntegration.getGormDomainService().getNewSessionInfo(env);

        // create session but bypass command service
        this.ksession = kbase.newStatefulKnowledgeSession(conf, this.env);

        this.kContext = new FixedKnowledgeCommandContext(
                null,
                null,
                null,
                this.ksession,
                null);

        //((JpaJDKTimerService) ((InternalKnowledgeRuntime) ksession).getTimerService()).getTimerJobFactoryManager().setCommandService(this);
        ((AcceptsTimerJobFactoryManager) ((InternalKnowledgeRuntime) ksession).getTimerService()).getTimerJobFactoryManager().setCommandService( this );
        this.marshallingHelper = new GormSessionMarshallingHelper(this.ksession, conf);

        //MarshallingConfigurationImpl config = (MarshallingConfigurationImpl) this.marshallingHelper.getMarshaller().getMarshallingConfiguration();
        //config.setMarshallProcessInstances(false);
        //config.setMarshallWorkItems(false);

        this.sessionInfo.setMarshallingHelper(this.marshallingHelper);
        ((InternalKnowledgeRuntime) this.ksession).setEndOperationListener(new EndOperationListenerImpl(this.sessionInfo));

        // Use the App scoped EntityManager if the user has provided it, and it is open.
        PlatformTransactionManager txManager = GrailsIntegration.getTransactionManager();
        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = txManager.getTransaction(txDef);
        try {
            registerRollbackSync();
            GrailsIntegration.getGormDomainService().saveDomain(this.sessionInfo);
            updateBlobs(false);
            txManager.commit(status);
        } catch (Exception t1) {
            try {
                txManager.rollback(status);
            } catch (Throwable t2) {
                throw new RuntimeException("Could not commit session or rollback", t2);
            }
            throw new RuntimeException("Could not commit session", t1);
        }

        // update the session id to be the same as the session info id
        ((InternalKnowledgeRuntime) ksession).setId(this.sessionInfo.getId());

    }

    public SingleSessionCommandService(int sessionId,
            KnowledgeBase kbase,
            KnowledgeSessionConfiguration conf,
            Environment env) {
        if (conf == null) {
            conf = new SessionConfiguration();
        }

        this.env = env;

        checkEnvironment(this.env);

        initKsession(sessionId, kbase, conf);
    }

    public void initKsession(int sessionId,
            KnowledgeBase kbase,
            KnowledgeSessionConfiguration conf) {
        if (!doRollback && this.ksession != null) {
            return;
            // nothing to initialise
        }

        this.doRollback = false;

        try {
            this.sessionInfo = GrailsIntegration.getGormDomainService().getSessionInfo(sessionId, env);
        } catch (Exception e) {
            throw new RuntimeException("Could not find session data for id " + sessionId,
                    e);
        }

        if (sessionInfo == null) {
            throw new RuntimeException("Could not find session data for id " + sessionId);
        }

        if (this.marshallingHelper == null) {
            // this should only happen when this class is first constructed
            this.marshallingHelper = new GormSessionMarshallingHelper(kbase, conf, env);
//            MarshallingConfigurationImpl config = (MarshallingConfigurationImpl) this.marshallingHelper.getMarshaller().getMarshallingConfiguration();
//            config.setMarshallProcessInstances(false);
//            config.setMarshallWorkItems(false);
        }

        this.sessionInfo.setMarshallingHelper(this.marshallingHelper);

        ((SessionConfiguration) conf).getTimerJobFactoryManager().setCommandService(this);

        // if this.ksession is null, it'll create a new one, else it'll use the existing one
        this.ksession = this.marshallingHelper.loadSnapshot(this.sessionInfo.getData(), this.ksession);

        // update the session id to be the same as the session info id
        ((InternalKnowledgeRuntime) ksession).setId(this.sessionInfo.getId());

        ((InternalKnowledgeRuntime) this.ksession).setEndOperationListener(new EndOperationListenerImpl(this.sessionInfo));

        if (this.kContext == null) {
            // this should only happen when this class is first constructed
            this.kContext = new FixedKnowledgeCommandContext(new ContextImpl("ksession",
                    null),
                    null,
                    null,
                    this.ksession,
                    null);
        }

        this.commandService = new DefaultCommandService(kContext);
    }

    public void initTransactionManager(Environment env) {
        Object tm = env.get(EnvironmentName.TRANSACTION_MANAGER);
        if (env.get(EnvironmentName.PERSISTENCE_CONTEXT_MANAGER) != null
                && env.get(EnvironmentName.TRANSACTION_MANAGER) != null) {
            System.out.println("TransactionManager & Persistence Context already instantiated.");
        } else {
            if (tm != null && tm.getClass().getName().startsWith("org.springframework")) {
                System.out.println("Instantiating  DroolsSpringTransactionManager");
            } else {
                System.out.println("Instantiating  JtaTransactionManager" + tm);
            }
            PlatformTransactionManager txManager = GrailsIntegration.getTransactionManager();
//            env.set( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER,
//                     this.jpm );
            env.set(EnvironmentName.TRANSACTION_MANAGER, txManager);

            SessionFactory sf = GrailsIntegration.getCurrentSessionFactory();

        }
    }

    public class EndOperationListenerImpl implements EndOperationListener {

        private SessionInfo info;

        public EndOperationListenerImpl(SessionInfo info) {
            this.info = info;
        }

        public void endOperation(InternalKnowledgeRuntime kruntime) {
            this.info.setLastModificationDate(new Date(kruntime.getLastIdleTimestamp()));
        }
    }

    public Context getContext() {
        return this.kContext;
    }

    public synchronized <T> T execute(Command<T> command) {

        PlatformTransactionManager txManager = GrailsIntegration.getTransactionManager();
        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = txManager.getTransaction(txDef);

        try {
            initKsession(this.sessionInfo.getId(),
                    this.marshallingHelper.getKbase(),
                    this.marshallingHelper.getConf());

            registerRollbackSync();
            configureEnvironment();

            T result = ((GenericCommand<T>) command).execute(this.kContext);

            updateBlobs(command instanceof DisposeCommand);
            txManager.commit(status);

            return result;
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.out.println("execute RuntimeException " + e.getMessage());
            status.setRollbackOnly();
            throw e;
        } catch (Exception e) {
            System.out.println("execute Exception " + e.getMessage());
            status.setRollbackOnly();
            throw new RuntimeException("Wrapped exception see cause", e);
        }
    }

    private void updateBlobs(final boolean isDispose) {
        final Set<HasBlob<?>> updates = (Set<HasBlob<?>>) env.get(HasBlob.GORM_UPDATE_SET);
        configureEnvironment();
        Session session = GrailsIntegration.getCurrentSession();

        session.doWork(new Work() {

            @Override
            public void execute(Connection conn) throws SQLException {
                boolean hasStoredKSession = false;
                for (final HasBlob<?> hasBlob : updates) {
                    if (!hasBlob.isDeleted()) {
                        persistBlob(isDispose, conn, hasBlob);
                    }
                    hasStoredKSession |= SingleSessionCommandService.this.sessionInfo == hasBlob;
                }
                if (!hasStoredKSession) {
                    persistBlob(isDispose, conn, SingleSessionCommandService.this.sessionInfo);
                }
            }

            private void persistBlob(boolean isDispose, Connection conn, HasBlob<?> hasBlob) throws SQLException {
                byte[] blob = null;
                try {
                    blob = hasBlob.generateBlob();
                } catch (RuntimeException e) {
                    if (!isDispose) {
                        System.out.println("--------> ERROR: " + e.getMessage());
                        throw e;
                    }
                }
                if (blob != null && blob.length > 0) {
                    PreparedStatement ps = conn.prepareStatement("update "
                            + getTablename(hasBlob)
                            + " set data = ? where id = ?");
                    try {
                        int i = 1;
                        ps.setBinaryStream(i++, new ByteArrayInputStream(blob), blob.length);
                        ps.setLong(i++, hasBlob.getId().longValue());
                        int count = ps.executeUpdate();
                        if (count != 1) {
                            throw new IllegalStateException("update blob for id:  " + hasBlob
                                    + " has failed, count: " + count);
                        }
                    } finally {
                        ps.close();
                    }
                }
            }
        });
    }

    public void dispose() {
        if (ksession != null) {
            System.out.println("Called dispose on SingleSessionCommandService");
            ksession.dispose();
        }
    }

    public int getSessionId() {
        return sessionInfo.getId();
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> getSyncronizationMap() {
        Map<Object, Object> map = (Map<Object, Object>) env.get("synchronizations");
        if (map == null) {
            map = Collections.synchronizedMap(new IdentityHashMap<Object, Object>());
            env.set("synchronizations", map);
        }
        return map;
    }

    private void registerRollbackSync() throws IllegalStateException {
        Map<Object, Object> map = getSyncronizationMap();

        if (!map.containsKey(this)) {
            TransactionSynchronizationManager.registerSynchronization(new SynchronizationImpl(this));
            map.put(this, this);
        }
    }

    private class SynchronizationImpl
            implements TransactionSynchronization {

        SingleSessionCommandService service;

        public SynchronizationImpl(SingleSessionCommandService service) {
            this.service = service;
        }

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    this.service.rollback();
                }
                this.service.getSyncronizationMap().remove(this.service);
                StatefulKnowledgeSessionImpl ksession = ((StatefulKnowledgeSessionImpl) this.service.ksession);

                // clean up cached process and work item instances
                if (ksession != null) {
                    ((InternalKnowledgeRuntime) ksession).getProcessRuntime().clearProcessInstances();
                    ((WorkItemManager) ksession.getWorkItemManager()).clear();
                }
            } catch (IllegalStateException ise) {
                System.out.println("IllegalStateException: ksession was already disposed");
            }
        }

        @Override
        public void suspend() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void beforeCommit(boolean readOnly) {
        }

        @Override
        public void afterCommit() {
        }
    }

    private void rollback() {
        this.doRollback = true;
    }

    private String getTablename(HasBlob<?> hasBlob) {
        String tablename = entitiesTablenames.get(hasBlob.getClass());
        if (tablename == null) {
            SessionFactory sf = GrailsIntegration.getCurrentSessionFactory();
            ClassMetadata classMetadata = sf.getClassMetadata(hasBlob.getClass());
            if (classMetadata instanceof SingleTableEntityPersister) {
                SingleTableEntityPersister step = (SingleTableEntityPersister) classMetadata;
                tablename = step.getTableName();
                entitiesTablenames.put(hasBlob.getClass(), tablename);
            }
        }
        return tablename;

    }
}
