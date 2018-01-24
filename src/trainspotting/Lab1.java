package trainspotting;
import TSim.*;
import java.util.concurrent.Semaphore;

public class Lab1 {
	Semaphore[] semaphore = new Semaphore[6];
	
	/*
	 *  The critical sections are as we discussed:
	 *	1-2. Northern and Southern stations: the semaphore's to control the switch as to prevent the trains from entering a lane that's already occupied by a train.
	 *	3-4. Northern and Southern single lanes that go from the station switches, 'till the middle fast lane switch: these sections are critical, because trains can't enter and pass them simultaneously without causing a collision. One train would have to wait at either side of this 'path' (either waiting on the station lane next to the switch, or in the middle lane before the crossing train passes.
	 *	5. Middle "fast" lane: if one's occupied, then the second train needs to be redirected to the other lane. Interestingly enough, if one train passes into the 'one lane' section after crossing the switch, then the previous behavior (3-4) will be the one governing the first train's stop.
	 *	6. Crossroad between northern train station lanes: one waits, while the other one passes.
	 */			
			
  public Lab1(Integer speed1, Integer speed2){
    TSimInterface tsi = TSimInterface.getInstance();
    for (int i = 0; i < 6; i++) {
    	semaphore[i] = new Semaphore(1);
    }
    try {
    Train train1 = new Train(1, speed1, tsi);
	Train train2 = new Train(2, speed2, tsi);
	train1.start();
	train2.start();
    } catch (CommandException e) {
    	e.printStackTrace();
    	System.exit(0);
    }
  }
  
  class Train extends Thread {
	  private int maxSpeed = 15;
	  private boolean traveling = true;
	  private int trainId;
	  private int speed;
	  private TSimInterface tsi;
	  
	  public Train(int id, int speed, TSimInterface tsi) throws CommandException{
		  this.trainId = id;
		  this.tsi = tsi;
		  maxSpeed = speed;
		  setSpeed(speed);
	  }
	  
	  public void setSpeed(int speed) throws CommandException {
		  this.speed = speed;
		  tsi.setSpeed(trainId, speed);
		
	  }
	  
	  private int getStatus() {
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
				switch(Integer.toString(sensor.getXpos()) + "," +
				       Integer.toString(sensor.getYpos())) {
				
				// Train stops, waits, and reverses movement. Falls through for all conditions.
				case "16,3":
				case "16,5":
				case "16,11":
				case "16,13":
					if (sensor.getStatus() == 1 && traveling == true) {
						int status = getStatus() * -1;
						setSpeed(0);
						Thread.sleep(1000 + (20 * speed));
						traveling = false;
						setSpeed(maxSpeed * status);
					} else if (sensor.getStatus() == 2 && traveling == false) {
						traveling = true;
					}
					break;
				
				// Crossroad cases
				case "6,7":
					//semaphore[5].tryAcquire();
					break;
				case "10,7":
					break;
				case "8,5":
					break;
				case "10,8":
					break;
				
				case "3,14": // SSL & OL2
					break;
				case "5,11": // SSL & OL2
					break;
				case "5,9":  // OL2 & Middle
					break;
				case "5,10": // OL2 & Middle
					break;
				
				case "15,7": // NSL & OL1
					break;
				case "15,8": // NSL & OL1
					break;
				case "14,9": // OL1 & Middle
					break;
				case "14,10":// OL1 & Middle
					break;
				
				}
							
			} catch (CommandException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		  }
	  }  
  }
}
