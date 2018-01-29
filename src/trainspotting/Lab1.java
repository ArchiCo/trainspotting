package trainspotting;

import TSim.*;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Lab1 {
	Semaphore[] semaphore = new Semaphore[6];
	public Lab1(Integer speed1, Integer speed2) {
		TSimInterface tsi = TSimInterface.getInstance();
		int trainId1 = 1, trainId2 = 2;
		for (int i = 0; i < 6; i++) {
			semaphore[i] = new Semaphore(1); // Initializing semaphores in a binary state
		}
		// Initializing instances of 2 trains which are defined as separate threads and starting them
		Train train1 = new Train(trainId1, speed1, tsi, Direction.SOUTH);
		Train train2 = new Train(trainId2, speed2, tsi, Direction.NORTH);
		train1.start();
		train2.start();
	}
	
	class Train extends Thread {
		private final int TRAIN_ID, MAXSPEED = 20;
		private int speed;
		private Direction movementDirection;
		private SensorEvent previousSensorEvent;
		private TSimInterface tsi;
		private ArrayList <Semaphore> locks = new ArrayList<Semaphore>();

		public Train(int trainId, int speed, TSimInterface tsi, Direction direction) {
			TRAIN_ID = trainId;
			this.tsi = tsi;
			movementDirection = direction;
			this.speed = speed;
		}

		public void run() {
			try {
				  tsi.setSpeed(TRAIN_ID, speed);
				  while (true) {
					  SensorEvent currentSensorEvent = tsi.getSensor(TRAIN_ID);
					  // Confirm if the latest sensor that the train've passed is a new sensor.
					  if (previousSensorEvent == null || 
						  currentSensorEvent.getXpos() != previousSensorEvent.getXpos() ||
						  currentSensorEvent.getYpos() != previousSensorEvent.getYpos()) {
						  // If so, proceed to assessing sensors
						  sensorActivity(currentSensorEvent.getXpos(), currentSensorEvent.getYpos());
						  previousSensorEvent = currentSensorEvent;
					  }
				  }
			} catch (CommandException | InterruptedException e) {
				e.printStackTrace();
			}
	  }
		

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
		
		/**
		 * Method that is used for handling train's and railroad' element's behaviors.
		 * Behavior execution is dependent on the latest active sensor (derived from 
		 * SensorEvent in the main loop and passed as x & y coordinates to be compared 
		 * against enumerated table of sensor elements) and train's movement direction 
		 * (traveling to North or South station).
		 * @param xPos x-Position of the latest active sensor responsible for the event
		 * @param yPos y-Position of the latest active sensor responsible for the event
		 * @throws CommandException
		 * @throws InterruptedException
		 */
		
		private void sensorActivity(int xPos, int yPos) throws CommandException, InterruptedException{
			Sensor activeSensor = getSensor(xPos, yPos);
			if (activeSensor != null) {
				int switchDirection;
				if (movementDirection == Direction.SOUTH) {
					switch(activeSensor) {
					case NORTH_STATION_UP: case NORTH_STATION_DOWN:
						acquire(Semaphores.NORTH_STATION.index);
						break;
						
					case WEST_CROSSROAD: case NORTH_CROSSROAD:
						acquire(Semaphores.CROSSROAD.index);
						break;
						
					case EAST_CROSSROAD: case SOUTH_CROSSROAD:
						releaseLock(Semaphores.CROSSROAD.index);
						break;
					
					case NORTH_STATION_LINE_UP: case NORTH_STATION_LINE_DOWN:
						acquire(Semaphores.NORTH_SINGLE_LANE.index);
						switchDirection = (activeSensor == Sensor.NORTH_STATION_LINE_UP) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.NORTH_STATION.xPos, Switch.NORTH_STATION.yPos, switchDirection);
						break;
					
					case NORTH_SINGLE_LINE:
						if (semaphore[Semaphores.FAST_MIDDLE_LANE.index].tryAcquire()) {
							locks.add(semaphore[Semaphores.FAST_MIDDLE_LANE.index]);
							tsi.setSwitch(Switch.MIDDLE_LINE_EAST.xPos, Switch.MIDDLE_LINE_EAST.yPos, TSimInterface.SWITCH_RIGHT);
						} else {
							tsi.setSwitch(Switch.MIDDLE_LINE_EAST.xPos, Switch.MIDDLE_LINE_EAST.yPos, TSimInterface.SWITCH_LEFT);
						}
						releaseLock(Semaphores.NORTH_STATION.index);
						break;
						
					case MIDDLE_LINE_NE: case MIDDLE_LINE_SE:
						releaseLock(Semaphores.NORTH_SINGLE_LANE.index);
						break;
						
					case MIDDLE_LINE_NW: case MIDDLE_LINE_SW:
						acquire(Semaphores.SOUTH_SINGLE_LANE.index);
						switchDirection = (activeSensor == Sensor.MIDDLE_LINE_NW) ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT;
						tsi.setSwitch(Switch.MIDDLE_LINE_WEST.xPos, Switch.MIDDLE_LINE_WEST.yPos, switchDirection);
						break;
						
					case SOUTH_SINGLE_LINE:
						if (semaphore[Semaphores.SOUTH_STATION.index].tryAcquire()) {
							locks.add(semaphore[Semaphores.SOUTH_STATION.index]);
							tsi.setSwitch(Switch.SOUTH_STATION.xPos, Switch.SOUTH_STATION.yPos, TSimInterface.SWITCH_LEFT);
						} else {
							tsi.setSwitch(Switch.SOUTH_STATION.xPos, Switch.SOUTH_STATION.yPos, TSimInterface.SWITCH_RIGHT);
						}
						releaseLock(Semaphores.FAST_MIDDLE_LANE.index);
						break;
						
					case SOUTH_STATION_LINE_UP: case SOUTH_STATION_LINE_DOWN:
						releaseLock(Semaphores.SOUTH_SINGLE_LANE.index);
						break;
						
					case SOUTH_STATION_UP: case SOUTH_STATION_DOWN:
						stationBehavior();
						break;	
						
					default:
						break;
					} 
				
				} else if (movementDirection == Direction.NORTH){
					switch(activeSensor) {
					case SOUTH_STATION_UP: case SOUTH_STATION_DOWN:
						acquire(Semaphores.SOUTH_STATION.index);
						break;
					
					case SOUTH_STATION_LINE_UP: case SOUTH_STATION_LINE_DOWN:
						acquire(Semaphores.SOUTH_SINGLE_LANE.index);
						switchDirection = (activeSensor == Sensor.SOUTH_STATION_LINE_DOWN) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.SOUTH_STATION.xPos, Switch.SOUTH_STATION.yPos, switchDirection);
						break;
						
					case SOUTH_SINGLE_LINE:
						if (semaphore[Semaphores.FAST_MIDDLE_LANE.index].tryAcquire()) {
							locks.add(semaphore[Semaphores.FAST_MIDDLE_LANE.index]);
							tsi.setSwitch(Switch.MIDDLE_LINE_WEST.xPos, Switch.MIDDLE_LINE_WEST.yPos, TSimInterface.SWITCH_LEFT);
						} else {
							tsi.setSwitch(Switch.MIDDLE_LINE_WEST.xPos, Switch.MIDDLE_LINE_WEST.yPos, TSimInterface.SWITCH_RIGHT);
						}
						releaseLock(Semaphores.SOUTH_STATION.index);
						break;
						
					case MIDDLE_LINE_NW: case MIDDLE_LINE_SW:
						releaseLock(Semaphores.SOUTH_SINGLE_LANE.index);
						break;	
					
					case MIDDLE_LINE_NE: case MIDDLE_LINE_SE:
						acquire(Semaphores.NORTH_SINGLE_LANE.index);
						switchDirection = (activeSensor == Sensor.MIDDLE_LINE_NE) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.MIDDLE_LINE_EAST.xPos, Switch.MIDDLE_LINE_EAST.yPos, switchDirection);
						break;	
						
					case NORTH_SINGLE_LINE:
						if (semaphore[Semaphores.NORTH_STATION.index].tryAcquire()) {
							locks.add(semaphore[Semaphores.NORTH_STATION.index]);
							tsi.setSwitch(Switch.NORTH_STATION.xPos, Switch.NORTH_STATION.yPos, TSimInterface.SWITCH_RIGHT);
						} else {
							tsi.setSwitch(Switch.NORTH_STATION.xPos, Switch.NORTH_STATION.yPos, TSimInterface.SWITCH_LEFT);
						}
						releaseLock(Semaphores.FAST_MIDDLE_LANE.index);
						break;
					
					case NORTH_STATION_LINE_UP: case NORTH_STATION_LINE_DOWN:
						releaseLock(Semaphores.NORTH_SINGLE_LANE.index);
						break;
						
					case EAST_CROSSROAD: case SOUTH_CROSSROAD:
						acquire(Semaphores.CROSSROAD.index);
						break;
						
					case WEST_CROSSROAD: case NORTH_CROSSROAD:
						releaseLock(Semaphores.CROSSROAD.index);
						break;
						
					case NORTH_STATION_UP: case NORTH_STATION_DOWN:
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
			//Thread.sleep(1000 + (20 * Math.abs(speed)));
			Thread.sleep(100);
			movementDirection = (movementDirection == Direction.NORTH) ? Direction.SOUTH : Direction.NORTH;
			speed = -speed;
			tsi.setSpeed(TRAIN_ID, speed);
		}
	
		private void acquire(int section) throws InterruptedException, CommandException {
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
	
	public enum Semaphores{
		CROSSROAD(0), NORTH_STATION(1), NORTH_SINGLE_LANE(2), FAST_MIDDLE_LANE(3), SOUTH_SINGLE_LANE(4), SOUTH_STATION(5);
		private int index;
		private Semaphores(int index) {this.index = index;}
	}
	
	public enum Direction{NORTH, SOUTH}; // Directional enumerators
	
	public enum Sensor { // Sensor enumerators corresponding to the map sensors
		// Station sensors
		NORTH_STATION_UP(15, 3), NORTH_STATION_DOWN(15, 5), 
		SOUTH_STATION_UP(15,11), SOUTH_STATION_DOWN(15,13),
		// Crossroad sensors
		NORTH_CROSSROAD(8,5), SOUTH_CROSSROAD(10,8), 
		WEST_CROSSROAD (6,7), EAST_CROSSROAD (10,7),
		// Station lane sensors
		NORTH_STATION_LINE_UP(14, 7), NORTH_STATION_LINE_DOWN(14, 8), 
		SOUTH_STATION_LINE_UP( 6,11), SOUTH_STATION_LINE_DOWN( 4,13), 
		// Middle lane sensors
		MIDDLE_LINE_NW( 7, 9), MIDDLE_LINE_SW( 7,10), 
		MIDDLE_LINE_NE(12, 9), MIDDLE_LINE_SE(12,10),
		// Single Lane sensors
		SOUTH_SINGLE_LINE ( 1, 9), NORTH_SINGLE_LINE (19, 9);
		private int xPos, yPos;
		private Sensor(int xPos, int yPos) { this.xPos = xPos; this.yPos = yPos; }
	}
	
	public enum Switch { // Switch enumerators
		NORTH_STATION(17,7), SOUTH_STATION(3,11), 
		MIDDLE_LINE_WEST(4,9), MIDDLE_LINE_EAST(15,9);
		private int xPos, yPos;
		private Switch(int xPos, int yPos) { this.xPos = xPos; this.yPos = yPos; }
	}
}