package de.unima.ar.collector.api;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.android.gms.wearable.WearableListenerService;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import de.unima.ar.collector.MainActivity;
import de.unima.ar.collector.R;
import de.unima.ar.collector.controller.ActivityController;
import de.unima.ar.collector.controller.SQLDBController;
import de.unima.ar.collector.database.DatabaseHelper;
import de.unima.ar.collector.sensors.AccelerometerSensorCollector;
import de.unima.ar.collector.sensors.GravitySensorCollector;
import de.unima.ar.collector.sensors.GyroscopeSensorCollector;
import de.unima.ar.collector.sensors.LinearAccelerationSensorCollector;
import de.unima.ar.collector.sensors.MagneticFieldSensorCollector;
import de.unima.ar.collector.sensors.OrientationSensorCollector;
import de.unima.ar.collector.sensors.PressureSensorCollector;
import de.unima.ar.collector.sensors.RotationVectorSensorCollector;
import de.unima.ar.collector.sensors.StepDetectorSensorCollector;
import de.unima.ar.collector.shared.Settings;
import de.unima.ar.collector.shared.database.SQLTableName;
import de.unima.ar.collector.shared.util.Utils;
import de.unima.ar.collector.util.DBUtils;
import de.unima.ar.collector.util.StringUtils;

public class Tasks
{
    protected static void informThatWearableHasStarted(byte[] rawData, WearableListenerService wls)
    {
        // register device
        String[] tmp = StringUtils.convertByteArrayToString(rawData).split(Pattern.quote("~#X*X#~"));
        String deviceID = tmp[0];
        String deviceAddress = tmp[1];

        if(ListenerService.getDevices().contains(deviceID)) {
            return;
        }

        ListenerService.addDevice(deviceID, deviceAddress);

        // main activity started?
        MainActivity activity = (MainActivity) ActivityController.getInstance().get("MainActivity");
        if(activity == null) {
            Intent intent = new Intent(wls, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            wls.startActivity(intent);

            return;
        }

        // main activity already started so we have to create the sql tables "manually"
        boolean known = SQLDBController.getInstance().registerDevice(deviceID);
        if(!known) {
            DatabaseHelper.createDeviceDependentTables(deviceID);
        }

        // refresh UI
        activity.refreshMainScreenOverview();

        // inform mobile device
        Toast toast = Toast.makeText(activity, activity.getString(R.string.listener_app_connected), Toast.LENGTH_SHORT);
        toast.show();
    }


    protected static void informThatWearableHasDestroyed(byte[] rawData)
    {
        // unregister device
        String deviceID = StringUtils.convertByteArrayToString(rawData);
        ListenerService.rmDevice(deviceID);

        // refresh overview
        MainActivity activity = (MainActivity) ActivityController.getInstance().get("MainActivity");
        if(activity != null) {
            activity.refreshMainScreenOverview();

            // inform mobile device
            Toast toast = Toast.makeText(activity, activity.getString(R.string.listener_app_disconnected), Toast.LENGTH_SHORT);
            toast.show();
        }
    }


    protected static void updatePostureValue(byte[] rawData)
    {
        // parse data
        String posture = StringUtils.convertByteArrayToString(rawData);

        // update database
        boolean result = DBUtils.updateActivity(posture, SQLTableName.POSTUREDATA, SQLTableName.POSTURES);
        if(!result) {
            return;
        }

        // update UI
        MainActivity mc = (MainActivity) ActivityController.getInstance().get("MainActivity");
        if(mc != null) {
            mc.refreshActivityScreenPosture(posture);
        }
    }


    protected static void updatePositionValue(byte[] rawData)
    {
        // parse data
        String position = StringUtils.convertByteArrayToString(rawData);

        // update database
        boolean result = DBUtils.updateActivity(position, SQLTableName.POSITIONDATA, SQLTableName.POSITIONS);
        if(!result) {
            return;
        }

        // update UI
        MainActivity mc = (MainActivity) ActivityController.getInstance().get("MainActivity");
        if(mc != null) {
            mc.refreshActivityScreenPosition(position);
        }
    }


    protected static void updateActivityValue(byte[] rawData)
    {
        // parse data
        String data = StringUtils.convertByteArrayToString(rawData);

        String activity = data;
        String subActivity = null;

        if(data.contains(Settings.ACTIVITY_DELIMITER)) {  //subactivity?
            subActivity = data.substring(data.indexOf(Settings.ACTIVITY_DELIMITER) + Settings.ACTIVITY_DELIMITER.length());
            activity = data.substring(0, data.indexOf(Settings.ACTIVITY_DELIMITER));
        }

        // update database
        String activityId = DatabaseHelper.getStringResultSet("SELECT id FROM " + SQLTableName.ACTIVITIES + " WHERE name = ? ", new String[]{ activity }).get(0);
        String subActivityID = null;
        if(subActivity != null) {
            subActivityID = DatabaseHelper.getStringResultSet("SELECT id FROM " + SQLTableName.SUBACTIVITIES + " WHERE name = ? AND activityid = ? ", new String[]{ subActivity, activityId }).get(0);
        }

        ContentValues newValues = new ContentValues();
        newValues.put("activityid", activityId);
        newValues.put("subactivityid", subActivityID);
        newValues.put("starttime", System.currentTimeMillis());
        newValues.put("endtime", 0);
        SQLDBController.getInstance().insert(SQLTableName.ACTIVITYDATA, null, newValues);

        // update UI
        MainActivity mc = (MainActivity) ActivityController.getInstance().get("MainActivity");
        if(mc != null) {
            mc.refreshActivityScreenActivity(data, true);
        }
    }


    protected static void deleteActivityValue(byte[] rawData)
    {
        // parse data
        String data = StringUtils.convertByteArrayToString(rawData);

        String activity = data;
        String subActivity = null;

        if(data.contains(Settings.ACTIVITY_DELIMITER)) {  //subactivity?
            subActivity = data.substring(data.indexOf(Settings.ACTIVITY_DELIMITER) + Settings.ACTIVITY_DELIMITER.length());
            activity = data.substring(0, data.indexOf(Settings.ACTIVITY_DELIMITER));
        }

        // update database
        String activityId = DatabaseHelper.getStringResultSet("SELECT id FROM " + SQLTableName.ACTIVITIES + " WHERE name = ? ", new String[]{ activity }).get(0);
        String subActivityID = null;
        if(subActivity != null) {
            subActivityID = DatabaseHelper.getStringResultSet("SELECT id FROM " + SQLTableName.SUBACTIVITIES + " WHERE name = ? AND activityid = ? ", new String[]{ subActivity, activityId }).get(0);
        }

        // update database
        ContentValues args = new ContentValues();
        args.put("endtime", System.currentTimeMillis());
        SQLDBController.getInstance().update(SQLTableName.ACTIVITYDATA, args, "activityid = ? AND (subactivityid = ? OR subactivityid is NULL) AND endtime = 0", new String[]{ activityId, subActivityID });

        // update UI
        MainActivity mc = (MainActivity) ActivityController.getInstance().get("MainActivity");
        if(mc != null) {
            mc.refreshActivityScreenActivity(data, false);
        }
    }


    protected static void processDatabaseRequest(String key, byte[] rawData)
    {
        try {
            StringBuilder sb = new StringBuilder();
            key = key.substring(key.lastIndexOf("/") + 1);
            String queryString = StringUtils.convertByteArrayToString(rawData);

            String delimiterColumn = Settings.DATABASE_DELIMITER;

            List<String[]> result = SQLDBController.getInstance().query(queryString, null, false);

            for(String[] row : result) {
                for(String value : row) {
                    sb.append(value);
                    sb.append(delimiterColumn);
                }
                sb = sb.delete(sb.length() - delimiterColumn.length(), sb.length());
                sb.append("\n"); // lineSeparator not supported
            }

            if(sb.length() != 0) {
                sb = sb.delete(sb.length() - ("\n").length(), sb.length());
            }

            BroadcastService.getInstance().sendMessage("/database/response/" + key, sb.toString());
        } catch(Exception e) {
            e.printStackTrace();

            // Check if UI is available
            Context context = ActivityController.getInstance().get("MainActivity");
            if(context != null) {
                Toast.makeText(context, context.getString(R.string.listener_database_failed), Toast.LENGTH_LONG).show();
            }
        }
    }


    protected static void processIncomingSensorData(String path, byte[] rawData)
    {
        if(SQLDBController.getInstance() == null) {
            return;
        }

        path = path.substring("/sensor/data/".length());

        String deviceID = path.substring(0, path.indexOf("/"));
        int type = Integer.valueOf(path.substring(path.indexOf("/") + 1));

        String data = StringUtils.convertByteArrayToString(rawData);
        String[] entries = StringUtils.split(data);
        ContentValues newValues = new ContentValues();
        for(int i = 0; i < entries.length; i += 2) {
            newValues.put(entries[i], entries[i + 1]);
        }

        switch(type) {
            case 1:
                AccelerometerSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                AccelerometerSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 2:
                MagneticFieldSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                MagneticFieldSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 3:
                OrientationSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                OrientationSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 4:
                GyroscopeSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                GyroscopeSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 6:
                PressureSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]) });
                PressureSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 9:
                GravitySensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                GravitySensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 10:
                LinearAccelerationSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                LinearAccelerationSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 11:
                RotationVectorSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]), Float.valueOf(entries[3]), Float.valueOf(entries[5]) });
                RotationVectorSensorCollector.writeDBStorage(deviceID, newValues);
                break;
            case 18:
                StepDetectorSensorCollector.updateLivePlotter(deviceID, new float[]{ Float.valueOf(entries[1]) });
                StepDetectorSensorCollector.writeDBStorage(deviceID, newValues);
                break;
        }
    }


    protected static void processIncomingSensorBlob(String path, byte[] rawData)
    {
        BroadcastService.getInstance().sendMessage("/sensor/blob/confirm/" + Arrays.hashCode(rawData), "");

        Object object = Utils.compressedByteArrayToObject(rawData);
        if(object == null || !(object instanceof List<?>) || ((List<?>) object).size() <= 1) {
            return;
        }

        List<String[]> entries = Utils.safeListCast((List<?>) object, String[].class);

        String[] header = entries.get(0);
        for(int i = 1; i < entries.size(); i++) {
            String[] entry = entries.get(i);
            String record = "";

            for(int j = 1; j < entry.length; j++) {
                record += header[j] + ";" + entry[j] + ";";
            }
            record = record.substring(0, record.length() - 1);

            Tasks.processIncomingSensorData(path.replace("blob", "data"), record.getBytes());
        }
    }
}