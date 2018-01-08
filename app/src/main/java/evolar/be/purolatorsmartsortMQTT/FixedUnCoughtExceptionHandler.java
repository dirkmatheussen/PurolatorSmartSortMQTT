package evolar.be.purolatorsmartsortMQTT;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Dirk on 6/06/17.
 */

public class FixedUnCoughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private Activity activity;

    public FixedUnCoughtExceptionHandler(Activity a) {
        activity = a;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {


        //first make all data persistent
        ((PurolatorActivityFixed) activity).storeData(true);

        Intent intent = new Intent(activity, PurolatorActivityFixed.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(PurolatorSmartsortMQTT.getsInstance().getBaseContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager mgr = (AlarmManager) PurolatorSmartsortMQTT.getsInstance().getBaseContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);

        activity.finish();
        System.exit(2);

    }
}
