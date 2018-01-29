package trainspotting;

import TSim.*;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Lab1 {
	Semaphore[] semaphore = new Semaphore[6];

	/*
	 * The critical sections are as we discussed: 1-2. Northern and Southern
	 * stations: the semaphore's to control the switch as to prevent the trains
	 * from entering a lane that's already occupied by a train. 3-4. Northern
	 * and Southern single lanes that go from the station switches, 'till the
	 * middle fast lane switch: these sections are critical, because trains
	 * can't enter and pass them simultaneously without causing a collision. One
	 * train would have to wait at either side of this 'path' (either waiting on
	 * the station lane next to the switch, or in the middle lane before the
	 * crossing train passes. 5. Middle "fast" lane: if one's occupied, then the
	 * second train needs to be redirected to the other lane. Interestingly
	 * enough, if one train passes into the 'one lane' section after crossing
	 * the switch, then the previous behavior (3-4) will be the one governing
	 * the first train's stop. 6. Crossroad between northern train station
	 * lanes: one waits, while the other one passes.
	 */

	public Lab1(Integer speed1, Integer speed2) {
		TSimInterface tsi = TSimInterface.getInstance();
		for (int i = 0; i < 6; i++) {
			semaphore[i] = new Semaphore(1);
		}
		Train train1 = new Train(1, speed1, tsi, Direction.SOUTH);
		Train train2 = new Train(2, speed2, tsi, Direction.NORTH);
		train1.start();
		train2.start();
	}
	
	class Train extends Thread {
		private final int TRAIN_ID;
		private final int MAXSPEED = 20;
		private int speed;
		private Direction direction;
		private SensorEvent previousEvent;
		private TSimInterface tsi;
		private ArrayList <Semaphore> locks = new ArrayList<Semaphore>();

		public Train(int trainId, int speed, TSimInterface tsi, Direction direction) {
			TRAIN_ID = trainId;
			this.tsi = tsi;
			this.direction = direction;
			this.speed = speed;
		}

		public void run() {
			try {
				  tsi.setSpeed(TRAIN_ID, speed);
				  while (true) {
					  SensorEvent currentEvent = tsi.getSensor(TRAIN_ID);
					  if (previousEvent == null || 
						  currentEvent.getXpos() != previousEvent.getXpos() ||
						  currentEvent.getYpos() != previousEvent.getYpos()) {
						  
						  sensorActivity(currentEvent.getXpos(), currentEvent.getYpos(), direction);
						  previousEvent = currentEvent;
					  }
				  }
			} catch (CommandException | InterruptedException e) {
				e.printStackTrace();
			}
	  }
		
		private void sensorActivity(int xPos, int yPos, Direction direction) throws CommandException, InterruptedException{
			Sensor activeSensor = getSensor(xPos, yPos);
			int switchTo;
			if (activeSensor != null) {
				if (this.direction == Direction.SOUTH) {
					switch(activeSensor) {
					case N_STATION_N: case N_STATION_S:
						gainPriority(1);
						break;
						
					case W_CROSSROAD: case N_CROSSROAD:
						gainPriority(0);
						break;
						
					case E_CROSSROAD: case S_CROSSROAD:
						releaseLock(0);
						break;
					
					case N_STATION_LN: case N_STATION_LS:
						gainPriority(2);
						switchTo = (activeSensor == Sensor.N_STATION_LN) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.N_STATION.xPos, Switch.N_STATION.yPos, switchTo);
						break;
					
					case SINGLE_LE:
						if (semaphore[3].tryAcquire()) {
							locks.add(semaphore[3]);
							tsi.setSwitch(Switch.MIDDLE_E.xPos, Switch.MIDDLE_E.yPos, TSimInterface.SWITCH_RIGHT);
						} else {
							tsi.setSwitch(Switch.MIDDLE_E.xPos, Switch.MIDDLE_E.yPos, TSimInterface.SWITCH_LEFT);
						}
						releaseLock(1);
						break;
						
					case MIDDLE_NE: case MIDDLE_SE:
						releaseLock(2);
						break;
						
					case MIDDLE_NW: case MIDDLE_SW:
						gainPriority(4);
						switchTo = (activeSensor == Sensor.MIDDLE_NW) ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT;
						tsi.setSwitch(Switch.MIDDLE_W.xPos, Switch.MIDDLE_W.yPos, switchTo);
						break;
						
					case SINGLE_LW:
						if (semaphore[5].tryAcquire()) {
							locks.add(semaphore[5]);
							tsi.setSwitch(Switch.S_STATION.xPos, Switch.S_STATION.yPos, TSimInterface.SWITCH_LEFT);
						} else {
							tsi.setSwitch(Switch.S_STATION.xPos, Switch.S_STATION.yPos, TSimInterface.SWITCH_RIGHT);
						}
						releaseLock(3);
						break;
						
					case S_STATION_LN: case S_STATION_LS:
						releaseLock(4);
						break;
						
					case S_STATION_N: case S_STATION_S:
						stationBehavior();
						break;	
						
					default:
						break;
					} 
				
				} else if (this.direction == Direction.NORTH){
					switch(activeSensor) {
					case S_STATION_N: case S_STATION_S:
						gainPriority(5);
						break;
					
					case S_STATION_LN: case S_STATION_LS:
						gainPriority(4);
						switchTo = (activeSensor == Sensor.S_STATION_LS) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.S_STATION.xPos, Switch.S_STATION.yPos, switchTo);
						break;
						
					case SINGLE_LW:
						if (semaphore[3].tryAcquire()) {
							locks.add(semaphore[3]);
							tsi.setSwitch(Switch.MIDDLE_W.xPos, Switch.MIDDLE_W.yPos, TSimInterface.SWITCH_LEFT);
						} else {
							tsi.setSwitch(Switch.MIDDLE_W.xPos, Switch.MIDDLE_W.yPos, TSimInterface.SWITCH_RIGHT);
						}
						releaseLock(5);
						break;
						
					case MIDDLE_NW: case MIDDLE_SW:
						releaseLock(4);
						break;	
					
					case MIDDLE_NE: case MIDDLE_SE:
						gainPriority(2);
						switchTo = (activeSensor == Sensor.MIDDLE_NE) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.MIDDLE_E.xPos, Switch.MIDDLE_E.yPos, switchTo);
						break;	
						
					case SINGLE_LE:
						if (semaphore[1].tryAcquire()) {
							locks.add(semaphore[1]);
							tsi.setSwitch(Switch.N_STATION.xPos, Switch.N_STATION.yPos, TSimInterface.SWITCH_RIGHT);
						} else {
							tsi.setSwitch(Switch.N_STATION.xPos, Switch.N_STATION.yPos, TSimInterface.SWITCH_LEFT);
						}
						releaseLock(3);
						break;
					
					case N_STATION_LN: case N_STATION_LS:
						releaseLock(2);
						break;
						
					case E_CROSSROAD: case S_CROSSROAD:
						gainPriority(0);
						break;
						
					case W_CROSSROAD: case N_CROSSROAD:
						releaseLock(0);
						break;
						
					case N_STATION_N: case N_STATION_S:
						stationBehavior();
						break;
						
					default:
						break;
					}
				}
			}
		}
		
		private Sensor getSensor(int xPos, int yPos) {
			for(Sensor s : Sensor.values()) {
				if (s.xPos == xPos && s.yPos == yPos) {
					return s;
				}
			}
			return null;
		}
		
		private void stationBehavior() throws CommandException, InterruptedException {
			tsi.setSpeed(TRAIN_ID, 0);
			Thread.sleep(1000 + (20 * Math.abs(speed)));
			direction = (direction == Direction.NORTH) ? Direction.SOUTH : Direction.NORTH;
			speed = -speed;
			tsi.setSpeed(TRAIN_ID, speed);
		}
	
		private void gainPriority(int section) throws InterruptedException, CommandException {
			if (!locks.contains(semaphore[section])) {
				tsi.setSpeed(TRAIN_ID, 0);
				semaphore[section].acquire();
				locks.add(semaphore[section]);
				tsi.setSpeed(TRAIN_ID, speed);
			}
		}
		
		private void releaseLock(int section) {
			if(locks.contains(semaphore[section])) {
				locks.remove(semaphore[section]);
				semaphore[section].release();
			}
		}
	}
	
	public enum Direction{NORTH, SOUTH}; // Directional enumerators
	
	public enum Sensor { // Sensor enumerators corresponding to the map sensors
		N_STATION_N (15, 3), N_STATION_S (15, 5), S_STATION_N (15,11), S_STATION_S (15,13), // Station sensors
		N_CROSSROAD ( 8, 5), S_CROSSROAD (10, 8), W_CROSSROAD ( 6, 7), E_CROSSROAD (10, 7), // Crossroad sensors
		N_STATION_LN(14, 7), N_STATION_LS(14, 8), S_STATION_LN( 6,11), S_STATION_LS( 4,13), // Station lane sensors
		MIDDLE_NW ( 7, 9), MIDDLE_SW ( 7,10), MIDDLE_NE (12, 9), MIDDLE_SE (12,10), // Middle lane sensors
		SINGLE_LW ( 1, 9), SINGLE_LE (19, 9); // Single Lane sensors
		private int xPos, yPos;
		private Sensor(int xPos, int yPos) { this.xPos = xPos; this.yPos = yPos; }
	}
	
	public enum Switch { // Switch enumerators
		N_STATION (17, 7), S_STATION ( 3,11), MIDDLE_W ( 4, 9), MIDDLE_E (15, 9);
		private int xPos, yPos;
		private Switch(int xPos, int yPos) { this.xPos = xPos; this.yPos = yPos; }
	}
}