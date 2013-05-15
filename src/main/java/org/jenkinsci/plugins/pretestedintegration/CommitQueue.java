package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.Queue;

import java.util.concurrent.Semaphore;
/**
 * Queue for ensuring that only one build is running at a time. This class is a
 * Singleton, so use getInstance() to access it.
 */
public class CommitQueue {
	
	// Singleton instance
	private static CommitQueue instance;
	
	Semaphore semaphore;
	
	/**
	 * Private because it is a singleton.
	 */	
	private CommitQueue() {
		semaphore = new Semaphore(1, true);
	}
	
	/**
	 * Get the singleton instance.
	 */
	public static CommitQueue getInstance() {
		if(instance == null) {
			instance = new CommitQueue();
		}
		return instance;
	}
	
	/**
	 * Block until the commit can be tested without any other commits
	 * running at the same time.
	 * After calling this function, release() MUST be called, so make sure that
	 * every error scenario releases the lock at some point.
	 */
	public void enqueueAndWait() {
		semaphore.acquireUninterruptibly();
	}
	
	/**
	 * Signal that the build is done and a new one can get access.
	 */
	public void release() {
		semaphore.release();
	}

	/**
	 * Test if the lock is available, without blocking.
	 */	
	public boolean available() {
		return semaphore.availablePermits() > 0;
	}
}
