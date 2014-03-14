package nachos.userprog;


import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import nachos.machine.*;
import nachos.threads.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {

	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
		// Modified Code
		initializeFreePhyMemoryPages();
	}

	/**
	 * Test the console device.
	 */
	
	public void selfTest() {
		super.selfTest();
	}
	/* Design requirements are:
	 * 1. create a way (method) of allocating physical memory such that different processes do not overlap in their memory usage.
	 * 2. must be able to take advantage of non-contiguous gaps in free memory pool.
	 * 3. all process's memory must be freed on exit, whether from an a call to exit or from an illegal operation.
	 * 4. modify "UserProcesss.readVirtualMemory" and "UserProcess.writeVirtualMemory" to make it capable of working with multiple processes.
	 * 5. have to maintain the pageTable for each UserProcess that maps the users virtual address to physical address.
	 * 6. have "TranslationEntry.readOnly" set correctly when read from a COFF section marked as read-only.
	 * 7. Both methods must always return the number of bytes transferred, even on failure.
	 * 8. modify "UserProcess.loadSections" to allocate the required number of pages and set up the pageTable structure.
	 */
	
	/* READ CHAPTER 9
	 * Running more than one user program at a time requires that each program has its own memory. 
	 * This means there must be a mapping from the program’s logical (or virtual memory) to physical memory.
	 * Physical memory is divided into frames (fixed size blocks). 
	 * Logical memory is divided into pages of the same size as of frames.
	 */
	
	/* MEMORY OF NACHOS (INFO TAKEN FROM INTERNET)
	 * The physical memory in Nachos is in 32 bit address space. 
	 * It is an array of bytes called "mainMemory" in "Processor.java" in the "machine" directory. 
	 * 
	 * PAGE SIZE IN NACHOS 
	 * The page size is also in Processor.java listed as hex 400 , which is 1024 decimal or 2^10.
	 * This means 10 bits are needed for the offset leaving 22 bits for the page number. 
	 * Get a reference to this memory with the "Machine.processor().getMemory()" method.
	 */
	
	/* ALGORITHM (INFO TAKEN FROM DOCUMENTATION)
	 * 1.	We need to implement a way to track "physical memory" for this task. 
	 * 		The documentation suggests a linked list of available frames i.e physical memory blocks (free space list).
	 * 
	 * 2. 	A method that returns a list of available frames for a request for memory. 
	 * For example, if the caller requests 8 frames of memory, 
	 * the method would return a list or array containing the frame numbers of 8 available frames 
	 * which are then taken off the free space list.
	 * 
	 * 3. 	A method to mark a frame as available when the caller no longer needs it. 
	 * (i.e. add it to the free space list). 
	 * 
	 * 4. 	Initially, all frames should be free.
	 */
	
	/* SYNCHORNIZATION :
	 * Synchronized access can be achieved by using a shared Semaphore.
	 * 
	 * NEW CLASSES ARE DECLARED - PageBlock and FreePages
	 * A new class "PageBlock" is declared inside UserKernel to represent a contiguous set of pages. 
	 * 
	 * Free page blocks are stored both in a sorted set (sorted by size) and in a list, sorted by size, 
	 * this provided quick access to both the relative size and position of page blocks, 
	 * and remained accurate as page blocks were added or removed.
	 */

	private class PhyMemoryBlock implements Comparable<PhyMemoryBlock>{
		// Two variables 
		//1. baseAddress which shows the start address 
		//2. limitAddress which shows the number of pages used (basically gives the  end address of the memory block) 
		public int baseAddress;
		public int limitAddress;
		
		// Constructor for PhyMemoryBlock
		public PhyMemoryBlock(int s, int n){
			baseAddress = s;
			limitAddress = n;
		}
		
		// To compare the other Memory blocks 
		public int compareTo(PhyMemoryBlock other){
			final int ABOVE = -1;
			final int EQUAL = 0;
			final int BELOW = 1;
			
			if (limitAddress < other.limitAddress)
				return ABOVE;
			if (limitAddress > other.limitAddress)
				return BELOW;
			if (limitAddress == other.limitAddress)
				return EQUAL;
			
			Lib.assertNotReached("PhyMemoryBlock limit address comparison not reachable ( Error )!!");
			return EQUAL;
		}
	}
	
	// The Physical Memory Blocks are sorted by size. Tree Set arranges its elements in ascending order.
	private TreeSet<PhyMemoryBlock> freeMemoryBlock = new TreeSet<PhyMemoryBlock>(); 
	
	// The Physical Memory Block are sorted in the positions also, as suggested in the documentation.
	// LinkedList will be used.
	private LinkedList<PhyMemoryBlock> freeMemoryPool = new LinkedList<PhyMemoryBlock>();
	
	private int totalFreeMemorypages = 0;
	private Semaphore accessSynch = null;
	
	//Initialising free Memory
	private void initializeFreePhyMemoryPages(){
		// Protect freeMemoryPool using Semaphore.
		// Create a Semaphore object here in the initialization.
		accessSynch = new Semaphore(1);
		accessSynch.P(); // stop all other access to the pool. Since Initializing therefore no interruption is desirable.
		
		// free all the memory.
		totalFreeMemorypages = Machine.processor().getNumPhysPages(); // Gets the total no. of Physical pages attached to the processor
		freeMemoryBlock.clear();
		freeMemoryBlock.add(new PhyMemoryBlock(0, totalFreeMemorypages)); // Initially all the pages are considered to be free so adding all of them to the tree set i.e BaseAdd = 0 and limit = last i.e the total pages
		
		accessSynch.V(); // release access on the pool.
	}
	
	// This method will get the smallest Free memory block from the free memory blocks available.
	public int getSmallestFreeMemoryBlock(){
		// protect access into the free memory pool
		accessSynch.P();
		
		int block = 0;
		
		try{
			PhyMemoryBlock blk = freeMemoryBlock.first();
			// since I have defined a tree set which arranges the memory blocks in ascending order.
			// so of all the elements present at this time, the first element will give us the smallest element.
			if (blk != null){
				block = blk.limitAddress;
			}
		}catch(NoSuchElementException e){
			// Throw exception if the list is not empty.
		}
		accessSynch.V();
		// release access to the pool.
		return block;
	}

	// Get the largest free memory block present at this time.
	public int getLargestFreeMemoryBlock(){
		// protect access into the free memory pool
		accessSynch.P();
		
		int block = 0;
		
		try{
			PhyMemoryBlock blk = freeMemoryBlock.last();
			// since I have defined a tree set which arranges the memory blocks in ascending order.
			// so of all the elements present at this time, the last element will give us the largest element.
			if (blk != null){
				block = blk.limitAddress;
			}
		}catch(NoSuchElementException e){
			// Throw exception if the list is not empty.
		}
		accessSynch.V();
		// release access to the pool.
		return block;
	}
	
	// This Memory Block will be released back into the free memory pool.
	
	public void releasePhyMemoryBlock(int s, int n){
		accessSynch.P(); // Stop access of others to the pool
		
		PhyMemoryBlock releaseBlock = new PhyMemoryBlock(s, n);
		int BelowBlockOffset = releaseBlock.baseAddress + releaseBlock.limitAddress;
		int AboveBlockOffset = releaseBlock.baseAddress;
		
		totalFreeMemorypages += releaseBlock.limitAddress; // LimitAddress gives the total num of Pages present inthe block and not the final address (confusion)
		
		// release Blocks position in the linked list. 13523010867
		// Use list iterator as we have to check every block.
		java.util.ListIterator<PhyMemoryBlock> freeMemPoolIterator = freeMemoryPool.listIterator();
		
		while (freeMemPoolIterator.hasNext()){
			PhyMemoryBlock currentBlock = freeMemPoolIterator.next();
			
			if(currentBlock.baseAddress < releaseBlock.baseAddress){
				// then the releaseBlock is the block AFTER/BELOW the currentBlock.
				// Checking for the adjacency of the blocks
				if(currentBlock.baseAddress + currentBlock.limitAddress == AboveBlockOffset){
					//the block is the adjacent above block.
					// First Remove the adjacent above block ( this is our current block in this condition) , then add the release blocks pages into it, and then add the new current block into the pool.
					freeMemoryBlock.remove(currentBlock);
					currentBlock.limitAddress += releaseBlock.limitAddress;
					freeMemoryBlock.add(currentBlock);
					
					accessSynch.V(); // Work done
					return;
				}
				// keep looping untill we reach to the end of the list
			}else{
				// Automatically goes to the ABOVE condition no need to check.
				// The release block is ABOVE this position.
				if(currentBlock.baseAddress == BelowBlockOffset){
					// then this is the adjacent block above
					// follow the same procedure above
					freeMemoryBlock.remove(currentBlock);
					currentBlock.limitAddress += releaseBlock.limitAddress;
					currentBlock.baseAddress = releaseBlock.baseAddress;
					freeMemoryBlock.add(currentBlock);
					
					accessSynch.V();
					return;
				}
				//If the block is not the adjacent block then we have to go one block above and insert it there.
				
				freeMemPoolIterator.previous();  // go back one step.
				break;
			}	
		}
		//if no adjacent blocks were found then insert it at the present position itseflf where the block is currently present..
		freeMemPoolIterator.add(releaseBlock);
		freeMemoryBlock.add(releaseBlock);
		
		accessSynch.V();
		
	}
	
	
	public int allocatePhyMemBlock(int limitAddress){
		accessSynch.P();
		
		int block = -1;
		Iterator<PhyMemoryBlock> iter = freeMemoryBlock.iterator();
		// this iterator will return objects in ascending order.
		
		while(iter.hasNext()){
			PhyMemoryBlock currentblock = iter.next();
			// Employing the BEst Fit algorithm.
			// checking if the block is enough. The smallest ones are in the first.
			
			if(currentblock.limitAddress >= limitAddress){
				// this block is large enough and can be used. Also iterater has given the n=blocks in the ascending order so no need to worry about wasting the memory.
				block = currentblock.baseAddress;
				
				// Remove this allocated block fromthe memory and keep the pool.
				// We need to send the available free memory back into the free Memory pool.
				
				freeMemoryBlock.remove(currentblock);
				freeMemoryPool.remove(currentblock);
				
				totalFreeMemorypages -= currentblock.limitAddress;
				currentblock.baseAddress += limitAddress;
				currentblock.limitAddress -= limitAddress;
				
				if(currentblock.limitAddress > 0){
					accessSynch.V();
					releasePhyMemoryBlock(currentblock.baseAddress, currentblock.limitAddress);
					accessSynch.P();
					
				}
			}
		}
		accessSynch.V();
		return block;
	}
	
	public int getFreePhyMemBlocks(){
		return totalFreeMemorypages;
	}
	
	public int allocatePhyMemBlock(){
		return allocatePhyMemBlock(1);
		
	}
		
	public void releasePhyMemBlock(int start){
		releasePhyMemoryBlock(start, 1);
	}
		
	// Modifies the page Table to contain all the pages  required.
	
	public TranslationEntry[] fillPageTable(int limitAddress){
		TranslationEntry[] pageTable = new TranslationEntry[limitAddress];
		int currentVirtualBlock = 0;
		
		while(currentVirtualBlock < limitAddress){
			int pagesRequired = limitAddress - currentVirtualBlock;
			int start = -1;
			int largestBlock = getLargestFreeMemoryBlock();
			
			// attempting a contiguous block, else largest available block will be opted.
			if(largestBlock <= 0){
				 break;	 
			}else if (largestBlock < pagesRequired){
				pagesRequired = largestBlock;
			}
			start = allocatePhyMemBlock(pagesRequired);
			
			if (start >= 0){
				for(int i = 0; i < pagesRequired ; i ++){
					pageTable [i + currentVirtualBlock] = new TranslationEntry(i+ currentVirtualBlock, i + start, true, false, false, false);
				}
				currentVirtualBlock += pagesRequired;
			}
		}
		if(pageTable[pageTable.length-1] == null){
			
			emptyPageTable(pageTable);
			pageTable = null;
		}
		return pageTable;
	}
	
	public void emptyPageTable(TranslationEntry[] pageTable){
		PhyMemoryBlock block = null;
		for( int currentVirtualblock = 0; currentVirtualblock < pageTable.length; currentVirtualblock++){
			TranslationEntry entry = pageTable[currentVirtualblock];
			if(entry != null && entry.valid){
				if(block != null){
					if((block.baseAddress + block.limitAddress) == entry.ppn){
						block.limitAddress += 1;
					}else{
						//New section of contiguous memory blocks
						releasePhyMemoryBlock(block.baseAddress, block.limitAddress);
						block.limitAddress = 1;
						block.baseAddress = entry.ppn;
					}	
				}else {
					block = new PhyMemoryBlock(entry.ppn, 1);
				}
					
			}
		}
		// Release the last block. ---------------------------------------------------------------------------------------------------------------------------
		if (block != null){
			releasePhyMemoryBlock(block.baseAddress, block.limitAddress);
		}
	}
	
	//provide a nicer way for the UserProcess to access the UserKernel
		public static UserKernel getKernel() {
			if(kernel instanceof UserKernel) return (UserKernel)kernel;
			return null;		
		}
	
	
	
	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
	
	
	// CODE Hint taken from Internet. A nice way of obtaining the kernel.
	/*
	public static UserKernel obtainKernel(){
		if(kernel instanceof UserKernel) return (UserKernel) kernel;
		return null;
	}
	*/
	}

