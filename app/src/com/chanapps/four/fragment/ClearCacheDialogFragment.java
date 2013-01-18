package com.chanapps.four.fragment;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import com.chanapps.four.activity.BoardSelectorActivity;
import com.chanapps.four.activity.R;
import com.chanapps.four.component.ChanNotificationManager;
import com.chanapps.four.data.ChanFileStorage;

/**
* Created with IntelliJ IDEA.
* User: arley
* Date: 12/14/12
* Time: 12:44 PM
* To change this template use File | Settings | File Templates.
*/
public class ClearCacheDialogFragment extends DialogFragment {

    public static final String TAG = ClearCacheDialogFragment.class.getSimpleName();

    private static final boolean DEBUG = false;

    private SettingsFragment fragment;

    private static ClearCacheAsyncTask clearCacheAsyncTask;

    public ClearCacheDialogFragment(SettingsFragment fragment) {
        super();
        this.fragment = fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return (new AlertDialog.Builder(getActivity()))
                .setMessage(R.string.dialog_clear_cache_confirm)
                .setPositiveButton(R.string.dialog_clear,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                (new ClearCacheAsyncTask(getActivity())).execute();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // ignore
                            }
                        })
                .create();
    }

    private static class ClearCacheAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private static boolean runningDelete = false;

        public ClearCacheAsyncTask() {}

        public ClearCacheAsyncTask(Context context) {
            this.context = context;
        }

        @Override
        public void onPreExecute() {
            if (runningDelete)
                Toast.makeText(context, R.string.pref_clear_cache_already_running, Toast.LENGTH_SHORT).show();
            else
                if (ChanNotificationManager.getInstance(context).isEnabled())
                    Toast.makeText(context, R.string.pref_clear_cache_pre_execute, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(context, R.string.pref_clear_cache_pre_execute_no_notify, Toast.LENGTH_SHORT).show();
        }

        @Override
        public String doInBackground(Void... params) {
            if (runningDelete)
                return null;
            String contentText;
            if (ChanFileStorage.deleteCacheDirectory(context)) {
                if (DEBUG) Log.i(TAG, "Successfully cleared cache");
                contentText = context.getString(R.string.pref_clear_cache_success);
            }
            else {
                Log.e(TAG, "Failed to run clear cache command");
                contentText = context.getString(R.string.pref_clear_cache_error);
            }
            return contentText;
        }

        @Override
        public void onCancelled() {
            runningDelete = false;
            if (DEBUG) Log.i(TAG, "Cancelled clear cache");
            ChanNotificationManager manager = ChanNotificationManager.getInstance(context);
            if (manager.isEnabled())
                manager.sendNotification(
                        ChanNotificationManager.CLEAR_CACHE_NOTIFY_ID,
                        makeNotification(context.getString(R.string.pref_clear_cache_error)));
            else
                Toast.makeText(context, R.string.pref_clear_cache_error, Toast.LENGTH_LONG);
        }

        @Override
        public void onPostExecute(String result) {
            runningDelete = false;
            if (result != null) {
                if (DEBUG) Log.i(TAG, "Post execute with clear cache result=" + result);
                ChanNotificationManager manager = ChanNotificationManager.getInstance(context);
                if (manager.isEnabled())
                    manager.sendNotification(
                            ChanNotificationManager.CLEAR_CACHE_NOTIFY_ID,
                            makeNotification(result));
                else
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            }
        }

        private Notification makeNotification(String contentText) {
            Intent intent = new Intent(context, BoardSelectorActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.four_leaf_clover_1)
                    .setContentTitle(context.getString(R.string.pref_clear_cache_notification_title))
                    .setContentText(contentText)
                    .setContentIntent(pendingIntent)
                    .build();
            return notification;
        }
    }

}
