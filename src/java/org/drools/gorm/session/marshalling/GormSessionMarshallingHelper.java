package org.drools.gorm.session.marshalling;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.drools.KnowledgeBase;
import org.drools.gorm.marshalling.GrailsPlaceholderResolverStrategy;
import org.drools.gorm.session.SessionInfo;
import org.drools.marshalling.Marshaller;
import org.drools.marshalling.MarshallerFactory;
import org.drools.marshalling.ObjectMarshallingStrategy;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.KnowledgeSessionConfiguration;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.persistence.SessionMarshallingHelper;

public class GormSessionMarshallingHelper {

    private KnowledgeBase kbase;
    private KnowledgeSessionConfiguration conf;
    private StatefulKnowledgeSession ksession;
    private Marshaller marshaller;
    private Environment env;

    /**
     * Exist Info, so load session from here
     *
     * @param info
     * @param ruleBase
     * @param conf
     * @param marshallingConfiguration
     */
    public GormSessionMarshallingHelper(SessionInfo info,
            KnowledgeBase kbase,
            KnowledgeSessionConfiguration conf,
            Environment env) {
        info.setMarshallingHelper(this);
        this.kbase = kbase;
        this.conf = conf;
        this.env = env;
        this.marshaller = MarshallerFactory.newMarshaller(kbase, buildObjectMashallingStrategies());
        loadSnapshot(info.getData());
    }

    public GormSessionMarshallingHelper(KnowledgeBase kbase,
            KnowledgeSessionConfiguration conf, Environment env) {
        this.kbase = kbase;
        this.conf = conf;
        this.env = env;
        this.marshaller = MarshallerFactory.newMarshaller(kbase, buildObjectMashallingStrategies());
    }

    /**
     * new session, don't write now as info will request it on update callback
     *
     * @param info
     * @param session
     * @param conf
     * @param marshallingConfiguration
     */
    public GormSessionMarshallingHelper(StatefulKnowledgeSession ksession,
            KnowledgeSessionConfiguration conf) {
        this.ksession = ksession;
        this.kbase = ksession.getKnowledgeBase();
        this.conf = conf;
        this.env = ksession.getEnvironment();
        this.marshaller = MarshallerFactory.newMarshaller(kbase, buildObjectMashallingStrategies());

    }

    public Marshaller getMarshaller() {
        return this.marshaller;
    }

    private ObjectMarshallingStrategy[] buildObjectMashallingStrategies() {
        ObjectMarshallingStrategy[] strategies = (ObjectMarshallingStrategy[]) this.env.get(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES);
        if (strategies != null) {
            List<ObjectMarshallingStrategy> aux = new ArrayList<ObjectMarshallingStrategy>(strategies.length + 1);
            boolean exists = false;
            for (int i = 0; i < strategies.length && !exists; i++) {
                exists = strategies[i] instanceof GrailsPlaceholderResolverStrategy;
            }
            if (!exists) {
                aux.add(new GrailsPlaceholderResolverStrategy());
            }
            aux.addAll(Arrays.asList(strategies));
            strategies = aux.toArray(new ObjectMarshallingStrategy[aux.size()]);
        } else {
            strategies = new ObjectMarshallingStrategy[]{
                new GrailsPlaceholderResolverStrategy(),
                MarshallerFactory.newSerializeMarshallingStrategy()};
        }
        this.env.set(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, strategies);
        return strategies;
    }

    public byte[] getSnapshot() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            marshaller.marshall(baos, ksession);
        } catch (IOException e) {
            throw new RuntimeException("Unable to get session snapshot", e);
        }
        return baos.toByteArray();
    }

    public StatefulKnowledgeSession loadSnapshot(byte[] bytes, StatefulKnowledgeSession ksession) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            if (ksession == null) {
                this.ksession = this.marshaller.unmarshall(bais, this.conf, this.env);
            } else {
                this.ksession = ksession;
                this.marshaller.unmarshall(bais, this.ksession);
            }
            return this.ksession;
        } catch (Exception e) {
            throw new RuntimeException("Unable to load session snapshot", e);
        }
    }

    public StatefulKnowledgeSession loadSnapshot(byte[] bytes) {
        return loadSnapshot(bytes, ksession);
    }

    public StatefulKnowledgeSession getObject() {
        return ksession;
    }

    public KnowledgeBase getKbase() {
        return kbase;
    }

    public KnowledgeSessionConfiguration getConf() {
        return conf;
    }
}
