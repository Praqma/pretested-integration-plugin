package org.jenkinsci.plugins.pretestedintegration;

import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public final class BuildQueue {
    
	private final static Logger logger = Logger.getLogger(BuildQueue.class.getName());// Generated code DONT TOUCH! Bookmark: 3ca61d8e671737b5ead8aaccd31875c4
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
		logger.entering("BuildQueue", "getInstance");// Generated code DONT TOUCH! Bookmark: e85647720e74c86d6e3c473cc744be0d
		if(instance == null) {
			instance = new BuildQueue();
		}
		logger.exiting("BuildQueue", "getInstance");// Generated code DONT TOUCH! Bookmark: a8860d044f6d7209a50db4df3efc155e
		return instance;
	}
	
	/**
	* Block until the commit can be tested without any other commits
	* running at the same time.
	* After calling this function, release() MUST be called, so make sure that
	* every error scenario releases the lock at some point.
	*/
	public void enqueueAndWait() {
		logger.entering("BuildQueue", "enqueueAndWait");// Generated code DONT TOUCH! Bookmark: de958aca2d596894ae022627fa2c59f7
		semaphore.acquireUninterruptibly();
		logger.exiting("BuildQueue", "enqueueAndWait");// Generated code DONT TOUCH! Bookmark: 8d9b0ef93c509d9925807ba1d651884c
	}
	
	/**
	* Signal that the build is done and a new one can get access.
	*/
	public void release() {
		logger.entering("BuildQueue", "release");// Generated code DONT TOUCH! Bookmark: ab5b5d150b1249cfe2d92dc55e826a2f
		semaphore.release();
		logger.exiting("BuildQueue", "release");// Generated code DONT TOUCH! Bookmark: 5ba4d0199b4978dfb73accdbfd946aae
	}
	
	/**
	* Test if the lock is available, without blocking.
        * @return boolean indicating if the queue is not occupied 
	*/	
	public boolean available() {
		logger.entering("BuildQueue", "available");// Generated code DONT TOUCH! Bookmark: a5770e885a1c3c79b9996916440b4c7a		
        logger.exiting("BuildQueue", "available");
		return semaphore.availablePermits() > 0;
	}
}
