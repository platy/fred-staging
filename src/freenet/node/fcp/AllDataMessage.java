/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.fcp;

import com.db4o.ObjectContainer;

import freenet.node.Node;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * All the data, all in one big chunk. Obviously we must already have
 * all the data to send it. We do not want to have to block on a request,
 * especially as there may be errors.
 */
public class AllDataMessage extends DataCarryingMessage {

	final long dataLength;
	final boolean global;
	final String identifier;
	final long startupTime, completionTime;
	final String mimeType;
	
	public AllDataMessage(Bucket bucket, String identifier, boolean global, long startupTime, long completionTime, String mimeType) {
		this.bucket = bucket;
		this.dataLength = bucket.size();
		this.identifier = identifier;
		this.global = global;
		this.startupTime = startupTime;
		this.completionTime = completionTime;
		this.mimeType = mimeType;
	}

	@Override
	long dataLength() {
		return dataLength;
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fs = new SimpleFieldSet(true);
		fs.put("DataLength", dataLength);
		fs.putSingle("Identifier", identifier);
		if(global) fs.putSingle("Global", "true");
		fs.put("StartupTime", startupTime);
		fs.put("CompletionTime", completionTime);
		if(mimeType!=null) fs.putSingle("Metadata.ContentType", mimeType);
		return fs;
	}

	@Override
	public String getName() {
		return "AllData";
	}

	@Override
	public void run(FCPConnectionHandler handler, Node node) throws MessageInvalidException {
		throw new MessageInvalidException(ProtocolErrorMessage.INVALID_MESSAGE, "AllData goes from server to client not the other way around", identifier, global);
	}

	@Override
	String getIdentifier() {
		return identifier;
	}

	@Override
	boolean isGlobal() {
		return global;
	}

	@Override
	public void removeFrom(ObjectContainer container) {
		container.delete(this);
	}

}
