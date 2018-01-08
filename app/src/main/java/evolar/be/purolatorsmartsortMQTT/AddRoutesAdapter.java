package evolar.be.purolatorsmartsortMQTT;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;

/**
 * Created by Dirk on 21/03/16.
 */
public class AddRoutesAdapter extends ArrayAdapter<PostalRoute>{

    static class ViewHolder{
        public TextView postalCodeView;
        public EditText editRoute;
    }

    public ListView mEditView;
    public Context mContext;

    public AddRoutesAdapter(Context context, ListView editView, ArrayList<PostalRoute> labels) {
        super(context, 0, labels);
        mEditView = editView;
        mContext = context;
    }


    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        // Get the data item for this position
        PostalRoute label = getItem(position);
        final ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.addroute_row, parent, false);
            // Lookup view for data population
            holder = new ViewHolder();


            holder.postalCodeView = (TextView) convertView.findViewById(R.id.postalView);
            holder.editRoute = (EditText) convertView.findViewById(R.id.editRoute);

            convertView.setTag(holder);


        } else{
            holder = (ViewHolder) convertView.getTag();
        }

        InputFilter setRouteFilters[]  = new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(3)};

        // Populate the data into the template view using the data object
        holder.postalCodeView.setText(label.getPostalCode());
        holder.editRoute.setText(label.getRoute());
        holder.editRoute.setFilters(setRouteFilters);
        holder.editRoute.setFocusableInTouchMode(true);
        holder.editRoute.setFocusable(true);



        holder.editRoute.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus){
                    String routeNumber = ((EditText)v).getText().toString();
                    getItem(position).setRoute(routeNumber);

                }
            }
        });


/*
        if (position == 0) {
            holder.editRoute.postDelayed(new Runnable() {
                public void run() {
                    holder.editRoute.requestFocus();
                    InputMethodManager lManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    lManager.showSoftInput(holder.editRoute, InputMethodManager.SHOW_FORCED);
                }
            }, 100);
        }
*/
        final PostalRoute fLabel = label;
        holder.editRoute.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_NEXT|| event.getKeyCode() == KeyEvent.KEYCODE_ENTER){
                    getItem(position).setRoute(holder.editRoute.getText().toString());


                    if (position + 1 != getLabels().size() && getItem(position).getRoute().length() == 3) {
                        mEditView.smoothScrollToPosition(position + 1);
                        /*
                        mEditView.postDelayed(new Runnable() {
                            public void run() {
                                EditText nextField = (EditText) holder.editRoute.focusSearch(View.FOCUS_RIGHT);
                                if (nextField != null) {
                                    nextField.requestFocus();
                                }
                            }
                        }, 200);
                        */
                        return false;
                    }
                    if (position + 1 == getLabels().size()){
                        return true;
                    }

                }
                return false;
            }
        });
/*
        holder.editRoute.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                if (s.length() == 3){

                    BaseInputConnection inputConnection = new BaseInputConnection(holder.editRoute, true);
                    inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));

                }

            }

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });
*/
        convertView.setBackgroundColor(Color.LTGRAY);

        return convertView;
    }

    public ArrayList<PostalRoute> getLabels(){

        ArrayList<PostalRoute> labels = new ArrayList<PostalRoute>();
        for (int i=0;i<getCount();i++){
            labels.add(getItem(i));
        }
        return labels;
    }


}
