package freenet.support;

import java.util.HashSet;

import freenet.crypt.RandomSource;

/**
 * An array which supports very fast remove-and-return-a-random-element.
 */
public class RandomGrabArray {

	/** Array of items. Non-null's followed by null's. */
	private RandomGrabArrayItem[] reqs;
	/** Index of first null item. */
	private int index;
	/** Random source */
	private RandomSource rand;
	/** What do we already have? FIXME: Replace with a Bloom filter or something (to save 
	 * RAM), or rewrite the whole class as a custom hashset maybe based on the classpath 
	 * HashSet. Note that removeRandom() is *the* common operation, so MUST BE FAST.
	 */
	private HashSet contents;
	private final static int MIN_SIZE = 32;

	public RandomGrabArray(RandomSource rand) {
		this.reqs = new RandomGrabArrayItem[MIN_SIZE];
		index = 0;
		this.rand = rand;
		contents = new HashSet();
	}
	
	public void add(RandomGrabArrayItem req) {
		if(req.isCancelled()) {
			if(Logger.shouldLog(Logger.MINOR, this))
				Logger.minor(this, "Is finished already: "+req);
			return;
		}
		req.setParentGrabArray(this);
		synchronized(this) {
			if(contents.contains(req)) return;
			contents.add(req);
			if(index >= reqs.length) {
				RandomGrabArrayItem[] r = new RandomGrabArrayItem[reqs.length*2];
				System.arraycopy(reqs, 0, r, 0, reqs.length);
				reqs = r;
			}
			reqs[index++] = req;
		}
	}
	
	public RandomGrabArrayItem removeRandom() {
		RandomGrabArrayItem ret;
		synchronized(this) {
		boolean logMINOR = Logger.shouldLog(Logger.MINOR, this);
		while(true) {
			if(index == 0) {
				if(logMINOR) Logger.minor(this, "All null on "+this);
				return null;
			}
			int i = rand.nextInt(index);
			ret = reqs[i];
			if(ret != null && !ret.canRemove()) return ret;
			reqs[i] = reqs[--index];
			reqs[index] = null;
			if(ret != null)
				contents.remove(ret);
			// Shrink array
			if((index < reqs.length / 4) && (reqs.length > MIN_SIZE)) {
				// Shrink array
				int newSize = Math.max(index * 2, MIN_SIZE);
				RandomGrabArrayItem[] r = new RandomGrabArrayItem[newSize];
				System.arraycopy(reqs, 0, r, 0, r.length);
				reqs = r;
			}
			if((ret != null) && !ret.isCancelled()) break;
		}
		}
		ret.setParentGrabArray(null);
		return ret;
	}
	
	public void remove(RandomGrabArrayItem it) {
		synchronized(this) {
		if(!contents.contains(it)) return;
		contents.remove(it);
		for(int i=0;i<index;i++) {
			if((reqs[i] == it) || reqs[i].equals(it)) {
				reqs[i] = reqs[--index];
				reqs[index] = null;
				break;
			}
		}
		}
		it.setParentGrabArray(null);
	}

	public synchronized boolean isEmpty() {
		return index == 0;
	}
}
