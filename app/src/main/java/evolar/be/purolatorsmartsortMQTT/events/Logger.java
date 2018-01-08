package evolar.be.purolatorsmartsortMQTT.events;

import java.util.Date;
import java.util.StringTokenizer;

/**
 * event that contains all the logging data to send back to the FTP Server
 * used to generate logs & manifast
 * Captured by the FTPComms class
 * Created by Dirk on 24/03/16.
 */
public class Logger {

    private String logType;     //action log, manifest entry, FTPUpload

    private String device_id;
    private String userCode;
    private String terminalID;

    private String parkingPlanMasterVersionID;
    private String routePlanVersionId;
    private String pinMasterId;
    private String prePrintID;
    private String SSStatusReason;
    private String smartSortMode;       //default = 1
    private Date scanDateTime;          //default is now
    private String postalCode;
    private String provinceCode;
    private String municipalityName;
    private String streetNumber;
    private String streetNumberSuffix;
    private String streetName;
    private String streetType;
    private String streetDirection;
    private String unitNumber;
    private String customerName;
    private Date printDateTime;
    private String deliveryTime;
    private String shipmentType;
    private String DeliveryType;
    private String diversionCode;
    private String handlingClassType;
    private String packageType;
    private String barcodeType;
    private String resolvedBy;
    private String alternateAddressFlag;


    private String routeNumber;
    private String shelfNumber;
    private String shelfOverride;
    private String deliverySequence;
    private String primarySort;
    private String sideofBelt;
    private String pinCode;


    private String scannedCode;


    private String status;   //fixed scanner - ringscanner - shelf/truck log

    public Logger(){
        smartSortMode = "1";
        scanDateTime = new Date();


    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
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

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getTerminalID() {
        return terminalID;
    }

    public void setTerminalID(String terminalID) {
        this.terminalID = terminalID;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
    }

    public String getParkingPlanMasterVersionID() {
        return parkingPlanMasterVersionID;
    }

    public void setParkingPlanMasterVersionID(String parkingPlanMasterVersionID) {
        this.parkingPlanMasterVersionID = parkingPlanMasterVersionID;
    }

    public String getRoutePlanVersionId() {
        return routePlanVersionId;
    }

    public void setRoutePlanVersionId(String routePlanVersionId) {
        this.routePlanVersionId = routePlanVersionId;
    }

    public String getPinMasterId() {
        return pinMasterId;
    }

    public void setPinMasterId(String pinMasterId) {
        this.pinMasterId = pinMasterId;
    }

    public String getPrePrintID() {
        return prePrintID;
    }

    public void setPrePrintID(String prePrintID) {
        this.prePrintID = prePrintID;
    }

    public String getSSStatusReason() {
        return SSStatusReason;
    }

    public void setSSStatusReason(String SSStatusReason) {
        this.SSStatusReason = SSStatusReason;
    }

    public String getSmartSortMode() {
        return smartSortMode;
    }

    public void setSmartSortMode(String smartSortMode) {
        this.smartSortMode = smartSortMode;
    }

    public Date getScanDateTime() {
        return scanDateTime;
    }

    public void setScanDateTime(Date scanDateTime) {
        this.scanDateTime = scanDateTime;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    public String getMunicipalityName() {
        return municipalityName;
    }

    public void setMunicipalityName(String municipalityName) {
        this.municipalityName = municipalityName;
    }

    public String getStreetNumber() {
        return streetNumber;
    }

    public void setStreetNumber(String streetNumber) {
        this.streetNumber = streetNumber;
    }

    public String getStreetNumberSuffix() {
        return streetNumberSuffix;
    }

    public void setStreetNumberSuffix(String streetNumberSuffix) {
        this.streetNumberSuffix = streetNumberSuffix;
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

    public String getStreetDirection() {
        return streetDirection;
    }

    public void setStreetDirection(String streetDirection) {
        this.streetDirection = streetDirection;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public void setUnitNumber(String unitNumber) {
        this.unitNumber = unitNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Date getPrintDateTime() {
        return printDateTime;
    }

    public void setPrintDateTime(Date printDateTime) {
        this.printDateTime = printDateTime;
    }

    public String getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(String deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public String getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(String shipmentType) {
        this.shipmentType = shipmentType;
    }

    public String getDeliveryType() {
        return DeliveryType;
    }

    public void setDeliveryType(String deliveryType) {
        DeliveryType = deliveryType;
    }

    public String getDiversionCode() {
        return diversionCode;
    }

    public void setDiversionCode(String diversionCode) {
        this.diversionCode = diversionCode;
    }

    public String getHandlingClassType() {
        return handlingClassType;
    }

    public void setHandlingClassType(String handlingClassType) {
        this.handlingClassType = handlingClassType;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getBarcodeType() {
        return barcodeType;
    }

    public void setBarcodeType(String barcodeType) {
        this.barcodeType = barcodeType;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getAlternateAddressFlag() {
        return alternateAddressFlag;
    }

    public void setAlternateAddressFlag(String alternateAddressFlag) {
        this.alternateAddressFlag = alternateAddressFlag;
    }
}
