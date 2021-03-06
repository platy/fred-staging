/**
 * @author toad
 * To the extent that this is copyrightable, it's part of Freenet and licensed 
 * under GPL2 or later. However, it's a trivial interface taken from Sun JDK 1.5,
 * and we will use that when we migrate to 1.5.
 */
package freenet.support;

public interface Executor {
	
	/** Execute a job. */
	public void execute(Runnable job, String jobName);
	public void execute(Runnable job, String jobName, boolean fromTicker);

	/** Count the number of threads waiting for work at each priority level */
	public int[] waitingThreads();
	/** Count the number of threads running at each priority level */
	public int[] runningThreads();

	/** Fast method returning how many threads are waiting */
	public int getWaitingThreadsCount();
}
