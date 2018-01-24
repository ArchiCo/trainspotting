package trainspotting;
import TSim.*;
import java.util.concurrent.Semaphore;

public class Lab1 {
	private int maxspeed = 19;
	Semaphore[] semaphores = new Semaphore[6];
	
	/*
	 *  The critical sections are as we discussed:
	 *	1-2. Northern and Southern stations: the semaphore's to control the switch as to prevent the trains from entering a lane that's already occupied by a train.
	 *	3-4. Northern and Southern single lanes that go from the station switches, 'till the middle fast lane switch: these sections are critical, because trains can't enter and pass them simultaneously without causing a collision. One train would have to wait at either side of this 'path' (either waiting on the station lane next to the switch, or in the middle lane before the crossing train passes.
	 *	5. Middle "fast" lane: if one's occupied, then the second train needs to be redirected to the other lane. Interestingly enough, if one train passes into the 'one lane' section after crossing the switch, then the previous behavior (3-4) will be the one governing the first train's stop.
	 *	6. Crossroad between northern train station lanes: one waits, while the other one passes.
	 */			
			
  public Lab1(Integer speed1, Integer speed2){
    TSimInterface tsi = TSimInterface.getInstance();
    try {
    	
    	Train train1 = new Train(1, tsi);
    	Train train2 = new Train(2, tsi);
    	train1.setSpeed(speed1);
    	train2.setSpeed(speed2);
    	train1.start();
    	train2.start();
   }
    catch (CommandException e) {
      e.printStackTrace();    // or only e.getMessage() for the error
      System.exit(1);
    }
  }
  
  class Train extends Thread {
	  private int trainId;
	  private int speed;
	  private TSimInterface tsi;
	  
	  public Train(int id, TSimInterface tsi) {
		  this.trainId = id;
		  this.tsi = tsi;
	  }
	  
	  public void setSpeed(int speed) throws CommandException {
		  this.speed = speed;
		  tsi.setSpeed(trainId, speed);
	  }
	  
	  public int getStatus() {
		  if (speed > 0) { 
			  return  1; } // If train's moving forward
		  else if (speed < 0) { 
			  return -1; } // If train's moving backwards
		  else {
			  return 0;    // If train's stopped
		  }
	  }
	  
	  public void run() {
		  while (true) {
			  try {
				SensorEvent sensor = tsi.getSensor(trainId);
				
							
			} catch (CommandException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		  }
	  }  
  }
}
