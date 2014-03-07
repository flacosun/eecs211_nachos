package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;
	/*----------------------------------------Xiangqing's Code starts here-----------------------------*/
	private final static int OAHU = 0;
	private final static int MOLOKAI = 1;
	private static int[] numOfChildrenOnIsland = {0, 0};
	private static int[] numOfAdultsOnIsland = {0, 0};
	private static int locationOfBoat = OAHU;
	private static int numOfBoardingChildren = 0;
	private static Lock boat = new Lock();
	private static Condition waitForBoarding = new Condition(boat);
	private static Condition waitOnOahu = new Condition(boat);
	private static Condition waitOnMolokai = new Condition(boat);
	private static Communicator message = new Communicator();
	
	/*----------------------------------------Xiangqing's Code ends here-------------------------------*/

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		//System.out.println("\n ***Testing Boats with only 2 children***");
		//begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//begin(1, 2, b);

		 System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		 begin(3, 3, b);
	}

 	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;
		

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		// Hajara's Code
		Runnable child= new Runnable() {
			public void run() {
				ChildItinerary();
				//yield();
				//SampleItinerary();
			}
		};
		for(int i=1; i<= children;i++)
		{
			KThread t = new KThread(child);
			t.setName("Child Thread"+ i);
			t.fork();
			
		}
		Runnable adult= new Runnable() {
			public void run() {
				AdultItinerary();
				//SampleItinerary();
			}
		};
		
		for(int i=1; i<= adults;i++)
		{
			KThread t = new KThread(adult);
			t.setName("Adult Thread"+ i);
			t.fork();
			
			
		}
		// Code ends here.
		/*----------------------------------------Xiangqing's Code starts here-----------------------------*/
		int total = children + adults;
		//child.run();
		while(message.listen() != total );
		/*----------------------------------------Xiangqing's Code ends here-------------------------------*/
 	}


 	static void AdultItinerary()
	{
		
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */

		// Hajara's code
		int location=OAHU;
		numOfAdultsOnIsland[OAHU]++;
		boat.acquire();
		while(locationOfBoat!=OAHU||numOfChildrenOnIsland[OAHU]>1||numOfBoardingChildren>0)
		{
			if(locationOfBoat==OAHU)
				waitOnOahu.wakeAll();
			waitOnOahu.sleep();               
		}
		bg.AdultRowToMolokai();
		numOfAdultsOnIsland[OAHU]--;
		numOfAdultsOnIsland[MOLOKAI]++;
		locationOfBoat = MOLOKAI;
		location=MOLOKAI;
		message.speak(numOfAdultsOnIsland[MOLOKAI]+numOfChildrenOnIsland[MOLOKAI]);		 
		waitOnMolokai.wakeAll();
        // waitOnMolokai.sleep();
		// Code ends here 
				
		
		boat.release();
  	}

	
	/*----------------------------------------Xiangqing's Code starts here-----------------------------*/
	static void ChildItinerary() {
		int location = OAHU; 
		numOfChildrenOnIsland[OAHU]++;
		while(true){
			boat.acquire();
			if(location == OAHU){
				while(locationOfBoat != OAHU || numOfBoardingChildren == 2 || numOfChildrenOnIsland[OAHU] == 1){
					if(locationOfBoat == OAHU)
						waitOnOahu.wakeAll();
					waitOnOahu.sleep();
				}
				numOfBoardingChildren++;
				if(numOfBoardingChildren == 1){
					waitOnOahu.wakeAll();
					waitForBoarding.sleep();
					numOfChildrenOnIsland[OAHU]--;
					bg.ChildRideToMolokai();
					locationOfBoat = MOLOKAI;
					location = MOLOKAI;
					numOfBoardingChildren = 0;
					numOfChildrenOnIsland[MOLOKAI]++;
					message.speak(numOfChildrenOnIsland[MOLOKAI] + numOfAdultsOnIsland[MOLOKAI]);
					waitOnMolokai.wakeAll();
					waitOnMolokai.sleep();
				}
				else{
					waitForBoarding.wake();
					numOfChildrenOnIsland[OAHU]--;
					bg.ChildRowToMolokai();
					location = MOLOKAI;
					numOfChildrenOnIsland[MOLOKAI]++;
					waitOnMolokai.sleep();
				}
			}
			else{
				while(locationOfBoat != MOLOKAI)
					waitOnMolokai.sleep();
				numOfChildrenOnIsland[MOLOKAI]--;
				bg.ChildRowToOahu();
				location = OAHU;
				locationOfBoat = OAHU;
				numOfChildrenOnIsland[OAHU]++;
				waitOnOahu.wakeAll();
				waitOnOahu.sleep();
			}
			boat.release();
		}
	}
	/*----------------------------------------Xiangqing's Code ends here-------------------------------*/
	
	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		boat.acquire();
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
		boat.release();
	}

}
