package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	// HARSH's CODE ------ Defining Class specific variables to be used in the methods -- waituntill and Timer Interrupt 
	// Defining an Alarm.java specific ArrayList of type suspendedThread which contains the suspendedThread's List.
	private static ArrayList<SuspendedThread> suspendedThreadList = new ArrayList<SuspendedThread>();
	private static long wakeTime;
	
	
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		// KThread.currentThread().yield(); // This is that Busy Waiting code 
// ---------- HARSH's CODE ----------------------------------------------------
		// Go through all the elements of the ArrayList suspendedThreadList
		for ( int i = suspendedThreadList.size()-1; i >= 0; i--){
			// 1. if suspendedThread's wait time is complete. i.e "x" ticks are done 
			// 2. Disable Interrupts first ( for atomicity of putting thread in ready queue)
			// 3. then put the thread into the ready queue.
			if( suspendedThreadList.get(i).getwaketime() <= Machine.timer().getTime()){
				boolean intStatus = Machine.interrupt().disable();				
				suspendedThreadList.get(i).getThread().ready();  				// See KThread.ready method
				// we have only added the thread into the ready queue from the suspendedThreadList. 
				// We have not removed the thread from the suspendedThreadList.
				// So Remove the Thread from the suspendedThreadList. 
				suspendedThreadList.remove(i);
				// This remove method will shift the elements to the left.
				// Enable the Interrupts now.
				Machine.interrupt().restore(intStatus);
			}
		}
		//Yield the Current Thread now.
		KThread.currentThread().yield(); 					// see Kthread.yield()
	}
	
	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		
//------- HARSH's CODE ---------------------------------------------------------------------
		/* The below solution contains the busy waiting. ( Harsh )
		long wakeTime = Machine.timer().getTime() + x;
		while (wakeTime > Machine.timer().getTime())
			KThread.yield();
		*/
		
		// Calculate the wake time for the thread 
		// Current time is given by Machine.timer().getTime()
		long wakeTime = Machine.timer().getTime() + x;
		
		// if wake time has not passed
		if(wakeTime > Machine.timer().getTime()){
		//Disable the INTERRUPTS to provide atomicity. 	
			boolean intStatus = Machine.interrupt().disable();
		//Creating an object of Suspended Thread class to hold all the thread and its wake time
			SuspendedThread sT = new SuspendedThread(KThread.currentThread(), wakeTime);
		// populating the arraylist suspendedThreadList
			suspendedThreadList.add(sT);
		//And put the current thread to sleep
			KThread.currentThread().sleep();
		//Enable the Interrupts
			Machine.interrupt().restore(intStatus);	
		}
//-----------------------------------------------------------------------------------HARSH's CODE
	}
// ------------ HARSH's CODE ---------------------------- 
	public class SuspendedThread{
		private KThread thread;
		private long waketime;
		
		//suspended thread constructor
		SuspendedThread(KThread thread, long waketime){
			this.thread = thread;
			this.waketime = waketime;
			}
		// Generic get method
		public long getwaketime(){
			return waketime;
		}
		//generic get method 
		public KThread getThread(){
			return thread;
		}
		
	}
	/*
	public static void selfTest(){
		AlarmTest.runTest();
	}
	*/
	
}