package camos.mode_execution;

import java.util.List;
import java.util.Objects;

import com.graphhopper.jsprit.core.problem.Location;

public class Coordinate {

    double longitude;
    double latitude;
    String code;

    public Coordinate(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    //computes distance in km of this and other coordinate
    public double computeDistance(Coordinate other) {
        double dlong = (other.longitude - this.longitude) * (Math.PI / 180D);
        double dlat = (other.latitude - this.latitude) * (Math.PI / 180D);
        double a = Math.pow(Math.sin(dlat / 2D), 2D) + Math.cos(this.latitude * (Math.PI / 180D)) *
                Math.cos(other.latitude * (Math.PI / 180D)) * Math.pow(Math.sin(dlong / 2D), 2D);
        double c = 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
        return 6378.1370D * c;
    }
    public static Coordinate computeCenter(List<Coordinate> coordinates) {
        double sumlat = 0;
        double sumlong = 0;
        for (Coordinate coord : coordinates) {
            sumlat += Math.toRadians(coord.latitude);
            sumlong += Math.toRadians(coord.longitude);
        }
        double avgLat = sumlat / coordinates.size();
        double avgLong = sumlong / coordinates.size();
        return new Coordinate(Math.toDegrees(avgLong), Math.toDegrees(avgLat));
    }
    public static double distFunc(double distanceToTarget) {
        return Math.log(distanceToTarget + 1) / Math.log(2);
    }
    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinate that = (Coordinate) o;
        return Double.compare(longitude, that.longitude) == 0 && Double.compare(latitude, that.latitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(longitude, latitude);
    }


    public static boolean coordinateTheSameAsLocation(Coordinate coordinate, Location location){
        return coordinate.getLongitude()==location.getCoordinate().getX() && coordinate.getLatitude()==location.getCoordinate().getY();
    }

    public static Coordinate locationToCoordinate(Location location){
        return new Coordinate(location.getCoordinate().getX(),location.getCoordinate().getY());
    }

    public static Location coordinateToLocation(Coordinate coordinate){
        return Location.newInstance(coordinate.getLongitude(),coordinate.getLatitude());
    }


}
