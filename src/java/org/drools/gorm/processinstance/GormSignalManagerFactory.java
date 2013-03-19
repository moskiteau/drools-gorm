package org.drools.gorm.processinstance;

import org.drools.common.InternalKnowledgeRuntime;
import org.jbpm.process.instance.event.SignalManager;
import org.jbpm.process.instance.event.SignalManagerFactory;

public class GormSignalManagerFactory implements SignalManagerFactory {

	public SignalManager createSignalManager(InternalKnowledgeRuntime kruntime) {
		return new GormSignalManager(kruntime);
	}

}
