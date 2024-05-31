package camos.mode_execution.carmodels;

import camos.GeneralManager;

public class MiniBus extends Vehicle{

    public MiniBus() {
        this.seatCount = GeneralManager.busSeatCount;
        this.withDriver = true;
        this.co2EmissionPerLiter = GeneralManager.busCo2EmissionPerLiter;
        this.pricePerLiter = GeneralManager.busPricePerLiter;
        this.consumptionPerKm = GeneralManager.busConsumptionPerKm;
        idCounter++;
        id = idCounter;
    }

}
