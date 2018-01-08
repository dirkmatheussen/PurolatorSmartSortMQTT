package evolar.be.purolatorsmartsortMQTT;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * Class that contains the information of a scanned Label
 * Is maintained in an array/ adapter. for the fixed scanner the label is of type route or of type remedation
 *
 * Created by Dirk on 21/03/16.
 */
public class Label {

    private final static boolean D = true;

    private int labelType = 0;              //0 = route label
                                            //1 = remediation label

    private String routeNumber;
    private String shelfNumber;
    private String deliverySequence;
    private String primarySort;
    private String sideofBelt;
    private String pinCode;                 //unique Identification code, based in the scanned 1D or 2D Barcode.
                                            // It is normally the PIN Code or Street, number, postcode, city
    private String remediationId;           //identified barcode/pincode for remediation
    private Date scanTime;
    private String scannedCode;             //the scanned code, without \n.

    private double distanceTravelled;       //distance travelled from fixed scanner till now, used by the Vuzix glasses
    private long lastSpeedMeasurement = 0;       //time in ms of last SpeedMeasurement

    private boolean sendToGlass = false;
    private boolean missort = false;





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

    public Date getScanTime() {
        return scanTime;
    }

    public void setScanTime(Date scanTime) {
        this.scanTime = scanTime;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public int getLabelType() {
        return labelType;
    }

    public void setLabelType(int labelType) {
        this.labelType = labelType;
    }


    public boolean isSendToGlass() {
        return sendToGlass;
    }

    public void setSendToGlass(boolean sendToGlass) {
        this.sendToGlass = sendToGlass;
    }

    public double getDistanceTravelled() {
        return distanceTravelled;
    }

    public void setDistanceTravelled(double distanceTravelled) {
        this.distanceTravelled = distanceTravelled;
    }

    public long getLastSpeedMeasurement() {
        return lastSpeedMeasurement;
    }

    public void setLastSpeedMeasurement(long lastSpeedMeasurement) {
        this.lastSpeedMeasurement = lastSpeedMeasurement;

    }

    public String getRemediationId() {
        return remediationId;
    }

    public void setRemediationId(String remediationId) {
        this.remediationId = remediationId;
    }

    public boolean isMissort() {
        return missort;
    }

    public void setMissort(boolean missort) {
        this.missort = missort;
    }

    /**
     * Calculate a unique MD5 Hash of this object
     * The seed value is: routeNumber+shelfNumber+primarySort+sideofBelt+pinCode
     *
     * @return
     */
    public String getMD5hash(){

        String internalKey = null;


        try {
            MessageDigest md = 	MessageDigest.getInstance("MD5");
            String seed = routeNumber+shelfNumber+primarySort+sideofBelt+pinCode;
            byte[] array = md.digest(seed.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte anArray : array) {
                sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
            }
            internalKey =  sb.toString() + "==";

        } catch (NoSuchAlgorithmException e) {
            if(D) Log.d("Label","Error in MD5Hash Creation, reason: " + e.getLocalizedMessage());
        }
        return internalKey;
    }
}
