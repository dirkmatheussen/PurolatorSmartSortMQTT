package evolar.be.purolatorsmartsortMQTT;

/**
 * Pojo Object to store street information for a postalcode or for a street
 * Is needed for fast dataentry during remediation
 * Created by Dirk on 4/06/17.
 */

public class RPMLookUp {

    private String postalCode;
    private String streetName;
    private String streetType;
    private int fromNumber;
    private int toNumber;
    private String routeNumber;


    public RPMLookUp(){


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

    public String getRouteNumber() {
        return routeNumber;
    }

    public void setRouteNumber(String routeNumber) {
        this.routeNumber = routeNumber;
    }
}
