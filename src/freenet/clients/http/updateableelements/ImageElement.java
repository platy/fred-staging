package freenet.clients.http.updateableelements;

import java.util.Map;
import java.util.Random;

import freenet.client.FetchException;
import freenet.clients.http.FProxyFetchInProgress;
import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyFetchWaiter;
import freenet.clients.http.SimpleToadletServer;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.filter.HTMLFilter.ParsedTag;
import freenet.keys.FreenetURI;
import freenet.support.Base64;

public class ImageElement extends BaseUpdateableElement {

	/** The tracker that the Fetcher can be acquired */
	public FProxyFetchTracker		tracker;
	/** The URI of the download this progress bar shows */
	public FreenetURI				key;
	/** The maxSize */
	public long						maxSize;
	/** The FetchListener that gets notified when the download progresses */
	private NotifierFetchListener	fetchListener;

	private ParsedTag				originalImg;

	private int						randomNumber	= new Random().nextInt();

	public ImageElement(FProxyFetchTracker tracker, FreenetURI key, long maxSize, ToadletContext ctx, ParsedTag originalImg) throws FetchException {
		super("span", ctx);
		long now = System.currentTimeMillis();
		System.err.println("ImageElement creating for uri:" + key);
		this.originalImg = originalImg;
		this.tracker = tracker;
		this.key = key;
		this.maxSize = maxSize;
		init();
		// Creates and registers the FetchListener
		fetchListener = new NotifierFetchListener(((SimpleToadletServer) ctx.getContainer()).pushDataManager, this);
		FProxyFetchWaiter waiter=tracker.makeFetcher(key, maxSize);
		tracker.getFetchInProgress(key, maxSize).addListener(fetchListener);
		tracker.getFetchInProgress(key, maxSize).close(waiter);
		System.err.println("ImageElement creating finished in:" + (System.currentTimeMillis() - now) + " ms");
	}

	@Override
	public void dispose() {
		System.err.println("Disposing ImageElement");
		// Deregisters the FetchListener
		FProxyFetchInProgress progress = tracker.getFetchInProgress(key, maxSize);
		if (progress != null) {
			progress.removeListener(fetchListener);
			System.err.println("canCancel():"+progress.canCancel());
			progress.requestImmediateCancel();
			if (progress.canCancel()) {
				tracker.run();
			}
		}
	}

	@Override
	public String getUpdaterId(String requestId) {
		return getId(key, randomNumber);
	}

	public static String getId(FreenetURI uri, int randomNumber) {
		return Base64.encodeStandard(("image[URI:" + uri.toString() + ",random:" + randomNumber + "]").getBytes());
	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.PROGRESSBAR_UPDATER;
	}

	@Override
	public void updateState() {
		System.err.println("Updating ImageElement for url:" + key);
		children.clear();
		FProxyFetchResult fr = null;
		FProxyFetchWaiter waiter = null;
		try {
			try {
				waiter = tracker.makeFetcher(key, maxSize);
				fr = waiter.getResult();
			} catch (FetchException fe) {
				addChild("div", "error");
				fe.printStackTrace();
			}
			if (fr == null) {
				addChild("div", "No fetcher found");
			} else {

				if (fr.isFinished() && fr.hasData()) {
					System.err.println("ImageElement is completed");
					setContent(originalImg.toString());
				} else if (fr.failed != null) {
					System.err.println("ImageElement is errorous");
					Map<String, String> attr = originalImg.getAttributesAsMap();
					attr.put("src", "/static/error.png");
					setContent(new ParsedTag(originalImg, attr).toString());
					fr.failed.printStackTrace();
				} else {
					System.err.println("ImageElement is still in progress");
					int total = fr.requiredBlocks;
					int fetchedPercent = (int) (fr.fetchedBlocks / (double) total * 100);
					Map<String, String> attr = originalImg.getAttributesAsMap();
					attr.put("src", "/imagecreator/?text=" + fetchedPercent + "%25");
					setContent(new ParsedTag(originalImg, attr).toString());
				}
			}
		} finally {
			if(waiter!=null){
				tracker.getFetchInProgress(key, maxSize).close(waiter);
			}
			if(fr!=null){
				tracker.getFetchInProgress(key, maxSize).close(fr);
			}
		}
	}

}
