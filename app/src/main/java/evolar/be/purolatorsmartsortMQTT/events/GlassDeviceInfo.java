package evolar.be.purolatorsmartsortMQTT.events;

import java.util.ArrayList;

/**
 * Configuration information send from a smartGlass to the tablet
 * Used when the app becomes active or when the app stops.
 *
 * Created by Dirk on 7/04/16.
 */
public class GlassDeviceInfo {

    Boolean active;                     //flag to indicate if device is active or not
    String userName;                    //name of the logged on user
    String deviceName;
    String deviceType;                  //FIXED,GLASS,WAVE
    ArrayList<String> assignedRoutes = new ArrayList<String>();

    String className = GlassDeviceInfo.class.getSimpleName();

    public GlassDeviceInfo(){


    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public ArrayList<String> getAssignedRoutes() {
        return assignedRoutes;
    }

    public void setAssignedRoutes(ArrayList<String> assignedRoutes) {
        this.assignedRoutes = assignedRoutes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
}
