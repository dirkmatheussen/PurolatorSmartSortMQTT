package evolar.be.purolatorsmartsortMQTT.events;

import java.util.Date;

import evolar.be.purolatorsmartsortMQTT.PurolatorSmartsortMQTT;

/**
 * Created by Dirk on 17/03/16.
 * POJO Object that contains all information for updating on the screen
 */
public class UIUpdater {

    private String updateType;

    private String routeNumber;
    private String shelfNumber;
    private String deliverySequence;
    private String primarySort;
    private String sideofBelt;
    private String pinCode;
    private String postalCode;

    private String scannedCode;
    private String remediationId;           //identifier when in remediation (typically the barcode or pincode)
    private Date scanDate;

    //errorhandling message send to screen
    private String errorCode;
    private String errorMessage;
    private int severityCode;

    //
    private boolean correctBay;              //package scanned by Operator is in correct bay or not

    public UIUpdater(){
        updateType = PurolatorSmartsortMQTT.UPD_TOPSCREEN;     //default is to show the topinformation on the screen
        errorCode="ERR";  //default is error
        correctBay = true;

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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getSeverityCode() {
        return severityCode;
    }

    public void setSeverityCode(int severityCode) {
        this.severityCode = severityCode;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getUpdateType() {
        return updateType;
    }

    public void setUpdateType(String updateType) {
        this.updateType = updateType;
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

    public Date getScanDate() {
        return scanDate;
    }

    public void setScanDate(Date scanDate) {
        this.scanDate = scanDate;
    }

    public String getRemediationId() {
        return remediationId;
    }

    public void setRemediationId(String remediationId) {
        this.remediationId = remediationId;
    }

    public boolean isCorrectBay() {
        return correctBay;
    }

    public void setCorrectBay(boolean correctBay) {
        this.correctBay = correctBay;
    }
}
