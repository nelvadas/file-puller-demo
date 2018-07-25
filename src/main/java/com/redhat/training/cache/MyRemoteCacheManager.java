package com.redhat.training.cache;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

public class MyRemoteCacheManager extends RemoteCacheManager {
	
	public MyRemoteCacheManager( String  servers) {
		super(new ConfigurationBuilder().addServers(servers).forceReturnValues(true).build());
	   
	}

}
