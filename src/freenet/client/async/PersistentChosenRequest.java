/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client.async;

import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.node.BulkCallFailureItem;
import freenet.node.LowLevelGetException;
import freenet.node.RequestScheduler;
import freenet.node.SendableGet;
import freenet.node.SendableInsert;
import freenet.node.SendableRequest;
import freenet.node.SendableRequestSender;
import freenet.node.SupportsBulkCallFailure;
import freenet.support.Logger;
import freenet.support.io.NativeThread;

/**
 * A persistent SendableRequest chosen by ClientRequestScheduler. In order to minimize database I/O 
 * (and hence disk I/O and object churn), we select the entire SendableRequest, including all blocks 
 * on it. We keep it in RAM, until all blocks have succeeded/failed. Then we call all relevant 
 * callbacks in a single transaction.
 * @author toad
 */
public class PersistentChosenRequest {

	/** The request object */
	public transient final SendableRequest request;
	/** Priority when we selected it */
	public transient final short prio;
	/** Retry count when we selected it */
	public transient final int retryCount;
	public transient final boolean localRequestOnly;
	public transient final boolean cacheLocalRequests;
	public transient final boolean ignoreStore;
	public transient final int size;
	public transient final ArrayList<PersistentChosenBlock> blocksNotStarted;
	public transient final ArrayList<PersistentChosenBlock> blocksStarted;
	public transient final ArrayList<PersistentChosenBlock> blocksFinished;
	public final RequestScheduler scheduler;
	public final SendableRequestSender sender;
	private boolean logMINOR;
	
	PersistentChosenRequest(SendableRequest req, short prio, int retryCount, ObjectContainer container, RequestScheduler sched, ClientContext context) {
		request = req;
		this.prio = prio;
		this.retryCount = retryCount;
		if(req instanceof SendableGet) {
			SendableGet sg = (SendableGet) req;
			FetchContext ctx = sg.getContext();
			if(container != null)
				container.activate(ctx, 1);
			localRequestOnly = ctx.localRequestOnly;
			cacheLocalRequests = ctx.cacheLocalRequests;
			ignoreStore = ctx.ignoreStore;
		} else {
			localRequestOnly = false;
			cacheLocalRequests = false;
			ignoreStore = false;
		}
		blocksNotStarted = new ArrayList<PersistentChosenBlock>();
		blocksStarted = new ArrayList<PersistentChosenBlock>();
		blocksFinished = new ArrayList<PersistentChosenBlock>();
		this.scheduler = sched;
		// Fill up blocksNotStarted
		boolean reqActive = container.ext().isActive(req);
		if(!reqActive)
			container.activate(req, 1);
		blocksNotStarted.addAll(req.makeBlocks(this, sched, container, context));
		sender = req.getSender(container, context);
		if(!reqActive)
			container.deactivate(req, 1);
		size = blocksNotStarted.size();
	}

	void onFinished(PersistentChosenBlock block, ClientContext context) {
		logMINOR = Logger.shouldLog(Logger.MINOR, this);
		if(logMINOR)
			Logger.minor(this, "onFinished() on "+this+" for "+block);
		synchronized(this) {
			// Remove by pointer
			for(int i=0;i<blocksNotStarted.size();i++) {
				if(blocksNotStarted.get(i) == block) {
					blocksNotStarted.remove(i);
					Logger.error(this, "Block finished but was in blocksNotStarted: "+block+" for "+this, new Exception("error"));
					i--;
				}
			}
			for(int i=0;i<blocksStarted.size();i++) {
				if(blocksStarted.get(i) == block) {
					blocksStarted.remove(i);
					i--;
				}
			}
			for(PersistentChosenBlock cmp : blocksFinished)
				if(cmp == block) {
					Logger.error(this, "Block already in blocksFinished: "+block+" for "+this);
					return;
				}
			blocksFinished.add(block);
			if(blocksFinished.size() < size) {
				if(logMINOR)
					Logger.minor(this, "Blocks finished: "+blocksFinished.size()+" of "+size+" on "+this+" for "+request);
				return;
			}
		}
		// All finished.
		context.jobRunner.queue(new DBJob() {

			public void run(ObjectContainer container, ClientContext context) {
				finish(container, context);
			}
			
		}, NativeThread.NORM_PRIORITY + 1, false);
	}

	private void finish(ObjectContainer container, ClientContext context) {
		Logger.error(this, "Finishing "+this+" for "+request);
		// Call all the callbacks.
		if(request instanceof SendableGet) {
			boolean supportsBulk = request instanceof SupportsBulkCallFailure;
			Vector<BulkCallFailureItem> bulkFailItems = null;
			for(PersistentChosenBlock block : blocksFinished) {
				if(!block.fetchSucceeded()) {
					LowLevelGetException e = block.failedGet();
					if(supportsBulk) {
						if(bulkFailItems == null)
							bulkFailItems = new Vector<BulkCallFailureItem>();
						bulkFailItems.add(new BulkCallFailureItem(e, block.token));
					} else {
						container.activate(request, 1);
						((SendableGet)request).onFailure(e, block.token, container, context);
						container.commit(); // db4o is read-committed, so we need to commit here.
					}
				}
			}
			if(bulkFailItems != null) {
				container.activate(request, 1);
				((SupportsBulkCallFailure)request).onFailure(bulkFailItems.toArray(new BulkCallFailureItem[bulkFailItems.size()]), container, context);
				container.commit(); // db4o is read-committed, so we need to commit here.
			}
		} else /*if(request instanceof SendableInsert)*/ {
			for(PersistentChosenBlock block : blocksFinished) {
				container.activate(request, 1);
				if(block.insertSucceeded()) {
					((SendableInsert)request).onSuccess(block.token, container, context);
					container.commit(); // db4o is read-committed, so we need to commit here.
				} else {
					((SendableInsert)request).onFailure(block.failedPut(), block.token, container, context);
					container.commit(); // db4o is read-committed, so we need to commit here.
				}
			}
		}
		scheduler.removeRunningRequest(request);
	}

	public synchronized ChosenBlock grabNotStarted(Random random) {
		int size = blocksNotStarted.size();
		if(size == 0) return null;
		if(size == 1) return blocksNotStarted.remove(0);
		return blocksNotStarted.remove(random.nextInt(size));
	}

	public synchronized int sizeNotStarted() {
		return blocksNotStarted.size();
	}

	public void onDumped(ClientRequestSchedulerCore core, ObjectContainer container) {
		ArrayList<PersistentChosenBlock> oldNotStarted;
		boolean wasStarted;
		synchronized(this) {
			oldNotStarted = (ArrayList<PersistentChosenBlock>) blocksNotStarted.clone();
			blocksNotStarted.clear();
			wasStarted = !blocksStarted.isEmpty();
		}
		for(PersistentChosenBlock block : oldNotStarted) {
			block.removeFromFetching(core);
		}
		if(!wasStarted) {
			if(logMINOR) Logger.minor(this, "Finishing immediately in onDumped() as nothing pending: "+this);
			finish(container, core.sched.clientContext);
		}
	}
}