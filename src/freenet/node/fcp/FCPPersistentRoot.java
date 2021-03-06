/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.fcp;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Constraint;
import com.db4o.query.Predicate;
import com.db4o.query.Query;

import freenet.node.NodeClientCore;
import freenet.support.Logger;

/**
 * Persistent root object for FCP.
 * @author toad
 */
// WARNING: THIS CLASS IS STORED IN DB4O -- THINK TWICE BEFORE ADD/REMOVE/RENAME FIELDS
public class FCPPersistentRoot {

	final long nodeDBHandle;
	final FCPClient globalForeverClient;
	
	public FCPPersistentRoot(long nodeDBHandle, ObjectContainer container) {
		this.nodeDBHandle = nodeDBHandle;
		globalForeverClient = new FCPClient("Global Queue", null, true, null, ClientRequest.PERSIST_FOREVER, this, container);
	}

	public static FCPPersistentRoot create(final long nodeDBHandle, ObjectContainer container) {
		ObjectSet<FCPPersistentRoot> set = container.query(new Predicate<FCPPersistentRoot>() {
			@Override
			public boolean match(FCPPersistentRoot root) {
				return root.nodeDBHandle == nodeDBHandle;
			}
		});
		System.err.println("Count of roots: "+set.size());
		if(set.hasNext()) {
			System.err.println("Loaded FCP persistent root.");
			FCPPersistentRoot root = set.next();
			container.activate(root, 2);
			root.globalForeverClient.init(container);
			return root;
		}
		FCPPersistentRoot root = new FCPPersistentRoot(nodeDBHandle, container);
		container.store(root);
		System.err.println("Created FCP persistent root.");
		return root;
	}

	public FCPClient registerForeverClient(final String name, NodeClientCore core, FCPConnectionHandler handler, FCPServer server, ObjectContainer container) {
		if(Logger.shouldLog(Logger.MINOR, this)) Logger.minor(this, "Registering forever-client for "+name);
		/**
		 * FIXME DB4O:
		 * Native queries involving strings seem to do wierd things. I was getting
		 * the global queue returned here even though I compared with the passed-in 
		 * name! :<
		 * FIXME reproduce and file a bug for db4o.
		 */
		Query query = container.query();
		query.constrain(FCPClient.class);
		// Don't constrain by root because that set is huge.
		// I think that was the cause of the OOMs here...
		Constraint con = query.descend("name").constrain(name);
		con.and(query.descend("root").constrain(this).identity());
		ObjectSet set = query.execute();
		while(set.hasNext()) {
			FCPClient client = (FCPClient) set.next();
			container.activate(client, 1);
			if(client.root != this) {
				container.deactivate(client, 1);
				continue;
			}
			client.setConnection(handler);
			if(!(name.equals(client.name)))
				Logger.error(this, "Returning "+client+" for "+name);
			if(Logger.shouldLog(Logger.MINOR, this)) Logger.minor(this, "Returning "+client+" for "+name);
			client.init(container);
			return client;
		}
		FCPClient client = new FCPClient(name, handler, false, null, ClientRequest.PERSIST_FOREVER, this, container);
		container.store(client);
		return client;
	}

	public void maybeUnregisterClient(FCPClient client, ObjectContainer container) {
		if(!client.hasPersistentRequests(container)) {
			client.removeFromDatabase(container);
		}
	}

}
