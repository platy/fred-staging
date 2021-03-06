package freenet.clients.http;

import java.util.ArrayList;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.DatabaseDisabledException;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ExpectedFileSizeEvent;
import freenet.client.events.ExpectedMIMEEvent;
import freenet.client.events.SendingToNetworkEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.api.Bucket;

/** 
 * Fetching a page for a browser.
 * 
 * LOCKING: The lock on this object is always taken last.
 */
public class FProxyFetchInProgress implements ClientEventListener, ClientGetCallback {
	
	private static volatile boolean logMINOR;
	
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback() {
			
			@Override
			public void shouldUpdate() {
				logMINOR = Logger.shouldLog(Logger.MINOR, this);
			}
		});
	}
	
	/** The key we are fetching */
	final FreenetURI uri;
	/** The maximum size specified by the client */
	final long maxSize;
	/** Unique ID for the fetch */
	private final long identifier;
	/** Fetcher */
	private final ClientGetter getter;
	/** Any request which is waiting for a progress screen or data.
	 * We may want to wake requests separately in future. */
	private final ArrayList<FProxyFetchWaiter> waiters;
	private final ArrayList<FProxyFetchResult> results;
	/** The data, if we have it */
	private Bucket data;
	/** Creation time */
	private final long timeStarted;
	/** Finished? */
	private boolean finished;
	/** Size, if known */
	private long size;
	/** MIME type, if known */
	private String mimeType;
	/** Gone to network? */
	private boolean goneToNetwork;
	/** Total blocks */
	private int totalBlocks;
	/** Required blocks */
	private int requiredBlocks;
	/** Fetched blocks */
	private int fetchedBlocks;
	/** Failed blocks */
	private int failedBlocks;
	/** Fatally failed blocks */
	private int fatallyFailedBlocks;
	private int fetchedBlocksPreNetwork;
	/** Finalized the block set? */
	private boolean finalizedBlocks;
	/** Fetch failed */
	private FetchException failed;
	private boolean hasWaited;
	private boolean hasNotifiedFailure;
	/** Last time the fetch was accessed from the fproxy end */
	private long lastTouched;
	final FProxyFetchTracker tracker;
	/** Show even non-fatal failures for 5 seconds. Necessary for javascript to work,
	 * because it fetches the page and then reloads it if it isn't a progress update. */
	private long timeFailed;
	
	public FProxyFetchInProgress(FProxyFetchTracker tracker, FreenetURI key, long maxSize2, long identifier, ClientContext context, FetchContext fctx, RequestClient rc) {
		this.tracker = tracker;
		this.uri = key;
		this.maxSize = maxSize2;
		this.timeStarted = System.currentTimeMillis();
		this.identifier = identifier;
		fctx = new FetchContext(fctx, FetchContext.IDENTICAL_MASK, false, null);
		fctx.maxOutputLength = fctx.maxTempLength = maxSize;
		fctx.eventProducer.addEventListener(this);
		waiters = new ArrayList<FProxyFetchWaiter>();
		results = new ArrayList<FProxyFetchResult>();
		getter = new ClientGetter(this, uri, fctx, FProxyToadlet.PRIORITY, rc, null, null);
	}
	
	public synchronized FProxyFetchWaiter getWaiter() {
		lastTouched = System.currentTimeMillis();
		FProxyFetchWaiter waiter = new FProxyFetchWaiter(this);
		waiters.add(waiter);
		return waiter;
	}

	synchronized FProxyFetchResult innerGetResult(boolean hasWaited) {
		lastTouched = System.currentTimeMillis();
		FProxyFetchResult res;
		if(data != null)
			res = new FProxyFetchResult(this, data, mimeType, timeStarted, goneToNetwork, getETA(), hasWaited);
		else {
			res = new FProxyFetchResult(this, mimeType, size, timeStarted, goneToNetwork,
					totalBlocks, requiredBlocks, fetchedBlocks, failedBlocks, fatallyFailedBlocks, finalizedBlocks, failed, getETA(), hasWaited);
		}
		results.add(res);
		return res;
	}

	public void start(ClientContext context) throws FetchException {
		try {
			context.start(getter);
		} catch (FetchException e) {
			synchronized(this) {
				this.failed = e;
				this.finished = true;
			}
		} catch (DatabaseDisabledException e) {
			// Impossible
			Logger.error(this, "Failed to start: "+e);
			synchronized(this) {
				this.failed = new FetchException(FetchException.INTERNAL_ERROR, e);
				this.finished = true;
			}
		}
	}

	public void onRemoveEventProducer(ObjectContainer container) {
		// Impossible
	}

	public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context) {
		if(ce instanceof SplitfileProgressEvent) {
			SplitfileProgressEvent split = (SplitfileProgressEvent) ce;
			synchronized(this) {
				int oldReq = requiredBlocks - (fetchedBlocks + failedBlocks + fatallyFailedBlocks);
				totalBlocks = split.totalBlocks;
				fetchedBlocks = split.succeedBlocks;
				requiredBlocks = split.minSuccessfulBlocks;
				failedBlocks = split.failedBlocks;
				fatallyFailedBlocks = split.fatallyFailedBlocks;
				finalizedBlocks = split.finalizedTotal;
				int req = requiredBlocks - (fetchedBlocks + failedBlocks + fatallyFailedBlocks);
				if(!(req > 1024 && oldReq <= 1024)) return;
			}
		} else if(ce instanceof SendingToNetworkEvent) {
			synchronized(this) {
				if(goneToNetwork) return;
				goneToNetwork = true;
				fetchedBlocksPreNetwork = fetchedBlocks;
			}
		} else if(ce instanceof ExpectedMIMEEvent) {
			synchronized(this) {
				this.mimeType = ((ExpectedMIMEEvent)ce).expectedMIMEType;
			}
			if(!goneToNetwork) return;
		} else if(ce instanceof ExpectedFileSizeEvent) {
			synchronized(this) {
				this.size = ((ExpectedFileSizeEvent)ce).expectedSize;
			}
			if(!goneToNetwork) return;
		} else return;
		wakeWaiters(false);
	}

	private void wakeWaiters(boolean finished) {
		FProxyFetchWaiter[] waiting;
		synchronized(this) {
			waiting = waiters.toArray(new FProxyFetchWaiter[waiters.size()]);
		}
		for(FProxyFetchWaiter w : waiting) {
			w.wakeUp(finished);
		}
	}

	public void onFailure(FetchException e, ClientGetter state, ObjectContainer container) {
		synchronized(this) {
			this.failed = e;
			this.finished = true;
			this.timeFailed = System.currentTimeMillis();
		}
		wakeWaiters(true);
	}

	public void onMajorProgress(ObjectContainer container) {
		// Ignore
	}

	public void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container) {
		synchronized(this) {
			this.data = result.asBucket();
			this.mimeType = result.getMimeType();
			this.finished = true;
		}
		wakeWaiters(true);
	}

	public boolean hasData() {
		return data != null;
	}

	public boolean finished() {
		return finished;
	}

	public void close(FProxyFetchWaiter waiter) {
		synchronized(this) {
			waiters.remove(waiter);
			if(!results.isEmpty()) return;
			if(!waiters.isEmpty()) return;
		}
		tracker.queueCancel(this);
	}

	/** Keep for 30 seconds after last access */
	static final int LIFETIME = 30 * 1000;
	
	/** Caller should take the lock on FProxyToadlet.fetchers, then call this 
	 * function, if it returns true then finish the cancel outside the lock.
	 */
	public synchronized boolean canCancel() {
		if(!waiters.isEmpty()) return false;
		if(!results.isEmpty()) return false;
		if(lastTouched + LIFETIME >= System.currentTimeMillis()) {
			if(logMINOR) Logger.minor(this, "Not able to cancel for "+this+" : "+uri+" : "+maxSize);
			return false;
		}
		if(logMINOR) Logger.minor(this, "Can cancel for "+this+" : "+uri+" : "+maxSize);
		return true;
	}
	
	public void finishCancel() {
		if(logMINOR) Logger.minor(this, "Finishing cancel for "+this+" : "+uri+" : "+maxSize);
		if(data != null) {
			try {
				data.free();
			} catch (Throwable t) {
				// Ensure we get to the next bit
				Logger.error(this, "Failed to free: "+t, t);
			}
		}
		try {
			getter.cancel();
		} catch (Throwable t) {
			// Ensure we get to the next bit
			Logger.error(this, "Failed to cancel: "+t, t);
		}
	}

	public void close(FProxyFetchResult result) {
		synchronized(this) {
			results.remove(result);
			if(!results.isEmpty()) return;
			if(!waiters.isEmpty()) return;
		}
		tracker.queueCancel(this);
	}
	
	public synchronized long getETA() {
		if(!goneToNetwork) return -1;
		if(requiredBlocks <= 0) return -1;
		if(fetchedBlocks >= requiredBlocks) return -1;
		if(fetchedBlocks - fetchedBlocksPreNetwork < 5) return -1;
		return ((System.currentTimeMillis() - timeStarted) * (requiredBlocks - fetchedBlocksPreNetwork)) / (fetchedBlocks - fetchedBlocksPreNetwork);
	}

	public synchronized boolean notFinishedOrFatallyFinished() {
		if(data == null && failed == null) return true;
		if(failed != null && failed.isFatal()) return true;
		if(failed != null && !hasNotifiedFailure) {
			hasNotifiedFailure = true;
			return true;
		}
		if(failed != null && System.currentTimeMillis() - timeFailed < 5000)
			return true;
		return false;
	}
	
	public synchronized boolean hasNotifiedFailure() {
		return true;
	}

	public synchronized boolean hasWaited() {
		return hasWaited;
	}

	public synchronized void setHasWaited() {
		hasWaited = true;
	}
}
