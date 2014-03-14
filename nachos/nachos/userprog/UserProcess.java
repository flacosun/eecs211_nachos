package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		//Hajara's Code
		for (int i=0; i<MAX; i++)
		{ 
	         file[i] = new fileclass(); 
	    } 
		file[STDIN].file = UserKernel.console.openForReading(); 
	    file[STDIN].pos = 0;
	    Lib.assertTrue(file[STDIN] != null); 
	    OpenFile opn = UserKernel.fileSystem.open("out", false); 
	    //OpenFile opn = UserKernel.fileSystem.open("out", true); 
	    file[STDOUT].file = UserKernel.console.openForWriting(); 
	    file[STDOUT].pos = 0;
	    Lib.assertTrue(file[STDOUT] != null); 

	    int var = empty(); 
	    //System.out.println("*** File handle: " + var); 
	    file[var].file = opn;
	    file[var].pos = 0; 
	    //--
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		//Xiangqing's code
		fileList = new LinkedList<OpenFile>();
		childList = new LinkedList<UserProcess>();
		exitStatusMap = new HashMap<Integer, Integer>();
		exitStatusMapLock = new Lock();
		//Allocate process ID
		processIDLock.acquire();
		processID = processIDCounter;
		//Add processIDCounter by 1;
		if(processIDCounter == Integer.MAX_VALUE)
			processIDCounter = 1;
		else
			processIDCounter++;
		processIDLock.release();
		//Xiangqing's code ends
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		this.thread = new UThread(this);
		this.thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	// Code Starts
	// Getting a map from Virtual(logical Address) to Physical address space.
	private int mapVirtualToPhysicalAddress(int address){
		int virtualPage = Processor.pageFromAddress(address);
		int offset = Processor.offsetFromAddress(address);
		
		if(virtualPage >= pageTable.length){
			return -1;
		}
		
		TranslationEntry entry = pageTable[virtualPage];
		
		if(!entry.valid){
			return -1;
		}
		
		int phyPage = pageTable[virtualPage].ppn;
		
		return Processor.makeAddress(phyPage, offset);
	}	
	
	
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		//make sure that virtual address is valid for this process' virtual address space == Fred 1 
		// Check for less than memory block .. and mmore than memory blocks
		if (vaddr < 0)
			vaddr = 0;
		if (length > nachos.machine.Processor.makeAddress(numPages-1, pageSize-1) - vaddr)
			length = nachos.machine.Processor.makeAddress(numPages-1, pageSize-1) - vaddr;

		byte[] memory = Machine.processor().getMemory();

		int firstVirtualBlock = nachos.machine.Processor.pageFromAddress(vaddr);
		int lastVirtualBlock = nachos.machine.Processor.pageFromAddress(vaddr+length);
		int numOfBytesTransferred = 0;
		for (int i=firstVirtualBlock; i<=lastVirtualBlock; i++){
			if (!pageTable[i].valid)
				break; 
			//stop reading, return numBytesTransferred for whatever we've written so far
			int firstVirtualBlkAdd = nachos.machine.Processor.makeAddress(i, 0);
			int lastVirtualBlkAdd = nachos.machine.Processor.makeAddress(i, pageSize-1);
			int offset1;
			int offset2;
			//virtual page is in the middle, copy entire page (most common case)
			if (vaddr <= firstVirtualBlkAdd && vaddr+length >= lastVirtualBlkAdd){
				offset1 = 0;
				offset2 = pageSize - 1;
			}
			//virtual page is first to be transferred
			else if (vaddr > firstVirtualBlkAdd && vaddr+length >= lastVirtualBlkAdd){
				offset1 = vaddr - firstVirtualBlkAdd;
				offset2 = pageSize - 1;
			}
			//virtual page is last to be transferred
			else if (vaddr <= firstVirtualBlkAdd && vaddr+length < lastVirtualBlkAdd){
				offset1 = 0;
				offset2 = (vaddr + length) - firstVirtualBlkAdd;
			}
			//only need inner chunk of a virtual page (special case)
			else { 
				//(vaddr > firstVirtAddress && vaddr+length < lastVirtAddress)
				offset1 = vaddr - firstVirtualBlkAdd;
				offset2 = (vaddr + length) - firstVirtualBlkAdd;
			}
			int firstPhysAddress = nachos.machine.Processor.makeAddress(pageTable[i].ppn, offset1);
			//int lastPhysAddress = Machine.processor().makeAddress(pageTable[i].ppn, offset2);
			System.arraycopy(memory, firstPhysAddress, data, offset+numOfBytesTransferred, offset2-offset1);
			numOfBytesTransferred += (offset2-offset1);
			pageTable[i].used = true;
		}		
		return numOfBytesTransferred;
	}


	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset,int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		//make sure that virtual address is valid for this process' virtual address space
		if (vaddr < 0)
			vaddr = 0;
		if (length > nachos.machine.Processor.makeAddress(numPages-1, pageSize-1) - vaddr)
			length = Processor.makeAddress(numPages-1, pageSize-1) - vaddr;

		int firstVirtualBlock = Processor.pageFromAddress(vaddr);
		int lastVirtualBlock = Processor.pageFromAddress(vaddr+length);
		int numOfBytesTransferred = 0;
		for (int i=firstVirtualBlock; i<=lastVirtualBlock; i++){
			if (!pageTable[i].valid || pageTable[i].readOnly)
				break; //stop writing, return numBytesTransferred for whatever we've written so far
			int firstVirtualBlkAdd = Processor.makeAddress(i, 0);
			int lastVirtualBlkAdd = Processor.makeAddress(i, pageSize-1);
			int offset1;
			int offset2;
			//virtual page is in the middle, copy entire page (most common case)
			if (vaddr <= firstVirtualBlkAdd && vaddr+length >= lastVirtualBlkAdd){
				offset1 = 0;
				offset2 = pageSize - 1;
			}
			//virtual page is first to be transferred
			else if (vaddr > firstVirtualBlkAdd && vaddr+length >= lastVirtualBlkAdd){
				offset1 = vaddr - firstVirtualBlkAdd;
				offset2 = pageSize - 1;
			}
			//virtual page is last to be transferred
			else if (vaddr <= firstVirtualBlkAdd && vaddr+length < lastVirtualBlkAdd){
				offset1 = 0;
				offset2 = (vaddr + length) - firstVirtualBlkAdd;
			}
			//only need inner chunk of a virtual page (special case)
			else { //(vaddr > firstVirtAddress && vaddr+length < lastVirtAddress)
				offset1 = vaddr - firstVirtualBlkAdd;
				offset2 = (vaddr + length) - firstVirtualBlkAdd;
			}
			int firstPhysAddress = Processor.makeAddress(pageTable[i].ppn, offset1);
			//int lastPhysAddress = Machine.processor().makeAddress(pageTable[i].ppn, offset2);
			System.arraycopy(data, offset+numOfBytesTransferred, memory, firstPhysAddress, offset2-offset1);
			numOfBytesTransferred += (offset2-offset1);
			pageTable[i].used = pageTable[i].dirty = true;
		}

		return numOfBytesTransferred;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();                                                                                   //

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;                                                                                   // 

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		
		// Allocate pages to Multi User processes.
		
		pageTable = ((UserKernel) (Kernel.kernel)).fillPageTable(numPages);
		//if (numPages > Machine.processor().getNumPhysPages()) {
		if(pageTable == null){
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				int phyAddr = mapVirtualToPhysicalAddress(Processor.makeAddress(vpn, 0));
				
				if(phyAddr >= 0){
					section.loadPage(i, Processor.pageFromAddress(phyAddr));
					pageTable[vpn].readOnly = section.isReadOnly();
				}else{
					coff.close();
					Lib.debug(dbgProcess, "\tInvalid Page while Loading");
					return false;
				}
			}
		}
		return true; 
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	
	protected void unloadSections() {
		if(pageTable != null){
			((UserKernel)(UserKernel.kernel)).emptyPageTable(pageTable);
			
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/* ************************************Xiangqing's Code**************************/
	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}
	/**
	 * Handle the exit() system call.
	 */
	private void handleExit(int exitValue) {
		if (parent != null)
			parent.childList.remove(this);
		unloadSections();
		if (parent != null)
		{
			parent.exitStatusMapLock.acquire();
			parent.exitStatusMap.put(processID, exitValue);
			parent.exitStatusMapLock.release();
		}
		for(int i = 0; i < childList.size(); i++){
			UserProcess child = childList.get(i);
			child.parent = null;
		}
		exitStatusMap.clear();
		childList.clear();
		if (processID == 0){
			Kernel.kernel.terminate();
		}
		else{
			UThread.finish();
		}
	}
	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int pid, int statusVaddr) {
		UserProcess child = null;
		int numOfChild = childList.size();
		for (int i = 0; i < numOfChild; i++){
			if(childList.get(i).processID == pid){
				child = childList.get(i);
			}
		}
		if(child == null) {
			Lib.debug(dbgProcess, "handleJoin Error: no child found with specified Process ID");
			return -1;
		}
		if(child.thread != null){
			child.thread.join();
		}
		childList.remove(child);
		child.parent = null;
		exitStatusMapLock.acquire();
		if(!exitStatusMap.containsKey(child.processID)){
			Lib.debug(dbgProcess, "handleJoin Error: Can not get exit status of child process");
			return 0;
		}
		int exitStatus = exitStatusMap.get(child.processID).intValue();
		exitStatusMap.remove(child.processID);
		exitStatusMapLock.release();
		
		if(exitStatus == unhandledException){
			return 0; 
		}
		byte[] buffer = new byte[4];
		Lib.bytesFromInt(buffer, 0, exitStatus);
		int numOfBytesTransferred = writeVirtualMemory(statusVaddr, buffer);
		if (numOfBytesTransferred == 4){
			return 1; 
		}
		else{
			Lib.debug(dbgProcess, "handleJoin Error: Failed to write status");
			return 1;
		}
	}
	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int fileVirtualAddress, int numOfArg, int argOffset) {
		//bullet proof the exec
		if(fileVirtualAddress < 0){
			Lib.debug(dbgProcess, "Exec Error: Virtual Address is illegal");
			return -1;
		}
		String fileAddress = readVirtualMemoryString(fileVirtualAddress, 256);
		if(fileAddress == null){
			Lib.debug(dbgProcess, "Exec Error: No file address found in momery");
			return -1;
		}
		if(!fileAddress.endsWith("coff")&&!fileAddress.endsWith("COFF")){
			Lib.debug(dbgProcess, "Exec Error: This file is not a coff file");
			return -1;
		}
		if (numOfArg < 0){
			Lib.debug(dbgProcess, "Exec Error: Number of arguments must be non-negative");
			return -1;
		}
		String[] arguments = new String[numOfArg];
		for(int i=0; i < numOfArg; i++ ){
			byte[] buffer = new byte[4];
			int numOfBytesTransferred = readVirtualMemory(argOffset + (i*4), buffer);
			if (numOfBytesTransferred != 4){
				Lib.debug(dbgProcess, "Exec Error: Failed to read agument address");
				return -1;
			}
			int argVaddr = Lib.bytesToInt(buffer, 0);
			String argument = readVirtualMemoryString(argVaddr, 256);
			if (argument == null){
				Lib.debug(dbgProcess, "Exec Error: Failed to read argument");
				return -1;
			}
			arguments[i] = argument;
		}
		//execute the process by calling execute() method
		UserProcess child = UserProcess.newUserProcess();
		if (child.execute(fileAddress, arguments)){
			this.childList.add(child);
			child.parent = this;
			Lib.debug(dbgProcess, "Executing " + child.processID);
			
			return child.processID;
		}else{
			Lib.debug(dbgProcess, "Exec Error: Failed to execute the coff");
			return -1;
		} 	
	}
	

	/* ************************************Xiangqing's Code ends**************************/
	/* Hajara's code***/
	private int create(int par) {
		  
    	Lib.debug(dbgProcess, "create()"); 
        String filename = readVirtualMemoryString(par, MAXSTRLEN); 
        Lib.debug(dbgProcess, "filename: "+filename); 
        OpenFile ret = UserKernel.fileSystem.open(filename, true);  
        if (ret == null)
        {  
        	return -1;  
        }  
        else
        {  
            int var = empty();  
            if (var < 0)  
                return -1;  
            else 
            {  
                file[var].filename = filename;  
                file[var].file = ret;  
                file[var].pos = 0; 
                return var; 
            } 
        } 
    } 

    private int open(int par) 
    {
    	Lib.debug(dbgProcess, "open()"); 
    	        String filename = readVirtualMemoryString(par, MAXSTRLEN); 
    	Lib.debug(dbgProcess, "filename: "+filename); 
    	OpenFile ret = UserKernel.fileSystem.open(filename, false); 

    	        if (ret  == null)
    	        { 
    	            return -1; 
    	        }
    	        else 
    	        { 
    	            int var = empty(); 
    	            if (var < 0) 
    	                return -1;
    	            else 
    	            { 
    	                file[var].filename = filename; 
    	                file[var].file = ret ; 
    	                file[var].pos = 0; 
    	                return var; 
    	            }  
    	        } 
    	    } 
    private int read(int par1, int par2, int par3)
    {
    	//Lib.debug(dbgProcess, "read()"); 
    	         
    	        int handle = par1;
    	        int vaddr = par2; 
    	        int bufsize = par3; 

    	//Lib.debug(dbgProcess, "handle: " + handle); 
    	//Lib.debug(dbgProcess, "address: " + vaddr); 
    	//Lib.debug(dbgProcess, "size: " + bufsize); 
    	        if (handle < 0 || handle > MAX 
    	                || file[handle].file == null) 
    	            return -1; 

    	        fileclass fd = file[handle]; 
    	        byte[] buffer = new byte[bufsize]; 
    	        int ret = fd.file.read(buffer, 0, bufsize); 

    	        if (ret < 0)
    	        {  
    	            return -1; 
    	        } 
    	        else 
    	        { 
    	            int number = writeVirtualMemory(vaddr, buffer); 
    	            fd.pos = fd.pos + number; 
    	            return ret ; 
    	        } 
    	    } 
    	    

    	     private int write(int par1, int par2, int par3) {
    	Lib.debug(dbgProcess, "write()"); 
    	         
    	        int handle = par1; 
    	        int vaddr = par2; 
    	        int bufsize = par3;

    	Lib.debug(dbgProcess, "handle: " + handle);  
    	Lib.debug(dbgProcess, "address: " + vaddr);  
    	Lib.debug(dbgProcess, "size: " + bufsize);  
    	        if (handle < 0 || handle > MAX 
    	                || file[handle].file == null)  
    	            return -1;  

    	        fileclass fd = file[handle]; 

    	        byte[] buffer = new byte[bufsize]; 

    	        int tot_byte = readVirtualMemory(vaddr, buffer); 
    	        int ret = fd.file.write(buffer, 0, tot_byte); 

    	        if (ret < 0) 
    	        {  
    	            return -1;  
    	        } 
    	        else 
    	        { 
    	            fd.pos = fd.pos + ret;  
    	            return ret; 
    	        }  
    	    }
    	     private int close (int par)
    	     { 
    	    	 Lib.debug(dbgProcess, "close()"); 
    	    	         
    	    	         int handle = par; 
    	    	         if (par < 0 || par >= MAX) 
    	    	             return -1; 
    	    	         boolean ret = true; 

    	    	         fileclass fd = file[handle]; 
    	    	         fd.pos = 0;  
    	    	         fd.file.close(); 
    	    	         if (fd.removes) 
    	    	         { 
    	    	             ret= UserKernel.fileSystem.remove(fd.filename); 
    	    	             fd.removes = false; 
    	    	         } 

    	    	         fd.filename = "";
    	    	         return ret ? 0 : -1; 

    	     }
    	     private int unlink(int par) {
    	    	 Lib.debug(dbgProcess, "unlink()");

    	    	         boolean ret = true;

    	    	         String f_name = readVirtualMemoryString(par, MAXSTRLEN); 

    	    	 Lib.debug(dbgProcess, "file: " + f_name); 

    	    	         int var = files(f_name); 
    	    	         /*Xiangqing's fix
    	    	          Code Before fix:
    	    	          
    	    	          if (var < 0) 
    	    	         { 
    	    	   
    	    	             ret = UserKernel.fileSystem.remove(file[var].name); 
    	    	         } 
    	    	         else
    	    	         {
    	    	              file[var].removes = true; 
    	    	         }
    	    	          */
    	    	          
    	    	         ret = UserKernel.fileSystem.remove(f_name); 
    	    	         if (var >= 0 && ret)
    	    	         {
    	    	        	 
    	    	              file[var].removes = true; 
    	    	         }
    	    	         return ret ? 0 : -1; 
    }
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallExit:
			handleExit(a0); return 0; 
		
		case syscallCreate:
			return create(a0);
			
		case syscallOpen:
			return open(a0);

		case syscallRead:
			return read(a0, a1, a2);
			         
		case syscallWrite:
			return write(a0, a1, a2);

		case syscallClose:
			return close(a0);

		case syscallUnlink:
			return unlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}


	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			//Xiangqing's code
			handleExit(unhandledException);
			//Lib.assertNotReached("Unexpected exception");
			//Xiangqing's code ends
		}
	}
	//Hajara's code
	private int empty()
	{
        for (int i = 0; i < MAX; i++) 
        {
            if (file[i].file == null) 
                return i; 
        }
        return -1; 
    } 
	private int files(String filename) 
	{ 
        for (int i = 0; i < MAX; i++) 
        { 
        	Lib.debug(dbgProcess, file[i].filename);
            if (file[i].filename.equals(filename)) 
                return i; 
        }

        return -1; 
    }
	public static final int MAX = 16; 

	public static final int STDIN = 0; 

	public static final int STDOUT = 1; 
	public static final int MAXSTRLEN = 256; 
	private fileclass file[] = new fileclass[MAX]; 
	 
	public class fileclass 
	{ 
	    public fileclass() 
	    {
	    } 
	    private String filename = ""; 
	    private OpenFile file = null; 
	    private int pos = 0; 

	    private boolean removes = false;
	                                        
	}
	//Hajara's code ends

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;
	
	/************************* Xiangqing's Code**************************/
	//public static List<UserProcess> runningList = new LinkedList<UserProcess>();
	/** Unique ID of this process*/
	private int processID;
	
	/** The Counter of processID*/
	protected static int processIDCounter = 0;
	protected static Lock processIDLock = new Lock();
	
	/** The list of OpenFiles*/
	protected List<OpenFile> fileList;
	
	/** The list of child processes*/
	protected List<UserProcess> childList;

	/** The parent process*/
	protected UserProcess parent;
	
	/** The thread of this process*/
	protected UThread thread;
	
	/** The map of child process ID to its exit status*/
	protected Map<Integer, Integer> exitStatusMap;
	protected Lock exitStatusMapLock;
	protected static final int unhandledException = -38429057;
	//public int exitStatus;
	//public Semaphore joinSemaphore = new Semaphore();
	/************************* Xiangqing's Code ends*********************/
	/************************* Harsh's Test Code *************************/
	// Test Code Starts here
	public static void selfTest(){
		boolean pass = true;
		
		//Dodge up a page table and try doing a read/write on it's memory:
		UserProcess dummy = new UserProcess();
		dummy.pageTable = new TranslationEntry[2];
		dummy.pageTable[0] = new TranslationEntry(0,1,true,false,false,false);
		dummy.pageTable[1] = new TranslationEntry(1,0,true,false,false,false);
		
		//Now try a read/write on this stuff over the boundary of the pages:
		int vaddr = Processor.makeAddress(1,0) - 1;
		int wint = 0x012345678;
		int rint = 0;
		byte[] wbuffer = new byte[4];
		byte[] rbuffer = new byte[4];
		
		Lib.bytesFromInt(wbuffer,0,wint);
		dummy.writeVirtualMemory(vaddr, wbuffer);
		dummy.readVirtualMemory(vaddr, rbuffer);
		
		rint = Lib.bytesToInt(rbuffer, 0);
		if(rint!=wint){
			pass = false;
			System.err.println("FAIL: Read/Write failed to virtual memory!");
		}
		
		//Check that the information was written to the correct places!
		byte[] memory = Machine.processor().getMemory();
		rbuffer[0] = memory[Processor.makeAddress(2,0)-1];
		rbuffer[1] = memory[Processor.makeAddress(0,0)];
		rbuffer[2] = memory[Processor.makeAddress(0,1)];
		rbuffer[3] = memory[Processor.makeAddress(0,2)];
		
		rint = Lib.bytesToInt(rbuffer, 0);
		if(rint!=wint){
			pass = false;
			System.err.println("FAIL: Read/Write performed on wrong physical memory!");
		}
		
		//Test loading a certain number of pages:
		int pagesBefore = UserKernel.getKernel().getFreePhyMemBlocks();
		dummy.load("halt.coff",new String[]{});
		int pagesAfter = UserKernel.getKernel().getFreePhyMemBlocks();
		
		if(pagesAfter != pagesBefore - dummy.numPages){		   
			pass = false;
			System.err.println("FAIL: Failed to load the correct number of pages from Coff!");
		}
		
		//Test loading a huge process (should fail):
		if(dummy.load("huge.coff",new String[]{})){
			pass = false;
			System.err.println("FAIL: Reported successfull load of a HUGE coff file");
		}
		//dummy.handleExit(0);
		if(pass){
			System.out.println("->UserProcess tests completed successfully!");
		}else{
			System.err.println("Overall Failure of user process tests");
		}
	}
	/**************************Harsh's Test Code ends**********************/
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
