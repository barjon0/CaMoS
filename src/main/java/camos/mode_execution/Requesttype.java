package camos.mode_execution;

public enum Requesttype {

    DRIVETOUNI, DRIVEHOME, BOTH;

    public Requesttype getOpposite() {
        if (this == DRIVEHOME) {
            return DRIVETOUNI;
        } else if (this == DRIVETOUNI) {
            return DRIVEHOME;
        } else {
            return this;
        }
    }

    public static Requesttype getValue(String name) {
        if(name.equals("ToWork")) {
            return DRIVETOUNI;
        } else if(name.equals("FromWork")) {
            return DRIVEHOME;
        } else {
            throw new IllegalStateException("wrong name given to requesttype");
        }
    }
    @Override
    public String toString() {
        if (this == Requesttype.DRIVETOUNI) {
            return "ToWork";
        } else {
            return "FromWork";
        }
    }
}
