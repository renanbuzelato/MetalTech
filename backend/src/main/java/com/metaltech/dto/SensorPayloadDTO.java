package com.metaltech.dto;

public class SensorPayloadDTO {
    private Boolean sensorIndutivo;
    private Boolean sensorCapacitivo;
    private Double distanciaLaserMm;

    public Boolean getSensorIndutivo() { return sensorIndutivo != null && sensorIndutivo; }
    public void setSensorIndutivo(Boolean sensorIndutivo) { this.sensorIndutivo = sensorIndutivo; }

    public Boolean getSensorCapacitivo() { return sensorCapacitivo != null && sensorCapacitivo; }
    public void setSensorCapacitivo(Boolean sensorCapacitivo) { this.sensorCapacitivo = sensorCapacitivo; }

    public Double getDistanciaLaserMm() { return distanciaLaserMm != null ? distanciaLaserMm : 0.0; }
    public void setDistanciaLaserMm(Double distanciaLaserMm) { this.distanciaLaserMm = distanciaLaserMm; }
}