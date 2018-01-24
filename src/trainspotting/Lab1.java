package trainspotting;
import TSim.*;
import java.util.concurrent.Semaphore;

public class Lab1 {
	private int maxspeed = 19;
	Semaphore[] semaphores = new Semaphore[6];
	
	/*
	 * The given map has six critical points that trains need to mind to prevent the collision:
	 * 2x Station lanes
	 */
	
	// Semaphore 0-1: Station 1 & 2 -- blocks the switch, preventing the other train from entering the lane
	// Semaphore 2-3: One lane passages after stations	
	// Semaphore   4: Middle lane / fast passage
	// Semaphore   5: Crossroads

			
			
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
