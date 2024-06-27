package camos.mode_execution.groupings;

import camos.mode_execution.Requesttype;

public enum Stopreason {

    PICKUP, DROPOFF, PARKING, REFUELING;

    public static Stopreason getReason(Requesttype requesttype) {
        if(requesttype == Requesttype.DRIVETOUNI) {
            return PICKUP;
        } else if( requesttype == Requesttype.DRIVEHOME) {
            return DROPOFF;
        } else {
            throw new IllegalArgumentException("Both is not allowed here");
        }
    }

}
