package freenet.client;

import com.db4o.ObjectContainer;

import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientPutCallback;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

public class PutWaiter implements ClientPutCallback {

	private boolean finished;
	private boolean succeeded;
	private FreenetURI uri;
	private InsertException error;

	public synchronized void onSuccess(BaseClientPutter state, ObjectContainer container) {
		succeeded = true;
		finished = true;
		notifyAll();
	}

	public synchronized void onFailure(InsertException e, BaseClientPutter state, ObjectContainer container) {
		error = e;
		finished = true;
		notifyAll();
	}

	public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state, ObjectContainer container) {
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "URI: "+uri);
		if(this.uri == null)
			this.uri = uri;
		if(uri.equals(this.uri)) return;
		Logger.error(this, "URI already set: "+this.uri+" but new URI: "+uri, new Exception("error"));
	}

	public synchronized FreenetURI waitForCompletion() throws InsertException {
		while(!finished) {
			try {
				wait();
			} catch (InterruptedException e) {
				// Ignore
			}
		}
		if(error != null) {
			error.uri = uri;
			throw error;
		}
		if(succeeded) return uri;
		Logger.error(this, "Did not succeed but no error");
		throw new InsertException(InsertException.INTERNAL_ERROR, "Did not succeed but no error", uri);
	}

	public void onMajorProgress(ObjectContainer container) {
		// Ignore
	}

	public void onFetchable(BaseClientPutter state, ObjectContainer container) {
		// Ignore
	}

}
