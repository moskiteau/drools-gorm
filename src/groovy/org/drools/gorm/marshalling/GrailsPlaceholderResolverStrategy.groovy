package org.drools.gorm.marshalling

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.HashMap
import java.util.IdentityHashMap
import java.util.Map

import javax.persistence.Id;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


import org.drools.marshalling.ObjectMarshallingStrategyAcceptor
import org.drools.common.DroolsObjectInputStream
import org.drools.marshalling.ObjectMarshallingStrategy.Context
import org.drools.marshalling.ObjectMarshallingStrategy
import org.drools.runtime.Environment
import org.drools.runtime.EnvironmentName

import org.drools.gorm.GrailsIntegration
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

public class GrailsPlaceholderResolverStrategy implements ObjectMarshallingStrategy {
    
    private GrailsApplication grailsApplication
    private Environment env;
    
    public GrailsPlaceholderResolverStrategy() {
        this.grailsApplication = GrailsIntegration.getGrailsApplication()
    }  
    
    public boolean accept(Object object) {
        return this.grailsApplication.isArtefactOfType( DomainClassArtefactHandler.TYPE, (Class) object.class)
    }

    public void write(ObjectOutputStream os, Object object) throws IOException {
        os.writeObject(object.getClass().getCanonicalName())
        os.writeObject((Serializable) object.id)
    }
    
    public Object read(ObjectInputStream is) throws IOException, ClassNotFoundException {
        String canonicalName = is.readUTF()
        Object domain = this.grailsApplication.getClassForName(canonicalName)
        Serializable id = is.readObject();
        return domain.get(id)
    }      
    
    public byte[] marshal(Context context,
                          ObjectOutputStream os, 
                          Object object) throws IOException {
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( buff );
        oos.writeUTF(object.getClass().getCanonicalName());
        def id = (Serializable) object.id
        println "writing: ${object.getClass().getCanonicalName()}(${id})"
        oos.writeObject((Serializable) object.id);
        oos.close();
        return buff.toByteArray();
    }

    public Object unmarshal(Context context,
                            ObjectInputStream ois,
                            byte[] object,
                            ClassLoader classloader) throws IOException,
                                                    ClassNotFoundException {
        
        DroolsObjectInputStream is = new DroolsObjectInputStream( new ByteArrayInputStream( object ), classloader );
        String canonicalName = is.readUTF();
        println "trying to unmarshal with GrailsPlaceholderResolverStrategy $canonicalName"
        Object domain = this.grailsApplication.getClassForName(canonicalName)
        Serializable id = is.readObject();
        println "trying to get ${canonicalName}(${id.toString()})"
        return domain.get(id)
    }
        
    public Context createContext() {
        // no need for context
        return null;
    }
        
}
