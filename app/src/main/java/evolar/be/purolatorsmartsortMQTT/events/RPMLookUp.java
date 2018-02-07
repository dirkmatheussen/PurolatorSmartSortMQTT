package evolar.be.purolatorsmartsortMQTT.events;

/**
 * Pojo Object to store street information for a postalcode or for a street
 * Is needed for fast dataentry during remediation
 * Created by Dirk on 4/06/17.
 */

public class RPMLookUp {

    private String pinCode;                 //the scanned barcode
    private String postalCode;
    private String streetName;
    private String streetType;
    private int fromNumber;
    private int toNumber;
    private String fromUnitNumber;
    private String toUnitNumber;
    private String municipality;

    private String routeNumber;

    private boolean uniqueStreet;


    public RPMLookUp(){


    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public String getStreetType() {
        return streetType;
    }

    public void setStreetType(String streetType) {
        this.streetType = streetType;
    }

    public int getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(int fromNumber) {
        this.fromNumber = fromNumber;
    }

    public int getToNumber() {
        return toNumber;
    }

    public void setToNumber(int toNumber) {
        this.toNumber = toNumber;
    }

    public boolean isUniqueStreet() {
        return uniqueStreet;
    }

    public String getFromUnitNumber() {
        return fromUnitNumber;
    }

    public void setFromUnitNumber(String fromUnitNumber) {
        this.fromUnitNumber = fromUnitNumber;
    }

    public String getToUnitNumber() {
        return toUnitNumber;
    }

    public void setToUnitNumber(String toUnitNumber) {
        this.toUnitNumber = toUnitNumber;
    }

    public void setUniqueStreet(boolean uniqueStreet) {
        this.uniqueStreet = uniqueStreet;
    }

    public String getRouteNumber() {
        return routeNumber;
    }

    public void setRouteNumber(String routeNumber) {
        this.routeNumber = routeNumber;
    }

    public String getMunicipality() {
        return municipality;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }
}
