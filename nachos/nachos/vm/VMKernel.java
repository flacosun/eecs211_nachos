package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}
	
	protected OpenFile swapFile;
	protected List<Integer> freeSwapList;
	protected Map<IPTEntry, Integer> swapFileMap;
	protected Lock swapLock;
	
	
	void reclaimSwapSpace(VMProcess vmp){
		swapLock.acquire();
	    for(int i = 0; i < vmp.numPages; i++){
	    	IPTEntry tempEntry = new IPTEntry(vmp.processID,i);
	        int location = removeFromSwap(tempEntry);//remove this from the swapFileTable
	        if (location != -1){
	           freeSwapList.add(location);
	           //TODO:? load it back into memory;
	        }
	        else {
	        	//?Something bad happens, one page of this process is not found in the swapFileTable
	        	vmp.KillProcess();
	        }
	    }
	    swapLock.release();
	}
	
	public void reclaimMemory(VMProcess vmp) {
		for(int i = 0; i < vmp.numPages; i++) {
			IPTEntry entry = new IPTEntry(vmp.processID, i);
			if(VMProcess.IPT.containsKey(entry)) {
				TranslationEntry te = VMProcess.IPT.get(entry);
				if(coreMap[te.ppn].te == te){
					pageLock.acquire();
			        freeList.add(coreMap[te.ppn]);
			        pageLock.release();
				}
				VMProcess.IPT.remove(entry);
			}
		}
		
	}

	private int removeFromSwap(IPTEntry entry) {
		if(swapFileMap.containsKey(entry)) { //Page found in swap, remove it and return the value of its offset in swap.
			int location = swapFileMap.get(entry);
			swapFileMap.remove(entry);
			return location;
		}
		return -1; //Page not found in swap
	}
	
	//Getting space on the swap
	private int swapLength;
	 
	void writeToSwap(CoreMapEntry cme){
		int position;//page's postion in Swap 
		int swapOffset;
	    if(cme.vmp == currentProcess()){
	    	for(int i = 0; i < validTLB; i++){
	           if(Machine.processor().readTLBEntry(i).ppn == cme.te.ppn){
	        	   cme.te = Machine.processor().readTLBEntry(i);
	           }
	        }
	     }
	     if(!cme.te.dirty){
	        return;
	     }
	     swapLock.acquire();
	     IPTEntry swapEntry = new IPTEntry(cme.vmp.processID, cme.te.vpn);
	     //location = get location of page from swapFileTable for cme.vmp.procID and cme.te.vpn
	     if (swapFileMap.containsKey(swapEntry)){
	        swapOffset = swapFileMap.get(swapEntry) * pageSize;
	     } 
	     else {
	         if (freeSwapList.size() != 0){
	        	 position = freeSwapList.remove(0);
	             swapOffset = position * pageSize;
	         } 
	         else {
	        	 position = swapLength;
	             swapOffset =  position* pageSize;
	             swapLength++;
	         }
	         swapFileMap.put(swapEntry, position);
	     }
	     swapLock.release();
	     int memoryOffset = cme.te.vpn * pageSize;
	     byte[] buffer = new byte[pageSize]; 
	     int bytesTransferred = cme.vmp.readVirtualMemory(memoryOffset, buffer);
	     if(bytesTransferred != pageSize) {
	    	 Lib.debug('a', "bytesTransferred != pageSize!");
	    	 cme.vmp.KillProcess();
	     }
	     int byteWritten = swapFile.write(buffer, swapOffset, pageSize); 
	     if(byteWritten == -1){
	    	 Lib.debug('a', "The number of bytes written to swapFile is incorrect!");
	    	 cme.vmp.KillProcess();
	     }
	     cme.te.dirty = false;//set cme.te to not dirty
	}
	
	//resume the entry back to  assumes that the page is either in swap or in the executable
	void pageIn(CoreMapEntry cme){
	    swapLock.acquire();
	    IPTEntry swapEntry = new IPTEntry(cme.vmp.processID, cme.te.vpn);
	    int location = -1;
	    if (swapFileMap.containsKey(swapEntry)){
	    	location = swapFileMap.get(swapEntry);
	    }
	    swapLock.release();
	    if (location == -1){
	       cme.te.readOnly = cme.vmp.lazyPageIn(cme.te.vpn, cme.frameNumber);
	       //TODO: Question: What about the location in this case?
	    } 
	    else {
	       int memoryOffset = cme.frameNumber * pageSize;
	       int swapOffset = location * pageSize;
	       byte[] buffer = new byte[pageSize];
	       int bytesRead = swapFile.read(buffer, swapOffset, pageSize);
	       if(bytesRead != pageSize) {
		    	 Lib.debug('a', "The number of bytes read from swapFile is incorrect!");
		    	 cme.vmp.KillProcess();
		   }
		   int bytesTransferred = cme.vmp.writeVirtualMemory(memoryOffset, buffer);
		   if(bytesTransferred != pageSize) {
		    	 Lib.debug('a', "bytesTransferred != pageSize!");
		    	 cme.vmp.KillProcess();
		   }
	    }
	}
	
	protected Lock pageLock;
	protected Condition pageCV;
	CoreMapEntry[] coreMap;
	private Condition liveLockCV;
	 
	  // if you do not have the page lock DO NOT CALL THIS METHOD
	boolean lockFrame(int frameNumber){
		CoreMapEntry cme = coreMap[frameNumber];
	    if(cme == null) return true;
	    boolean returnValue = cme.inUse;
	    cme.inUse = true;
	    return returnValue;
	}
	 
	// if you do not have the page lock DO NOT CALL THIS METHOD
	void unlockFrame(int frameNumber){
	    coreMap[frameNumber].inUse = false;
	    liveLockCV.wakeAll();
	}
	
	//FindFreeFrame
	
	List<CoreMapEntry> freeList;

	 
	CoreMapEntry selectFrame(int vpn, int procID){
		CoreMapEntry victim = null;
		if (freeList.size() < numPhyPages){//FREE_LIST_SIZE
			boolean duplicate = true; 
			while(duplicate){
				victim = clock(); //run clock() to select a victim frame;
				if(!freeList.contains(victim)) duplicate = false;
			}
	        pageLock.acquire();
	        freeList.add(victim);
	        pageCV.wake();
	        pageLock.release();
	    }
	    for(CoreMapEntry cme : freeList) {
	        if(cme.vmp != null && cme.vmp.processID == procID && cme.te.vpn == vpn) {
	            freeList.remove(cme);
	            return cme;
	        }
	    }
	    victim = freeList.remove(0);
	    return victim;
	}
	
	
	static final int N = 2;
	 
	CoreMapEntry clock() {
		boolean used = false;
		int tickCount = 0;
		while (true) {
			if (coreMap[clockIndex] != null) {
				if (coreMap[clockIndex].te.used) {
					coreMap[clockIndex].ref = 0;
				} 
				else {
					coreMap[clockIndex].ref++;
				}
				if (coreMap[clockIndex].ref >= N) {
					pageLock.acquire();
					used = lockFrame(clockIndex);
					pageLock.release();
					if (used == false) {
						
						clockIndex++;
						if (clockIndex >= coreMap.length) clockIndex = 0; //if clockIndex goes beyond the number of physical pages, set it back to 0;
						
						break;
					}
				}
				coreMap[clockIndex].te.used = false;
			}
			tickCount++;
			clockIndex++;
			if (clockIndex >= coreMap.length) clockIndex = 0; //if clockIndex goes beyond the number of physical pages, set it back to 0;
			if (tickCount > (N+1) * coreMap.length) {
				pageLock.acquire();
				//liveLockCV.sleep();
				pageLock.release();
				tickCount -= coreMap.length;
			}
	    }
	    CoreMapEntry cme = coreMap[clockIndex];
	    clockIndex ++;
	    if (clockIndex >= coreMap.length) clockIndex = 0; 
	    //TODO: release the ptLock;
	    return cme;
	}
	 
	CoreMapEntry antiClock() {
		CoreMapEntry cme;
		boolean used = false;
		while (true) {
			double i = Math.random();
			i = i * numPhyPages; //number of physical pages;
			int index = (int)i;
			cme = coreMap[index];
			if (cme != null) {
				used = lockFrame(index);
				if (! used) {
					return cme;
				}
			}
		}
	}
	
	int loadPage(int vpn, VMProcess vmp){
	     if(vmp.canLazyLoad(vpn) == false && VMProcess.IPT.containsKey(new IPTEntry(vmp.processID, vpn)) == false){
	        return -1;
	     }
	     swapLock.acquire();
	     CoreMapEntry victim = selectFrame(vpn, vmp.processID);
	     int frameNum = victim.frameNumber;
	     swapLock.release();
	     if (victim.vmp == null || victim.vmp.processID != vmp.processID || victim.te.vpn != vpn){
	        if(victim.te.dirty){// write the page to disk if needed
	        	writeToSwap(victim);
	        }
	        if(victim.vmp != null){
	        	IPTEntry entry = new IPTEntry(victim.vmp.processID, victim.te.vpn);
	        
	        	if(VMProcess.IPT.containsKey(entry)){//zero out the page if necessary
	        		VMProcess.IPT.remove(entry);
	        	}
	        }
	        victim.te.vpn = vpn;
	        victim.vmp = vmp;
	        victim.te.readOnly = false;
	        pageIn(victim);
	     }
	 
	     victim.te.valid = true;
	     victim.ref = 0;
	     swapLock.acquire();
	     coreMap[frameNum] = victim;
	     swapLock.release();
	     pageLock.acquire();
	     VMProcess.IPT.put(new IPTEntry(victim.vmp.processID, victim.te.vpn), victim.te);
	     pageLock.release();
	     return frameNum;
	}
	
	
	
	
	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args){
	     super.initialize(args);
	     coreMap = new CoreMapEntry[numPhyPages];
	     for(int i = 0 ;i < numPhyPages; i++){
	        coreMap[i] = null;
	     }
	     freeList = new LinkedList<CoreMapEntry>();
	     freeSwapList = new LinkedList<Integer>();
	     TranslationEntry[] iArray = ((VMKernel) (Kernel.kernel)).fillPageTable(numPhyPages);
	     for(int i = 0; i < iArray.length; i++){
	    	 CoreMapEntry cme = new CoreMapEntry(iArray[i], null, i, false, 0);
	    	 coreMap[i] = cme;
	    	
	         freeList.add(cme);
	     }
	     
	     swapFileMap = new HashMap<IPTEntry, Integer>();
	     swapLock = new Lock();
	     pageLock = new Lock();
	     pageCV = new Condition(pageLock);
	     liveLockCV = new Condition(pageLock);
	     swapLength = 0;
	     validTLB = 0; //initialize valid TLB.
	     swapFile = new OpenFile(null, "swap");
	     clockIndex = 0;
	}
	

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		//TODO: The swap file should be closed and deleted
		super.terminate();
	}
	
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;
	public static int numPhyPages = Machine.processor().getNumPhysPages();
	private static final int pageSize = Processor.pageSize;
	public static int validTLB;
	private static int clockIndex;
	private static final char dbgVM = 'v';
	
}
