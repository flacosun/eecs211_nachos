package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	// Harsh Code --- Declaring a waitingThreadQueue of type ThreadQueue
	private ThreadQueue waitingThreadQueue = new ThreadedKernel().scheduler.newThreadQueue(false);
	
	/*
	 * Code Design 
	
	Sleep() Method  : All threads that call sleep() have to perform the following actions sequentially:
	
	1.  currentThread placed upon the ThreadQueue
	2.  The lock released
	3.  The currentThread put to sleep
	4.  Reacquire the lock
	5.  Return from sleep()
	
	Wake () : Calling wake() the actions to be performed are :
	1.  Take a thread off the waitingThreadQueue and place it on the ready queue
	
	WakeAll(): Upon calling wakeAll() the actions to be performed are :
	1.  Take all threads off the queue sequentially and place each on the ready queue
	 */
	
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		// Harsh Code 
		boolean currentStatus = Machine.interrupt().disable();
		waitingThreadQueue.waitForAccess(KThread.currentThread());
		conditionLock.release();
		KThread.sleep();
		conditionLock.acquire();
		
		Machine.interrupt().restore(currentStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		//Harsh Queue
		boolean currentStatus = Machine.interrupt().disable();
		
		KThread nextThreadinQueue = waitingThreadQueue.nextThread();
		if(nextThreadinQueue != null){
			nextThreadinQueue.ready();
		}
			
		Machine.interrupt().restore(currentStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		// Harsh Code
		boolean currentStatus = Machine.interrupt().disable();
		KThread eachThread;
		while ((eachThread = waitingThreadQueue.nextThread()) != null ){
			eachThread.ready();
		}
		Machine.interrupt().restore(currentStatus);
			
	}

	private Lock conditionLock;
	/*
	public static void Test(){
		Condition2Test.runTest();
	}
	*/
}
