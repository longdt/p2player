/**
 * 
 */
package com.solt.media.update;

/**
 * @author ThienLong
 *
 */
public class UpdateChecker implements Runnable {
	public static final int INITIAL = 0;
	private static final int INTERVAL = 10 * 60000;
	private volatile int state;
	private Thread checker;
	/**
	 * 
	 */
	public UpdateChecker() {
		checker = new Thread(this, "UpdateChecker");
	}
	
	public void start() {
		checker.start();
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			Thread.sleep(10000);
			while (true) {
				Thread.sleep(INTERVAL);
			}
		} catch (InterruptedException e) {
			
		}
	}
	
	public void stop() throws InterruptedException {
		checker.interrupt();
		checker.join();
	}

}
