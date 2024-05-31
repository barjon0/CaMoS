package camos.mode_execution.carmodels;

import camos.GeneralManager;

public class StudentVehicle extends Vehicle {


    public StudentVehicle() {
        this.seatCount = GeneralManager.studentCarSeatCount;
        this.withDriver = true;
        this.co2EmissionPerLiter = GeneralManager.studentCarCo2EmissionPerLiter;
        this.pricePerLiter = GeneralManager.studentCarPricePerLiter;
        this.consumptionPerKm = GeneralManager.studentCarConsumptionPerKm;
        idCounter++;
        this.id = idCounter;
    }

}
