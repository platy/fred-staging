package freenet.client.async;

import java.util.HashMap;
import java.util.Iterator;

import freenet.client.InsertContext;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;

/**
 * plain/dumb manifest putter: every file item is a redirect (no containers at all)
 */

public class PlainManifestPutter extends BaseManifestPutter {
	
	private static volatile boolean logDEBUG;
	
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback() {
			
			@Override
			public void shouldUpdate() {
				logDEBUG = Logger.shouldLog(Logger.DEBUG, this);
			}
		});
	}

	public PlainManifestPutter(ClientPutCallback clientCallback, HashMap<String, Object> manifestElements, short prioClass, FreenetURI target, String defaultName, InsertContext ctx, boolean getCHKOnly,
			RequestClient clientContext, boolean earlyEncode) {
		super(clientCallback, manifestElements, prioClass, target, defaultName, ctx, getCHKOnly, clientContext, earlyEncode);
	}

	@Override
	protected void makePutHandlers(HashMap<String,Object> manifestElements, HashMap<String, Object> putHandlersByName) {
		makePutHandlers(manifestElements, putHandlersByName, "/");
	}
		
	private void makePutHandlers(HashMap<String,Object> manifestElements, HashMap<String,Object> putHandlersByName, String prefix) {
		Iterator<String> it = manifestElements.keySet().iterator();
		while(it.hasNext()) {
			String name = it.next();
			Object o = manifestElements.get(name);
			if(o instanceof HashMap) {
				HashMap<String,Object> subMap = new HashMap<String,Object>();
				putHandlersByName.put(name, subMap);
				makePutHandlers((HashMap<String,Object>)o, subMap, prefix+name+ '/');
				if(logDEBUG)
					Logger.debug(this, "Sub map for "+name+" : "+subMap.size()+" elements from "+((HashMap)o).size());
			} else {
				ManifestElement element = (ManifestElement) o;
				addRedirect(name, element, putHandlersByName);
			}
		}
	}
}

