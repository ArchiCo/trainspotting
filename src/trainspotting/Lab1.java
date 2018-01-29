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
		private final int TRAIN_ID, MIN_SPEED = 1, MAX_SPEED = 22;
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
				  setSpeed(speed);
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
		 * The critical sections are as we discussed: 
		 * 
		 * 0. Crossroad between northern train station lanes: one waits, while the other 
		 * one passes.
		 * 
		 * 1 & 5. Northern and Southern stations: the semaphore's to control the switch as
		 * to prevent the trains from entering a lane that's already occupied by a train. 
		 * 
		 * 2 & 4. Northern and Southern single lanes that go from the station switches,´till
		 * the middle fast lane switch: these sections are critical, because trains can´t
		 * enter and pass them simultaneously without causing a collision. One train would
		 * have to wait at either side of this 'path' (either waiting on the station lane
		 * next to the switch, or in the middle lane before the crossing train passes. 
		 * 
		 * 3. Middle "fast" lane: if one's occupied, then the second train needs to be 
		 * redirected to the other lane. Interestingly enough, if one train passes into 
		 * the 'one lane' section after crossing the switch, then the previous behavior 
		 * (3d-4) will be the one governing the first train's stop.
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
					case STATION_NN: case STATION_NS:
						acquirePriority(Control.STATION_LANE_NN.node);
						break;
						
					case CROSSROAD_W: case CROSSROAD_N:
						acquirePriority(Control.CROSSROAD.node);
						break;
						
					case CROSSROAD_E: case CROSSROAD_S:
						releaseLock(Control.CROSSROAD.node);
						break;
					
					case STATION_LANE_NN: case STATION_LANE_NS:
						acquirePriority(Control.SINGLE_LANE_N.node);
						switchDirection = (activeSensor == Sensor.STATION_LANE_NN) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.STATION_N.xPos, Switch.STATION_N.yPos, switchDirection);
						break;
					
					case SINGLE_LANE_N:
						if (Control.FAST_LANE.node.tryAcquire()) {
							locks.add(Control.FAST_LANE.node);
							tsi.setSwitch(Switch.MIDDLE_LANE_E.xPos, Switch.MIDDLE_LANE_E.yPos, TSimInterface.SWITCH_RIGHT);
						} else {
							tsi.setSwitch(Switch.MIDDLE_LANE_E.xPos, Switch.MIDDLE_LANE_E.yPos, TSimInterface.SWITCH_LEFT);
						}
						releaseLock(Control.STATION_LANE_NN.node);
						break;
						
					case MIDDLE_LANE_NE: case MIDDLE_LANE_SE:
						releaseLock(Control.SINGLE_LANE_N.node);
						break;
						
					case MIDDLE_LANE_NW: case MIDDLE_LANE_SW:
						acquirePriority(Control.SINGLE_LANE_S.node);
						switchDirection = (activeSensor == Sensor.MIDDLE_LANE_NW) ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT;
						tsi.setSwitch(Switch.MIDDLE_LANE_W.xPos, Switch.MIDDLE_LANE_W.yPos, switchDirection);
						break;
						
					case SINGLE_LANE_S:
						if (Control.STATION_LANE_SN.node.tryAcquire()) {
							locks.add(Control.STATION_LANE_SN.node);
							tsi.setSwitch(Switch.STATION_S.xPos, Switch.STATION_S.yPos, TSimInterface.SWITCH_LEFT);
						} else {
							tsi.setSwitch(Switch.STATION_S.xPos, Switch.STATION_S.yPos, TSimInterface.SWITCH_RIGHT);
						}
						releaseLock(Control.FAST_LANE.node);
						break;
						
					case STATION_LANE_SN: case STATION_LANE_SS:
						releaseLock(Control.SINGLE_LANE_S.node);
						break;
						
					case STATION_SN: case STATION_SS:
						stationBehavior();
						break;	
						
					default:
						break;
					} 
				
				} else if (movementDirection == Direction.NORTH){
					switch(activeSensor) {
					case STATION_SN: case STATION_SS:
						acquirePriority(Control.STATION_LANE_SN.node);
						break;
					
					case STATION_LANE_SN: case STATION_LANE_SS:
						acquirePriority(Control.SINGLE_LANE_S.node);
						switchDirection = (activeSensor == Sensor.STATION_LANE_SS) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.STATION_S.xPos, Switch.STATION_S.yPos, switchDirection);
						break;
						
					case SINGLE_LANE_S:
						if (Control.FAST_LANE.node.tryAcquire()) {
							locks.add(Control.FAST_LANE.node);
							tsi.setSwitch(Switch.MIDDLE_LANE_W.xPos, Switch.MIDDLE_LANE_W.yPos, TSimInterface.SWITCH_LEFT);
						} else {
							tsi.setSwitch(Switch.MIDDLE_LANE_W.xPos, Switch.MIDDLE_LANE_W.yPos, TSimInterface.SWITCH_RIGHT);
						}
						releaseLock(Control.STATION_LANE_SN.node);
						break;
						
					case MIDDLE_LANE_NW: case MIDDLE_LANE_SW:
						releaseLock(Control.SINGLE_LANE_S.node);
						break;	
					
					case MIDDLE_LANE_NE: case MIDDLE_LANE_SE:
						acquirePriority(Control.SINGLE_LANE_N.node);
						switchDirection = (activeSensor == Sensor.MIDDLE_LANE_NE) ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
						tsi.setSwitch(Switch.MIDDLE_LANE_E.xPos, Switch.MIDDLE_LANE_E.yPos, switchDirection);
						break;	
						
					case SINGLE_LANE_N:
						if (Control.STATION_LANE_NN.node.tryAcquire()) {
							locks.add(Control.STATION_LANE_NN.node);
							tsi.setSwitch(Switch.STATION_N.xPos, Switch.STATION_N.yPos, TSimInterface.SWITCH_RIGHT);
						} else {
							tsi.setSwitch(Switch.STATION_N.xPos, Switch.STATION_N.yPos, TSimInterface.SWITCH_LEFT);
						}
						releaseLock(Control.FAST_LANE.node);
						break;
					
					case STATION_LANE_NN: case STATION_LANE_NS:
						releaseLock(Control.SINGLE_LANE_N.node);
						break;
						
					case CROSSROAD_E: case CROSSROAD_S:
						acquirePriority(Control.CROSSROAD.node);
						break;
						
					case CROSSROAD_W: case CROSSROAD_N:
						releaseLock(Control.CROSSROAD.node);
						break;
						
					case STATION_NN: case STATION_NS:
						stationBehavior();
						break;
						
					default:
						break;
					}
				}
			}
		}
		
		public void setSpeed(int speed) throws CommandException {
			if (Math.abs(speed) > MAX_SPEED) {
				speed = MAX_SPEED * (int) Math.signum(speed);
			} else if (Math.abs(speed) < MIN_SPEED) {
				speed = MIN_SPEED * (int) Math.signum(speed);
			}
			this.speed = speed;
			tsi.setSpeed(TRAIN_ID, this.speed);
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
			Train.sleep(1000 + (20 * Math.abs(speed)));
			//Train.sleep(100);
			movementDirection = (movementDirection == Direction.NORTH) ? Direction.SOUTH : Direction.NORTH;
			setSpeed(-speed);
		}
	
		private void acquirePriority(Semaphore semaphore) throws InterruptedException, CommandException {
			if (!locks.contains(semaphore)) {
				tsi.setSpeed(TRAIN_ID, 0);
				semaphore.acquire();
				locks.add(semaphore);
				setSpeed(speed);
			}
		}
		
		private void releaseLock(Semaphore semaphore) {
			if(locks.contains(semaphore)) {
				locks.remove(semaphore);
				semaphore.release();
			}
		}
	}
	
	public enum Control{
		CROSSROAD(), STATION_LANE_NN(), SINGLE_LANE_N(), FAST_LANE(), SINGLE_LANE_S(), STATION_LANE_SN();
		private Semaphore node;
		private Control() {
			node = new Semaphore(1);
		}
	}
	
	public enum Direction{NORTH, SOUTH}; // Directional enumerators
	
	public enum Sensor { // Sensor enumerators corresponding to the map sensors
		// Station sensors
		STATION_NN(15,3), STATION_NS(15,5), STATION_SN(15,11), STATION_SS(15,13),
		// Crossroad sensors
		CROSSROAD_N(8,5), CROSSROAD_S(10,8), CROSSROAD_W(6,7), CROSSROAD_E(10,7),
		// Station lane sensors
		STATION_LANE_NN(14,7), STATION_LANE_NS(14,8), STATION_LANE_SN(6,11), STATION_LANE_SS(4,13), 
		// Middle lane sensors
		MIDDLE_LANE_NW(7,9), MIDDLE_LANE_SW(7,10), MIDDLE_LANE_NE(12,9), MIDDLE_LANE_SE(12,10),
		// Single Lane sensors
		SINGLE_LANE_S(1,9), SINGLE_LANE_N (19,9);
		private int xPos, yPos;
		private Sensor(int xPos, int yPos) { 
			this.xPos = xPos; 
			this.yPos = yPos; 
		}
	}
	
	public enum Switch { // Switch enumerators
		STATION_N(17,7), STATION_S(3,11), MIDDLE_LANE_W(4,9), MIDDLE_LANE_E(15,9);
		private int xPos, yPos;
		private Switch(int xPos, int yPos) { 
			this.xPos = xPos; 
			this.yPos = yPos; 
		}
	}
}