package evolar.be.purolatorsmartsortMQTT.events;

import java.util.ArrayList;

/**
 * Object that contains all the information of a Glass send to the fixed scanner via MQTT
 * Created by Dirk on 6/04/16.
 */
public class GlassMessage {

    String barcode;
    String glassName;               //name of the sender
    String fixedTarget;             //name of the receiver
    String message;                 //message


    String className = GlassMessage.class.getSimpleName();



    public GlassMessage(){


    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getGlassName() {
        return glassName;
    }

    public void setGlassName(String glassName) {
        this.glassName = glassName;
    }

    public String getFixedTarget() {
        return fixedTarget;
    }

    public void setFixedTarget(String fixedTarget) {
        this.fixedTarget = fixedTarget;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
