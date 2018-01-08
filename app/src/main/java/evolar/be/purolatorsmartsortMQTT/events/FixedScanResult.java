package evolar.be.purolatorsmartsortMQTT.events;

import java.util.Date;

/**
 * event that contains the information from the Fixed scanner app or the ARC
 * Used as object to send to Glasses/ WaveSorter, with MQTT
 *
 * Created by Dirk on 24/03/16.
 */
public class FixedScanResult {

    private String routeNumber;
    private String shelfNumber;
    private String shelfOverride;
    private String deliverySequence;
    private String primarySort;
    private String sideofBelt;
    private String pinCode;

    private String postalCode;
    private String municipality;
    private String streetname;
    private String streetnumber;


    private String scannedCode;
    private Date scanDate;
    private String dimensionData;

    private String senderType;           //type of the Sender (FIXED,GLASS,WAVE)
    private String glassTarget;         //name of the receiver
    private boolean missort = false;     //default missort = false


    public FixedScanResult(){


    }

    public String getRouteNumber() {
        return routeNumber;
    }

    public void setRouteNumber(String routeNumber) {
        this.routeNumber = routeNumber;
    }

    public String getShelfNumber() {
        return shelfNumber;
    }

    public void setShelfNumber(String shelfNumber) {
        this.shelfNumber = shelfNumber;
    }

    public String getShelfOverride() {
        return shelfOverride;
    }

    public void setShelfOverride(String shelfOverride) {
        this.shelfOverride = shelfOverride;
    }

    public String getDeliverySequence() {
        return deliverySequence;
    }

    public void setDeliverySequence(String deliverySequence) {
        this.deliverySequence = deliverySequence;
    }

    public String getPrimarySort() {
        return primarySort;
    }

    public void setPrimarySort(String primarySort) {
        this.primarySort = primarySort;
    }

    public String getSideofBelt() {
        return sideofBelt;
    }

    public void setSideofBelt(String sideofBelt) {
        this.sideofBelt = sideofBelt;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getMunicipality() {
        return municipality;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }

    public String getStreetname() {
        return streetname;
    }

    public void setStreetname(String streetname) {
        this.streetname = streetname;
    }

    public String getStreetnumber() {
        return streetnumber;
    }

    public void setStreetnumber(String streetnumber) {
        this.streetnumber = streetnumber;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getGlassTarget() {
        return glassTarget;
    }

    public void setGlassTarget(String glassTarget) {
        this.glassTarget = glassTarget;
    }

    public Date getScanDate() {
        return scanDate;
    }

    public void setScanDate(Date scanDate) {
        this.scanDate = scanDate;
    }

    public String getDimensionData() {
        return dimensionData;
    }

    public void setDimensionData(String dimensionData) {
        this.dimensionData = dimensionData;
    }

    public boolean isMissort() {
        return missort;
    }
    public void setMissort(boolean missort) {
        this.missort = missort;
    }
}
