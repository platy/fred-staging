package freenet.client.async;

import java.util.ArrayList;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

import freenet.keys.Key;
import freenet.keys.KeyBlock;
import freenet.keys.NodeSSK;
import freenet.node.Node;
import freenet.node.PrioRunnable;
import freenet.node.RequestStarter;
import freenet.node.SendableGet;
import freenet.support.Executor;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.io.NativeThread;

/**
 * @author Matthew Toseland <toad@amphibian.dyndns.org> (0xE43DA450)
 */
public class DatastoreChecker implements PrioRunnable {
	
	private static volatile boolean logMINOR;
	
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback() {
			
			@Override
			public void shouldUpdate() {
				logMINOR = Logger.shouldLog(Logger.MINOR, this);
			}
		});
	}
	
	static final int MAX_PERSISTENT_KEYS = 1024;
	
	/** List of arrays of keys to check for persistent requests. PARTIAL: 
	 * When we run out we will look up some more DatastoreCheckerItem's. */
	private final ArrayList<Key[]>[] persistentKeys;
	/** List of persistent requests which we will call finishRegister() for 
	 * when we have checked the keys lists. PARTIAL: When we run out we 
	 * will look up some more DatastoreCheckerItem's. Deactivated. */
	private final ArrayList<SendableGet>[] persistentGetters;
	private final ArrayList<Boolean>[] persistentDontCache;
	private final ArrayList<ClientRequestScheduler>[] persistentSchedulers;
	private final ArrayList<DatastoreCheckerItem>[] persistentCheckerItems;
	private final ArrayList<BlockSet>[] persistentBlockSets;
	
	/** List of arrays of keys to check for transient requests. */
	private final ArrayList<Key[]>[] transientKeys;
	/** List of transient requests which we will call finishRegister() for
	 * when we have checked the keys lists. */
	private final ArrayList<SendableGet>[] transientGetters;
	private final ArrayList<BlockSet>[] transientBlockSets;
	
	private ClientContext context;
	private final Node node;
	
	public synchronized void setContext(ClientContext context) {
		this.context = context;
	}

	@SuppressWarnings("unchecked")
    public DatastoreChecker(Node node) {
		this.node = node;
		int priorities = RequestStarter.NUMBER_OF_PRIORITY_CLASSES;
		persistentKeys = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			persistentKeys[i] = new ArrayList<Key[]>();
		persistentGetters = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			persistentGetters[i] = new ArrayList<SendableGet>();
		persistentDontCache = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			persistentDontCache[i] = new ArrayList<Boolean>();
		persistentSchedulers = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			persistentSchedulers[i] = new ArrayList<ClientRequestScheduler>();
		persistentCheckerItems = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			persistentCheckerItems[i] = new ArrayList<DatastoreCheckerItem>();
		persistentBlockSets = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			persistentBlockSets[i] = new ArrayList<BlockSet>();
		transientKeys = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			transientKeys[i] = new ArrayList<Key[]>();
		transientGetters = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			transientGetters[i] = new ArrayList<SendableGet>();
		transientBlockSets = new ArrayList[priorities];
		for(int i=0;i<priorities;i++)
			transientBlockSets[i] = new ArrayList<BlockSet>();
	}
	
	private final DBJob loader =  new DBJob() {

		public boolean run(ObjectContainer container, ClientContext context) {
			loadPersistentRequests(container, context);
			return false;
		}
		
	};
	
    public void loadPersistentRequests(ObjectContainer container, final ClientContext context) {
		int totalSize = 0;
		synchronized(this) {
			for(int i=0;i<persistentKeys.length;i++) {
				for(int j=0;j<persistentKeys[i].size();j++)
					totalSize += persistentKeys[i].get(j).length;
			}
			if(totalSize > MAX_PERSISTENT_KEYS) {
				if(logMINOR) Logger.minor(this, "Persistent datastore checker queue already full");
				return;
			}
		}
		for(short p = RequestStarter.MAXIMUM_PRIORITY_CLASS; p <= RequestStarter.MINIMUM_PRIORITY_CLASS; p++) {
			final short prio = p;
			Query query = container.query();
			query.constrain(DatastoreCheckerItem.class);
			query.descend("nodeDBHandle").constrain(context.nodeDBHandle).
				and(query.descend("prio").constrain(prio));
			@SuppressWarnings("unchecked")
			ObjectSet<DatastoreCheckerItem> results = query.execute();
			for(DatastoreCheckerItem item : results) {
				if(item.chosenBy == context.bootID) continue;
				SendableGet getter = item.getter;
				if(getter == null || !container.ext().isStored(getter)) {
					if(logMINOR) Logger.minor(this, "Ignoring DatastoreCheckerItem because the SendableGet has already been deleted from the database");
					container.delete(item);
					continue;
				}
				BlockSet blocks = item.blocks;
				container.activate(getter, 1);
				boolean dontCache = getter.dontCache(container);
				ClientRequestScheduler sched = getter.getScheduler(context);
				synchronized(this) {
					if(persistentGetters[prio].contains(getter)) continue;
				}
				Key[] keys = getter.listKeys(container);
				// FIXME check the store bloom filter using store.probablyInStore().
				item.chosenBy = context.bootID;
				container.store(item);
				synchronized(this) {
					if(persistentGetters[prio].contains(getter)) continue;
					ArrayList<Key> finalKeysToCheck = new ArrayList<Key>();
					for(Key key : keys) {
						key = key.cloneKey();
						finalKeysToCheck.add(key);
					}
					Key[] finalKeys =
						finalKeysToCheck.toArray(new Key[finalKeysToCheck.size()]);
					persistentKeys[prio].add(finalKeys);
					persistentGetters[prio].add(getter);
					persistentDontCache[prio].add(dontCache);
					persistentSchedulers[prio].add(sched);
					persistentCheckerItems[prio].add(item);
					persistentBlockSets[prio].add(blocks);
					if(totalSize == 0)
						notifyAll();
					totalSize += finalKeys.length;
					if(totalSize > MAX_PERSISTENT_KEYS) {
						boolean full = trimPersistentQueue(prio, container);
						notifyAll();
						if(full) return;
					} else {
						notifyAll();
					}
				}
				container.deactivate(getter, 1);
			}
		}
	}
	
	/**
	 * Trim the queue of persistent requests until it is just over the limit.
	 * @param minPrio Only drop from priorities lower than this one.
	 * @return True unless the queue is under the limit.
	 */
	private boolean trimPersistentQueue(short prio, ObjectContainer container) {
		synchronized(this) {
			int preQueueSize = 0;
			for(int i=0;i<prio;i++) {
				for(int x=0;x<persistentKeys[i].size();x++)
					preQueueSize += persistentKeys[i].get(x).length;
			}
			if(preQueueSize > MAX_PERSISTENT_KEYS) {
				// Dump everything
				for(int i=prio+1;i<persistentKeys.length;i++) {
					for(DatastoreCheckerItem item : persistentCheckerItems[i]) {
						item.chosenBy = 0;
						container.store(item);
					}
					persistentSchedulers[i].clear();
					persistentDontCache[i].clear();
					persistentGetters[i].clear();
					persistentKeys[i].clear();
					persistentBlockSets[i].clear();
				}
				return true;
			} else {
				int postQueueSize = 0;
				for(int i=prio+1;i<persistentKeys.length;i++) {
					for(int x=0;x<persistentKeys[i].size();x++)
						postQueueSize += persistentKeys[i].get(x).length;
				}
				if(postQueueSize + preQueueSize < MAX_PERSISTENT_KEYS)
					return false;
				// Need to dump some stuff.
				for(int i=persistentKeys.length-1;i>prio;i--) {
					while(!persistentKeys[i].isEmpty()) {
						int idx = persistentKeys[i].size() - 1;
						DatastoreCheckerItem item = persistentCheckerItems[i].remove(idx);
						persistentSchedulers[i].remove(idx);
						persistentDontCache[i].remove(idx);
						persistentGetters[i].remove(idx);
						Key[] keys = persistentKeys[i].remove(idx);
						persistentBlockSets[i].remove(idx);
						item.chosenBy = 0;
						container.store(item);
						if(postQueueSize + preQueueSize - keys.length < MAX_PERSISTENT_KEYS) {
							return false;
						} else postQueueSize -= keys.length;
					}
				}
				// Still over the limit.
				return true;
			}
		}
	}
	
	public void queueTransientRequest(SendableGet getter, BlockSet blocks) {
		if(logMINOR) Logger.minor(this, "Queueing transient request "+getter);
		Key[] checkKeys = getter.listKeys(null);
		short prio = getter.getPriorityClass(null);
		// FIXME check using store.probablyInStore
		ArrayList<Key> finalKeysToCheck = new ArrayList<Key>();
		synchronized(this) {
			for(Key key : checkKeys) {
				finalKeysToCheck.add(key);
			}
			transientGetters[prio].add(getter);
			transientKeys[prio].add(finalKeysToCheck.toArray(new Key[finalKeysToCheck.size()]));
			transientBlockSets[prio].add(blocks);
			notifyAll();
		}
	}
	
	/**
	 * Queue a persistent request. We will store a DatastoreCheckerItem, then 
	 * check the datastore (on the datastore checker thread), and then call 
	 * finishRegister() (on the database thread). Caller must have already 
	 * stored and registered the HasKeyListener if any.
	 * @param getter
	 */
	public void queuePersistentRequest(SendableGet getter, BlockSet blocks, ObjectContainer container) {
		Key[] checkKeys = getter.listKeys(container);
		short prio = getter.getPriorityClass(container);
		boolean dontCache = getter.dontCache(container);
		ClientRequestScheduler sched = getter.getScheduler(context);
		DatastoreCheckerItem item = new DatastoreCheckerItem(getter, context.nodeDBHandle, prio, blocks);
		container.store(item);
		container.activate(blocks, 5);
		synchronized(this) {
			// FIXME only add if queue not full.
			int queueSize = 0;
			// Only count queued keys at no higher priority than this request.
			for(short p = 0;p<=prio;p++) {
				for(int x = 0;x<persistentKeys[p].size();x++) {
					queueSize += persistentKeys[p].get(x).length;
				}
			}
			if(queueSize > MAX_PERSISTENT_KEYS) return;
			item.chosenBy = context.bootID;
			container.store(item);
			// FIXME check using store.probablyInStore
			ArrayList<Key> finalKeysToCheck = new ArrayList<Key>();
			for(Key key : checkKeys) {
				finalKeysToCheck.add(key);
			}
			persistentGetters[prio].add(getter);
			persistentKeys[prio].add(finalKeysToCheck.toArray(new Key[finalKeysToCheck.size()]));
			persistentDontCache[prio].add(dontCache);
			persistentSchedulers[prio].add(sched);
			persistentCheckerItems[prio].add(item);
			persistentBlockSets[prio].add(blocks);
			trimPersistentQueue(prio, container);
			notifyAll();
		}
	}

	public void run() {
		while(true) {
			try {
				realRun();
			} catch (Throwable t) {
				Logger.error(this, "Caught "+t+" in datastore checker thread", t);
			}
		}
	}

	private void realRun() {
		Key[] keys = null;
		SendableGet getter = null;
		boolean persistent = false;
		boolean dontCache = false;
		ClientRequestScheduler sched = null;
		DatastoreCheckerItem item = null;
		BlockSet blocks = null;
		// If the queue is too large, don't check any more blocks. It is possible
		// that we can check the datastore faster than we can handle the resulting
		// blocks, this will cause OOM.
		int queueSize = context.jobRunner.getQueueSize(ClientRequestScheduler.TRIP_PENDING_PRIORITY);
		if(queueSize > 500) {
			// If the queue is over 500, don't run the datastore checker at all.
			// It's entirely possible that looking up blocks in the store will
			// make the situation first, because a key which is queued for a
			// non-persistent request may also be used by a persistent one.
			
			// FIXME consider setting a flag to not only only check transient
			// requests, but also check whether the keys are in the persistent
			// bloom filters first, and if they are not check them.
			try {
				Thread.sleep(10*1000);
			} catch (InterruptedException e) {
				// Ignore
			}
			return;
		}
		// If it's over 100, don't check blocks from persistent requests.
		boolean notPersistent = queueSize > 100;
		synchronized(this) {
			while(true) {
				for(short prio = 0;prio<transientKeys.length;prio++) {
					if(!transientKeys[prio].isEmpty()) {
						keys = transientKeys[prio].remove(0);
						getter = transientGetters[prio].remove(0);
						persistent = false;
						item = null;
						blocks = transientBlockSets[prio].remove(0);
						if(logMINOR)
							Logger.minor(this, "Checking transient request "+getter+" prio "+prio);
						break;
					} else if((!notPersistent) && (!persistentGetters[prio].isEmpty())) {
						keys = persistentKeys[prio].remove(0);
						getter = persistentGetters[prio].remove(0);
						persistent = true;
						dontCache = persistentDontCache[prio].remove(0);
						sched = persistentSchedulers[prio].remove(0);
						item = persistentCheckerItems[prio].remove(0);
						blocks = persistentBlockSets[prio].remove(0);
						break;
					}
				}
				if(keys == null) {
					if(logMINOR) Logger.minor(this, "Waiting for more persistent requests");
					try {
						context.jobRunner.queue(loader, NativeThread.HIGH_PRIORITY, true);
					} catch (DatabaseDisabledException e1) {
						// Ignore
					}
					try {
						wait(100*1000);
					} catch (InterruptedException e) {
						// Ok
					}
					continue;
				}
				break;
			}
		}
		if(!persistent) {
			dontCache = getter.dontCache(null);
			sched = getter.getScheduler(context);
		}
		boolean anyValid = false;
		for(Key key : keys) {
			KeyBlock block = null;
			if(blocks != null)
				block = blocks.get(key);
			if(blocks == null)
				block = node.fetch(key, dontCache, true, true, false, false);
			if(block != null) {
				if(logMINOR) Logger.minor(this, "Found key");
				if(key instanceof NodeSSK)
					sched.tripPendingKey(block);
				else // CHK
					sched.tripPendingKey(block);
			} else {
				anyValid = true;
			}
//			synchronized(this) {
//				keysToCheck[priority].remove(key);
//			}
		}
		if(persistent)
			try {
				context.jobRunner.queue(loader, NativeThread.HIGH_PRIORITY, true);
			} catch (DatabaseDisabledException e) {
				// Ignore
			}
		if(persistent) {
			final SendableGet get = getter;
			final ClientRequestScheduler scheduler = sched;
			final boolean valid = anyValid;
			final DatastoreCheckerItem it = item;
			try {
				context.jobRunner.queue(new DBJob() {

					public boolean run(ObjectContainer container, ClientContext context) {
						if(container.ext().isActive(get)) {
							Logger.error(this, "ALREADY ACTIVATED: "+get);
						}
						if(!container.ext().isStored(get)) {
							// Completed and deleted already.
							if(logMINOR) 
								Logger.minor(this, "Already deleted from database");
							container.delete(it);
							return false;
						}
						container.activate(get, 1);
						scheduler.finishRegister(new SendableGet[] { get }, true, container, valid, it);
						container.deactivate(get, 1);
						loader.run(container, context);
						return false;
					}
					
				}, NativeThread.NORM_PRIORITY, false);
			} catch (DatabaseDisabledException e) {
				// Impossible
			}
		} else {
			sched.finishRegister(new SendableGet[] { getter }, false, null, anyValid, item);
		}
	}
	
	synchronized void wakeUp() {
		notifyAll();
	}

	public void start(Executor executor, String name) {
		try {
			context.jobRunner.queue(loader, NativeThread.HIGH_PRIORITY-1, true);
		} catch (DatabaseDisabledException e) {
			// Ignore
		}
		executor.execute(this, name);
	}

	public int getPriority() {
		return NativeThread.NORM_PRIORITY;
	}
	
	public boolean objectCanNew(ObjectContainer container) {
		Logger.error(this, "Not storing DatastoreChecker in database", new Exception("error"));
		return false;
	}
	
}
