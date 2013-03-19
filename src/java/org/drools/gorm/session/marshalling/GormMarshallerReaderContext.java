package org.drools.gorm.session.marshalling;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.HashMap;
import java.util.Map;

import org.drools.common.BaseNode;
import org.drools.common.InternalFactHandle;
import org.drools.common.InternalRuleBase;
import org.drools.common.InternalWorkingMemory;
import org.drools.marshalling.MarshallerFactory;
import org.drools.marshalling.ObjectMarshallingStrategy;
import org.drools.marshalling.ObjectMarshallingStrategyStore;
import org.drools.marshalling.impl.ProtobufInputMarshaller.PBActivationsFilter;
import org.drools.reteoo.LeftTuple;
import org.drools.reteoo.RightTuple;
import org.drools.rule.EntryPoint;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.KnowledgeRuntime;
import org.drools.spi.PropagationContext;

import org.drools.marshalling.*;
import org.drools.marshalling.impl.*;

public class GormMarshallerReaderContext extends MarshallerReaderContext {

    private ClassLoader userClassLoader = null;    

    public GormMarshallerReaderContext(InputStream stream,
            InternalRuleBase ruleBase,
            Map<Integer, BaseNode> sinks,
            ObjectMarshallingStrategyStore resolverStrategyFactory,
            Map<Integer, TimersInputMarshaller> timerReaders,
            Environment env) throws IOException {
        super(stream,
              ruleBase,
              sinks,
              resolverStrategyFactory,
              timerReaders,
              true,
              true,
              env );
    }

    public GormMarshallerReaderContext(InputStream stream,
                                   InternalRuleBase ruleBase,
                                   Map<Integer, BaseNode> sinks,
                                   ObjectMarshallingStrategyStore resolverStrategyFactory,
                                   Map<Integer, TimersInputMarshaller> timerReaders,
                                   boolean marshalProcessInstances,
                                   boolean marshalWorkItems,
                                   Environment env) throws IOException {
        super(stream,
                ruleBase,
                sinks,
                resolverStrategyFactory,
                timerReaders,
                true,
                true,
                env);
    }

    public ClassLoader getUserClassLoader() {
        return userClassLoader;
    }

    public void setUserClassLoader(ClassLoader userClassLoader) {
        this.userClassLoader = userClassLoader;
    }

    @Override
    protected Class< ?> resolveClass(ObjectStreamClass desc) throws IOException,
            ClassNotFoundException {
        try {
            return super.resolveClass(desc);
        } catch (ClassNotFoundException e) {
            if (getUserClassLoader() != null) {
                return getUserClassLoader().loadClass(desc.getName());
            }
            throw e;
        }
    }
    
}
