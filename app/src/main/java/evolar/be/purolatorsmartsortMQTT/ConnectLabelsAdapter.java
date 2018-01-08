package evolar.be.purolatorsmartsortMQTT;

import android.content.Context;
import android.graphics.Color;
import android.media.Image;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;

import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;

/**
 * Created by Dirk on 21/03/16.
 */
public class ConnectLabelsAdapter extends ArrayAdapter<GlassDeviceInfo>{

    public ConnectLabelsAdapter(Context context, ArrayList<GlassDeviceInfo> labels) {
        super(context, 0, labels);
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        GlassDeviceInfo label = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.connect_label, parent, false);
        }
        // Lookup view for data population
        ImageView statusView = (ImageView) convertView.findViewById(R.id.statusView);
        TextView deviceView = (TextView) convertView.findViewById(R.id.connectDeviceView);

        // Populate the data into the template view using the data object
        deviceView.setText(label.getDeviceName());

        convertView.setBackgroundColor(Color.LTGRAY);

        return convertView;
    }

    public ArrayList<GlassDeviceInfo> getLabels(){

        ArrayList<GlassDeviceInfo> labels = new ArrayList<GlassDeviceInfo>();
        for (int i=0;i<getCount();i++){
            labels.add(getItem(i));
        }
        return labels;
    }

}
