/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client;

import java.util.HashMap;
import java.util.Set;

import freenet.client.async.ClientCallback;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.ClientPutter;
import freenet.client.events.ClientEventListener;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;

public interface HighLevelSimpleClient {

	/**
	 * Set the maximum length of the fetched data.
	 */
	public void setMaxLength(long maxLength);
	
	/**
	 * Set the maximum length of any intermediate data, e.g. ZIP manifests.
	 */
	public void setMaxIntermediateLength(long maxIntermediateLength);

	/**
	 * Blocking fetch of a URI
	 * @throws FetchException If there is an error fetching the data
	 */
	public FetchResult fetch(FreenetURI uri) throws FetchException;

	/**
	 * Blocking fetch of a URI with a configurable max-size.
	 */
	public FetchResult fetch(FreenetURI uri, long maxSize) throws FetchException;
	
	/**
	 * Blocking fetch of a URI with a configurable max-size and context object.
	 */
	public FetchResult fetch(FreenetURI uri, long maxSize, RequestClient context) throws FetchException;
	
	/**
	 * Non-blocking fetch of a URI with a configurable max-size (in bytes), context object, callback and context.
	 */
	public ClientGetter fetch(FreenetURI uri, long maxSize, RequestClient context, ClientGetCallback callback, FetchContext fctx) throws FetchException;
	
	@Deprecated
	public ClientGetter fetch(FreenetURI uri, long maxSize, RequestClient context, ClientCallback callback, FetchContext fctx) throws FetchException;
	
	/**
	 * Blocking insert.
	 * @param filenameHint If set, insert a single-file manifest containing only this file, under the given filename.
	 * @throws InsertException If there is an error inserting the data
	 */
	public FreenetURI insert(InsertBlock insert, boolean getCHKOnly, String filenameHint) throws InsertException;

	/**
	 * Non-blocking insert.
	 */
	public ClientPutter insert(InsertBlock insert, boolean getCHKOnly, String filenameHint, boolean isMetadata, InsertContext ctx, ClientPutCallback cb) throws InsertException;

	@Deprecated
	public ClientPutter insert(InsertBlock insert, boolean getCHKOnly, String filenameHint, boolean isMetadata, InsertContext ctx, ClientCallback cb) throws InsertException;
	
	/**
	 * Blocking insert of a redirect.
	 */
	public FreenetURI insertRedirect(FreenetURI insertURI, FreenetURI target) throws InsertException;
	
	/**
	 * Blocking insert of multiple files as a manifest (or zip manifest, etc).
	 */
	public FreenetURI insertManifest(FreenetURI insertURI, HashMap bucketsByName, String defaultName) throws InsertException;
	
	public FetchContext getFetchContext();
	public FetchContext getFetchContext(long size);

	/**
	 * Get an InsertContext.
	 * @param forceNonPersistent If true, force the request to use the non-persistent
	 * bucket pool.
	 */
	public InsertContext getInsertContext(boolean forceNonPersistent);
	
	/**
     * Add a ClientEventListener.
     * This name is misleading -- this hook not for the global queue.
     * Use {@link NodeClientCore#getFCPServer()} for the global queue.
     * 
     * @deprecated Use {@link #addEventHook(ClientEventListener)} instead
     */
    public void addGlobalHook(ClientEventListener listener);

	/**
	 * Add a ClientEventListener.
	 */
	public void addEventHook(ClientEventListener listener);

	/**
	 * Generates a new key pair, consisting of the insert URI at index 0 and the
	 * request URI at index 1.
	 * 
	 * @param docName
	 *            The document name
	 * @return An array containing the insert and request URI
	 */
	public FreenetURI[] generateKeyPair(String docName);

	/**
	 * Prefetch a key at a very low priority. If it hasn't been fetched within the timeout, 
	 * kill the fetch. 
	 * @param uri
	 * @param timeout
	 */
	public void prefetch(FreenetURI uri, long timeout, long maxSize, Set allowedTypes);

	public void prefetch(FreenetURI uri, long timeout, long maxSize, Set allowedTypes, short prio);
	
}
