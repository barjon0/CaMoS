package camos.mode_execution.carmodels;

import camos.GeneralManager;

public class StandInVehicle extends Vehicle{

    public StandInVehicle() {
        this.withDriver = true;
        this.co2EmissionPerLiter = GeneralManager.studentCarCo2EmissionPerLiter;
        this.pricePerLiter = GeneralManager.studentCarPricePerLiter*2;
        this.consumptionPerKm = GeneralManager.studentCarConsumptionPerKm;
        idCounter++;
        this.id = idCounter;
    }

}
