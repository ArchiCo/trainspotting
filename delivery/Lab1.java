package trainspotting;

import TSim.*;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Lab1 {

    public Lab1(Integer speed1, Integer speed2) {
        TSimInterface tsi = TSimInterface.getInstance();
        int trainId1 = 1, trainId2 = 2;
        // Initializing instances of 2 trains which are defined as separate threads and starting them
        Train train1 = new Train(trainId1, speed1, tsi, Direction.SOUTH);
        Train train2 = new Train(trainId2, speed2, tsi, Direction.NORTH);
        train1.start();
        train2.start();
    }

    public enum Direction {
        NORTH, SOUTH
    }; // Directional enumerators

    public enum Control {
        CROSSROAD(), STATION_LANE_NN(), SINGLE_LANE_N(), FAST_LANE(), SINGLE_LANE_S(), STATION_LANE_SN();
        private Semaphore node;

        private Control() {
            node = new Semaphore(1);
        }
    }

    public enum Sensor { // Sensor enumerators corresponding to the map sensors
        // Station sensors
        STATION_NN(15, 3), STATION_NS(15, 5), STATION_SN(15, 11), STATION_SS(15, 13),
        // Crossroad sensors
        CROSSROAD_N(9, 5), CROSSROAD_S(10, 8), CROSSROAD_W(6, 7), CROSSROAD_E(10, 7),
        // Station lane sensors
        STATION_LANE_NN(15, 7), STATION_LANE_NS(15, 8), STATION_LANE_SN(5, 11), STATION_LANE_SS(4, 13),
        // Middle lane sensors
        MIDDLE_LANE_NW(6, 9), MIDDLE_LANE_SW(6, 10), MIDDLE_LANE_NE(13, 9), MIDDLE_LANE_SE(13, 10),
        // Single Lane sensors
        SINGLE_LANE_S(1, 9), SINGLE_LANE_N(19, 9);
        private int xPos, yPos;

        private Sensor(int xPos, int yPos) {
            this.xPos = xPos;
            this.yPos = yPos;
        }
    }

    public enum Switch { // Switch enumerators
        STATION_N(17, 7), STATION_S(3, 11), MIDDLE_LANE_W(4, 9), MIDDLE_LANE_E(15, 9);
        private int xPos, yPos;

        private Switch(int xPos, int yPos) {
            this.xPos = xPos;
            this.yPos = yPos;
        }
    }

    class Train extends Thread {
        private final int TRAIN_ID, MAX_SPEED = 17;
        private int speed;
        private Direction movementDirection;
        private SensorEvent previousSensorEvent;
        private TSimInterface tsi;
        private ArrayList<Semaphore> locks = new ArrayList<Semaphore>();

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
                    if (previousSensorEvent == null || currentSensorEvent.getXpos() != previousSensorEvent.getXpos()
                            || currentSensorEvent.getYpos() != previousSensorEvent.getYpos()) {
                        // If so, proceed to assessing behavior according to the active sensor
                        sensorActivity(currentSensorEvent.getXpos(), currentSensorEvent.getYpos());
                        previousSensorEvent = currentSensorEvent;
                    }
                }
            } catch (CommandException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        /**
         * Method that is used for handling train's and railroad' element's
         * behaviors. Behavior execution is dependent on the latest active
         * sensor (derived from SensorEvent in the main loop and passed as x & y
         * coordinates to be compared against enumerated table of sensor
         * elements) and train's movement direction (traveling to North or South
         * station).
         * 
         * @param xPos: x-Position of the latest active sensor responsible for the event
         * @param yPos: y-Position of the latest active sensor responsible for the event
         * @throws CommandException
         * @throws InterruptedException
         */

        private void sensorActivity(int xPos, int yPos) throws CommandException, InterruptedException {
            Sensor activeSensor = getSensor(xPos, yPos);
            if (activeSensor != null) {
                int switchDirection;
                if (movementDirection == Direction.SOUTH) {
                    switch (activeSensor) {

                    case STATION_NN: // Initial departure from Northern station.
                    case STATION_NS:
                        if (activeSensor == Sensor.STATION_NN) { // If on northern lane
                            acquirePriority(Control.STATION_LANE_NN.node); // ensures that it stays reserved
                        }
                        break;

                    case CROSSROAD_W: // Entering crossroad
                    case CROSSROAD_N:
                        acquirePriority(Control.CROSSROAD.node); // ensure it's reserved by the train
                        break;

                    case CROSSROAD_E: // Exiting crossroad
                    case CROSSROAD_S:
                        releaseLock(Control.CROSSROAD.node);
                        break;

                    case STATION_LANE_NN: // Exiting station lanes towards the single lane section
                    case STATION_LANE_NS:
                        acquirePriority(Control.SINGLE_LANE_N.node); // ensure that the lane´s reserved by Train
                        switchDirection = (activeSensor == Sensor.STATION_LANE_NN) // Exiting from  N or S?
                                ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
                        setSwitch(Switch.STATION_N, switchDirection); // switch rails accordingly
                        break;

                    case SINGLE_LANE_N: // Passing the middle of the single lane
                        switchDirection = tryAcquiringPriority( // attempt to reserve the fast middle lane
                                Control.FAST_LANE, TSimInterface.SWITCH_RIGHT, TSimInterface.SWITCH_LEFT);
                        setSwitch(Switch.MIDDLE_LANE_E, switchDirection); // switch to fast or longer lane
                        releaseLock(Control.STATION_LANE_NN.node); // open access to northern lane
                        break;

                    case MIDDLE_LANE_NE: // Entering middle lane section
                    case MIDDLE_LANE_SE:
                        releaseLock(Control.SINGLE_LANE_N.node); // open access to the single lane
                        break;

                    case MIDDLE_LANE_NW: // Exiting middle lane section
                    case MIDDLE_LANE_SW:
                        acquirePriority(Control.SINGLE_LANE_S.node); // ensure that the lane´s reserved by Train
                        switchDirection = (activeSensor == Sensor.MIDDLE_LANE_NW) // Exiting from N or S?
                                ? TSimInterface.SWITCH_LEFT : TSimInterface.SWITCH_RIGHT;
                        setSwitch(Switch.MIDDLE_LANE_W, switchDirection); // switch rails accordingly
                        break;

                    case SINGLE_LANE_S: // Passing the middle of the single lane
                        switchDirection = tryAcquiringPriority( // attempt to reserve the northern lane
                                Control.STATION_LANE_SN, TSimInterface.SWITCH_LEFT, TSimInterface.SWITCH_RIGHT);
                        setSwitch(Switch.STATION_S, switchDirection); // switch rails to reserved or alt path
                        releaseLock(Control.FAST_LANE.node); // open access to the fast middle  lane
                        break;

                    case STATION_LANE_SN: // Entering one of the station lane.
                    case STATION_LANE_SS:
                        releaseLock(Control.SINGLE_LANE_S.node); // opening access to the single lane
                        break;

                    case STATION_SN: // Arriving to the destination
                    case STATION_SS:
                        stationBehavior(); // Initiate arrival behavior: stop, wait, reserve movement
                        break;

                    default:
                        break;
                    }

                } else if (movementDirection == Direction.NORTH) {

                    // The procedure for the journey repeats to the previous logic
                    switch (activeSensor) {

                    case STATION_SN:
                    case STATION_SS:
                        if (activeSensor == Sensor.STATION_SN) {
                            acquirePriority(Control.STATION_LANE_SN.node);
                        }
                        break;

                    case STATION_LANE_SN:
                    case STATION_LANE_SS:
                        acquirePriority(Control.SINGLE_LANE_S.node);
                        switchDirection = (activeSensor == Sensor.STATION_LANE_SS) ? TSimInterface.SWITCH_RIGHT
                                : TSimInterface.SWITCH_LEFT;
                        setSwitch(Switch.STATION_S, switchDirection);
                        break;

                    case SINGLE_LANE_S:
                        switchDirection = tryAcquiringPriority(Control.FAST_LANE, TSimInterface.SWITCH_LEFT,
                                TSimInterface.SWITCH_RIGHT);
                        setSwitch(Switch.MIDDLE_LANE_W, switchDirection);
                        releaseLock(Control.STATION_LANE_SN.node);
                        break;

                    case MIDDLE_LANE_NW:
                    case MIDDLE_LANE_SW:
                        releaseLock(Control.SINGLE_LANE_S.node);
                        break;

                    case MIDDLE_LANE_NE:
                    case MIDDLE_LANE_SE:
                        acquirePriority(Control.SINGLE_LANE_N.node);
                        switchDirection = (activeSensor == Sensor.MIDDLE_LANE_NE) ? TSimInterface.SWITCH_RIGHT
                                : TSimInterface.SWITCH_LEFT;
                        setSwitch(Switch.MIDDLE_LANE_E, switchDirection);
                        break;

                    case SINGLE_LANE_N:
                        switchDirection = tryAcquiringPriority(Control.STATION_LANE_NN, TSimInterface.SWITCH_RIGHT,
                                TSimInterface.SWITCH_LEFT);
                        setSwitch(Switch.STATION_N, switchDirection);
                        releaseLock(Control.FAST_LANE.node);
                        break;

                    case STATION_LANE_NN:
                    case STATION_LANE_NS:
                        releaseLock(Control.SINGLE_LANE_N.node);
                        break;

                    case CROSSROAD_E:
                    case CROSSROAD_S:
                        acquirePriority(Control.CROSSROAD.node);
                        break;

                    case CROSSROAD_W:
                    case CROSSROAD_N:
                        releaseLock(Control.CROSSROAD.node);
                        break;

                    case STATION_NN:
                    case STATION_NS:
                        stationBehavior();
                        break;

                    default:
                        break;
                    }
                }
            }
        }

        // Rail switch setting command. Encapsulated for the better code readability in the main body
        public void setSwitch(Switch railSwitch, int direction) throws CommandException {
            tsi.setSwitch(railSwitch.xPos, railSwitch.yPos, direction);
        }

        // Sets speed of the train while ensuring that it doesn't go over the MAX limit
        public void setSpeed(int speed) throws CommandException {
            if (Math.abs(speed) > MAX_SPEED) {
                speed = MAX_SPEED * (int) Math.signum(speed);
            }
            this.speed = speed;
            tsi.setSpeed(TRAIN_ID, this.speed);
        }

        // Makes sure that the active sensor exists in the mapping
        private Sensor getSensor(int xPos, int yPos) {
            for (Sensor s : Sensor.values()) {
                if (s.xPos == xPos && s.yPos == yPos) {
                    return s;
                }
            }
            return null;
        }

        // Station arrival, halt and departure behavior for the train. Time for
        // departure remains persistent regardless of the simulation's speed.
        private void stationBehavior() throws CommandException, InterruptedException {
            tsi.setSpeed(TRAIN_ID, 0);
            Train.sleep(1000 + (20 * Math.abs(speed)));
            movementDirection = (movementDirection == Direction.NORTH) ? Direction.SOUTH : Direction.NORTH;
            setSpeed(-speed);
        }

        // Semaphore and lock acquisition logic.
        private void acquirePriority(Semaphore semaphore) throws InterruptedException, CommandException {
            if (!locks.contains(semaphore)) {
                tsi.setSpeed(TRAIN_ID, 0);
                semaphore.acquire();
                locks.add(semaphore);
                setSpeed(speed);
            }
        }

        // Semaphore and lock acquisition in cases where an alternative path is available if a priority cannot be secured.
        private int tryAcquiringPriority(Control semaphore, int acquiredDirection, int alternativeDirection) {
            if (semaphore.node.tryAcquire()) {
                locks.add(semaphore.node);
                return acquiredDirection;
            } else {
                return alternativeDirection;
            }
        }

        // Semaphore and lock releasing logic.
        private void releaseLock(Semaphore semaphore) {
            if (locks.contains(semaphore)) {
                locks.remove(semaphore);
                semaphore.release();
            }
        }
    }
}