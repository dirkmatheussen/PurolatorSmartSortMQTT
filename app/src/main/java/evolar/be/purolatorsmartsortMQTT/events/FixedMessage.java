package evolar.be.purolatorsmartsortMQTT.events;

/**
 * Object that contains all the information of a Fixed scanner and send to all the active glasses
 * Used when tablet app starts & stops
 * Created by Dirk on 6/04/16.
 */
public class FixedMessage {

    private String fixedSender;             //name of the sender (Tablet)
    private String message;                 //message (START, STOP, PUDRO)
    private String value;                   //a value related to the message (PUDRO SPEED)
    private String receiver;                //name of the receiver (Glass


    String className = FixedMessage.class.getSimpleName();

    public FixedMessage(){


    }


    public String getFixedSender() {
        return fixedSender;
    }

    public void setFixedSender(String fixedSender) {
        this.fixedSender = fixedSender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
