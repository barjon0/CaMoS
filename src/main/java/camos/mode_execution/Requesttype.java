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

    @Override
    public String toString() {
        if (this == Requesttype.DRIVETOUNI) {
            return "ToWork";
        } else {
            return "FromWork";
        }
    }
}
