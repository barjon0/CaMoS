package camos.mode_execution;

import java.util.List;
import java.util.Objects;

import com.graphhopper.jsprit.core.problem.Location;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.CoordinateXY;
import org.opengis.referencing.operation.TransformException;

public class Coordinate {

    double longitude;
    double latitude;
    String code;

    public Coordinate(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    //computes distance in km of this and other coordinate
    public double computeDistance(Coordinate other) throws TransformException {
        org.locationtech.jts.geom.Coordinate oneCoordinate = new CoordinateXY(this.longitude, this.latitude);
        org.locationtech.jts.geom.Coordinate otherCoordinate = new CoordinateXY(other.longitude, other.latitude);

        double distance = JTS.orthodromicDistance(oneCoordinate, otherCoordinate, DefaultGeographicCRS.WGS84);
        return (distance / 1000);

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

    public boolean equalValue(Coordinate other) {
        return this.longitude == other.longitude && this.latitude == other.latitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return false;
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
