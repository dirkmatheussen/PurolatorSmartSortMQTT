package evolar.be.purolatorsmartsortMQTT;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.InputFilter;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

/**
 * Created by Dirk on 21/03/16.
 */
public class ScannedLabelsAdapter extends ArrayAdapter<Label>{


    public ScannedLabelsAdapter(Context context, ArrayList<Label> labels) {
        super(context, 0, labels);
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // Get the data item for this position



        final Label label = getItem(position);
        final int deletePosition = position;
        final ViewGroup fParent = parent;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_label, parent, false);
        }
        // Lookup view for data population
        TextView idView = (TextView) convertView.findViewById(R.id.itemidView);
        TextView routeView = (TextView) convertView.findViewById(R.id.itemRouteView);
        TextView shelfView = (TextView) convertView.findViewById(R.id.itemshelfView);
        TextView counterView = (TextView) convertView.findViewById(R.id.itemcounterview);
        final Button actionBtn = (Button) convertView.findViewById(R.id.actionBtn);

//TODO New Change: deletebutton changed into picture button with delete or edit functionality
        if (PurolatorSmartsortMQTT.getsInstance().configData.getDeviceType().equals("GLASS")) {
            actionBtn.setVisibility(View.INVISIBLE);


/*
            if (!label.getRouteNumber().equalsIgnoreCase("REM")) {
                actionBtn.setVisibility(View.INVISIBLE);
            } else if (Utilities.getPostalCode(label.getScannedCode()) != null)  {
                actionBtn.setVisibility(View.INVISIBLE);
            } else if (label.getRouteNumber().equalsIgnoreCase("REM")|| label.getRouteNumber().equalsIgnoreCase("XXX")){
                actionBtn.setVisibility(View.VISIBLE);
            }
 */
        }

        actionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PurolatorSmartsortMQTT.getsInstance().configData.getDeviceType().equals("GLASS")) {
                     editRow(label,fParent,deletePosition);

                } else {
                    remove(getLabels().get(deletePosition));
                    notifyDataSetChanged();
                }
            }
        });

        // Populate the data into the template view using the data object
        idView.setText(label.getPinCode());
        routeView.setText(label.getRouteNumber());
        shelfView.setText(label.getShelfNumber());

        idView.setTextColor(Color.DKGRAY);
        routeView.setTextColor(Color.DKGRAY);
        counterView.setTextColor(Color.DKGRAY);
        shelfView.setTextColor(Color.DKGRAY);

        long now = new Date().getTime();

        if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {

            if ((PurolatorSmartsortMQTT.getsInstance().DISTANCE) > 0) {
                String elapsed = " ";
                if (label.getLastSpeedMeasurement() > 0 && PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed() > 0) {

                    double PudroSpeed = PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed();

                    double distanceDone = label.getDistanceTravelled() + (PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed() * (now - label.getLastSpeedMeasurement()) / 1000.0);

                    label.setDistanceTravelled(distanceDone);
                    label.setLastSpeedMeasurement(now);

                    double eta = ((PurolatorSmartsortMQTT.getsInstance().DISTANCE * 1.0) - distanceDone) / PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed();


                    if (eta <= PurolatorSmartsortMQTT.getsInstance().DISTANCE_TIMER_RED) {
                        idView.setTextColor(Color.RED);
                        routeView.setTextColor(Color.RED);
                        counterView.setTextColor(Color.RED);
                        shelfView.setTextColor(Color.RED);
                    } else if (eta > PurolatorSmartsortMQTT.getsInstance().DISTANCE_TIMER_RED && eta <= PurolatorSmartsortMQTT.getsInstance().DISTANCE_TIMER_GREEN) {
                        idView.setTextColor(Color.GREEN);
                        routeView.setTextColor(Color.GREEN);
                        counterView.setTextColor(Color.GREEN);
                        shelfView.setTextColor(Color.GREEN);
                    } else {
                        idView.setTextColor(Color.DKGRAY);
                        routeView.setTextColor(Color.DKGRAY);
                        counterView.setTextColor(Color.DKGRAY);
                        shelfView.setTextColor(Color.DKGRAY);
                    }

                    if (eta < -120) {
                        elapsed = "!!!";
                    } else {
                        DecimalFormat decimalFormat = new DecimalFormat("#");
                        elapsed = decimalFormat.format(eta);
                    }

                }
                counterView.setText(elapsed);
                label.setLastSpeedMeasurement(now);
            } else {
                counterView.setText(String.valueOf(position));
            }
            //if it is Remediation record, put it in light Gray
            if (label.getRouteNumber().equals("REM")|| label.getRouteNumber().equals("XXX")){
                actionBtn.setVisibility(View.VISIBLE);

                idView.setTextColor(Color.GRAY);
                routeView.setTextColor(Color.GRAY);
                counterView.setTextColor(Color.GRAY);
                shelfView.setTextColor(Color.GRAY);

            }

        } else {
            idView.setWidth(800);
            counterView.setText(label.getSideofBelt());
            if (label.getLabelType() == 1){
                convertView.setBackgroundColor(Color.RED);
                counterView.setVisibility(View.INVISIBLE);
                shelfView.setVisibility(View.INVISIBLE);
                counterView.setVisibility(View.INVISIBLE);
            } else {
                if (label.isSendToGlass()) {
                    convertView.setBackgroundColor(Color.GREEN);
                } else if (label.isMissort()){
                    convertView.setBackgroundColor(Color.GRAY);
                } else {
                    convertView.setBackgroundColor(Color.YELLOW);
                }
            }
        }
        return convertView;
    }

    public ArrayList<Label> getLabels(){

        ArrayList<Label> labels = new ArrayList<Label>();
        for (int i=0;i<getCount();i++){
            labels.add(getItem(i));
        }
        return labels;
    }



    public void sortDate(final boolean descending){
            this.sort(new Comparator<Label>() {
                @Override
                public int compare(Label lhs, Label rhs ){
                    if (lhs.getScanTime() == null || rhs.getScanTime() == null)
                        return 0;
                    if (descending) return rhs.getScanTime().compareTo(lhs.getScanTime());
                    return lhs.getScanTime().compareTo(rhs.getScanTime());
                }
            });

    }

    /**
     *
     * Edit the content of 1 label, for remediation
     * This is handled by the PurolatorActivityGlass
     *
     * @param label
     */
    private void editRow(Label label,ViewGroup parent,final int deletePosition){

        UIUpdater uiUpdater = new UIUpdater();
        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
        uiUpdater.setErrorCode("MAKEPOPUP");
        uiUpdater.setErrorMessage(label.getPinCode()+";"+ label.getScannedCode());
        EventBus.getDefault().post(uiUpdater);

    }

}
