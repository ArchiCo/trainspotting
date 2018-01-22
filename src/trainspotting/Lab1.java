package trainspotting;
import TSim.*;

public class Lab1 {

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
	  private int id;
	  private int speed;
	  private TSimInterface tsi;
	  
	  public Train(int id, TSimInterface tsi) {
		  this.id = id;
		  this.tsi = tsi.getInstance();
	  }
	  
	  public void setSpeed(int speed) throws CommandException {
		  this.speed = speed;
		  tsi.setSpeed(id, speed);
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
			  
		  }
	  }  
  }
}
