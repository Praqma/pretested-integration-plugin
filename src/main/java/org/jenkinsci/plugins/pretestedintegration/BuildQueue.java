package org.jenkinsci.plugins.pretestedintegration;

import java.util.concurrent.Semaphore;

public final class BuildQueue {
	private static BuildQueue instance;
	
	Semaphore semaphore;
	
	/**
	* Private because it is a singleton.
	*/	
	private BuildQueue() {
		semaphore = new Semaphore(1, true);
	}
	
	/**
	* Get the singleton instance.
	*/
	public static BuildQueue getInstance() {
		if(instance == null) {
			instance = new BuildQueue();
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
        * @return boolean indicating if the queue is not occupied 
	*/	
	public boolean available() {
		return semaphore.availablePermits() > 0;
	}
}
