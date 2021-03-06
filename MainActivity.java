package project.thesis.vgu.drive;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LabelFormatter;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    static final String TAG = "drive";
    TextView statusText, fromText, toText;
    Calendar startTime, endTime;
    GraphView graph;
    LineGraphSeries<DataPoint> condSeries, phSeries, tempSeries;
    DataProcessingTask dataProcessingTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.status);
        fromText = findViewById(R.id.from);
        toText = findViewById(R.id.to);
        startTime = Calendar.getInstance();
        endTime = (Calendar) startTime.clone();
        fromText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(MainActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        startTime.set(year, month, dayOfMonth);
                        fromText.setText(String.format("%1$tY/%<tm/%<td %<tR", startTime));
                        new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                startTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                startTime.set(Calendar.MINUTE, minute);
                                fromText.setText(String.format("%1$tY/%<tm/%<td %<tR", startTime));
                            }
                        }, startTime.get(Calendar.HOUR_OF_DAY), startTime.get(Calendar.MINUTE), true).show();
                    }
                }, startTime.get(Calendar.YEAR), startTime.get(Calendar.MONTH), startTime.get(Calendar.DATE)).show();
            }
        });
        toText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(MainActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        endTime.set(year, month, dayOfMonth);
                        toText.setText(String.format("%1$tY/%<tm/%<td %<tR", endTime));
                        new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                endTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                endTime.set(Calendar.MINUTE, minute);
                                toText.setText(String.format("%1$tY/%<tm/%<td %<tR", endTime));
                            }
                        }, endTime.get(Calendar.HOUR_OF_DAY), endTime.get(Calendar.MINUTE), true).show();
                    }
                }, endTime.get(Calendar.YEAR), endTime.get(Calendar.MONTH), endTime.get(Calendar.DATE)).show();
            }
        });
        fromText.setText(String.format("%1$tY/%<tm/%<td %<tR", startTime));
        toText.setText(fromText.getText());
        graph = findViewById(R.id.graph);
        graph.getViewport().setScalable(true);

        condSeries = new LineGraphSeries<>();
        phSeries = new LineGraphSeries<>();
        tempSeries = new LineGraphSeries<>();
        condSeries.setDrawDataPoints(true);
        condSeries.setColor(0xff33b5e5);
        phSeries.setDrawDataPoints(true);
        phSeries.setColor(0xffaa66cc);
        tempSeries.setDrawDataPoints(true);
        tempSeries.setColor(0xffffbb33);
//        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        graph.addSeries(condSeries);
        graph.addSeries(phSeries);
        graph.addSeries(tempSeries);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getGridLabelRenderer().setLabelFormatter(new LabelFormatter() {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd\nHH:mm:ss");

            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    Log.e(TAG, "formatLabel" + value);
                    return dateFormat.format(new Date((long) value));
                }
                return "" + value;
            }

            @Override
            public void setViewport(Viewport viewport) {

            }
        });
//        graph.getGridLabelRenderer().setHumanRounding(false);
    }

    public void executeTask(View v) {
        Button button = (Button) v;
        if (button.getText().equals("check")) {
            if (startTime.after(endTime)) {
                statusText.setText("Start Time must not be later than End Time");
                return;
            }
            dataProcessingTask = new DataProcessingTask(statusText, button);
            dataProcessingTask.appContext = getApplicationContext();
            dataProcessingTask.graph = graph;
            dataProcessingTask.condSeries = condSeries;
            dataProcessingTask.phSeries = phSeries;
            dataProcessingTask.tempSeries = tempSeries;
            button.setText("cancel");
            dataProcessingTask.execute(startTime.clone(), endTime.clone());
        } else dataProcessingTask.cancel(true);
    }

    static class DataProcessingTask extends AsyncTask<Object, String, Void> {
        String token, appDirectory;
        Context appContext;
        WeakReference<TextView> statusTextReference;
        WeakReference<Button> buttonReference;
        LineGraphSeries<DataPoint> condSeries, phSeries, tempSeries;
        GraphView graph;

        public DataProcessingTask(TextView statusText, Button button) {
            statusTextReference = new WeakReference<TextView>(statusText);
            buttonReference = new WeakReference<Button>(button);
            token = (String) statusText.getTag();
        }

        void getToken(HttpsURLConnection connection, InputStream stream) {
            publishProgress(null, "getting token...");
            int c;
            StringBuilder s;
            byte[] tokenRequestBody = "client_id=463875113005-icovngqrabn2hass5tug5ik5m436ks2k.apps.googleusercontent.com&client_secret=8PWn96NTst2-rbkaXToWoi6F&refresh_token=1/VRNPHjn46h-4vuR8Emw754daGgEx9VmCFWFWronfIO8&grant_type=refresh_token".getBytes();
            for (; ; ) {
                if (isCancelled()) return;
                try {
                    connection = (HttpsURLConnection) new URL("https://www.googleapis.com/oauth2/v4/token").openConnection();
                    connection.setDoOutput(true);
                    connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.setFixedLengthStreamingMode(tokenRequestBody.length);
                    connection.getOutputStream().write(tokenRequestBody);
                    s = new StringBuilder();
                    if (connection.getResponseCode() == 200) {
                        stream = connection.getInputStream();
                        while ((c = stream.read()) != -1) {
                            if (c == 107 && stream.read() == 101 && stream.read() == 110 && stream.read() == 34 && stream.read() == 58) {
                                stream.read();
                                stream.read();
                                while ((c = stream.read()) != 34) s.append((char) c);
                                break;
                            }
                        }
                        stream.close();
                        token = "Bearer " + s.toString();
                        Log.e(TAG, token);
                        publishProgress(null, "token refreshed");
                    } else {
                        Log.e(TAG, "refreshToken fail");
                        Toast.makeText(appContext, "Failed to get Token", Toast.LENGTH_SHORT).show();
                        cancel(true);
                    }
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "refreshToken exception");
                    e.printStackTrace();
                    publishProgress(null, "getting token error, retrying...");
                    SystemClock.sleep(5000);
                }
            }
        }

        @Override
        protected Void doInBackground(Object... params) {
            StringBuilder s = new StringBuilder("https://www.googleapis.com/drive/v3/files?fields=files(id,name)&q=");
            Calendar startTime = (Calendar) params[0];
            int i = 0;
            condData = new ArrayList<>();
            phData = new ArrayList<>();
            tempData = new ArrayList<>();
            String name;
            appDirectory = appContext.getExternalFilesDir(null).getPath();
            List<String> incompleteFiles;
            String incompleteFilesJson = PreferenceManager.getDefaultSharedPreferences(appContext).getString("incompleteFiles", null);
            if (incompleteFilesJson != null)
                incompleteFiles = new Gson().fromJson(incompleteFilesJson, new TypeToken<List<String>>() {
                }.getType());
            else incompleteFiles = new ArrayList<>();
            while (!startTime.after(params[1])) {
                name = String.format("%1$tY%<tm%<td%<tH%<tM", startTime);
                if (!new File(appDirectory, name).exists() || incompleteFiles.contains(name)) {
                    if (i++ == 0) s.append("name%3D%27");
                    else s.append("%27orname%3D%27");
                    s.append(name);
                } else publishProgress(name, null);
                startTime.add(Calendar.MINUTE, 1);
            }
            if (i == 0) {
                Log.e(TAG, "all files exist");
                return null;
            }
            s.append("%27");

            InputStream stream;
            HttpsURLConnection connection;
            int c;
            List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            boolean isId = true;
            String searchUrl = s.toString();

            if (token == null) getToken(null, null);
            publishProgress(null, "searching for files...");
            for (; ; ) {
                if (isCancelled()) return null;
                try {
                    connection = (HttpsURLConnection) new URL(searchUrl).openConnection();
                    connection.addRequestProperty("Authorization", token);
                    if (connection.getResponseCode() == 200) {
                        stream = connection.getInputStream();
                        while ((c = stream.read()) != -1) {
                            if (c == 58 && stream.read() == 32 && stream.read() == 34) {
                                s = new StringBuilder();
                                while ((c = stream.read()) != 34) s.append((char) c);
                                if (isId) {
                                    isId = false;
                                    ids.add(s.toString());
                                } else {
                                    isId = true;
                                    names.add(s.toString());
                                }
                            }
                        }
                        stream.close();
                        if (names.size() == 0) {
                            Log.e(TAG, "found nothing");
                            return null;
                        }
                        byte[] buffer = new byte[1024];
                        publishProgress(null, "Downloading 0/" + ids.size() + " files");
                        OutputStream fileOutputStream;
                        for (i = 0; i < ids.size(); i++) {
                            name = names.get(i);
                            Log.e(TAG, name + ": " + ids.get(i));
                            for (; ; ) {
                                if (isCancelled()) return null;
                                try {
                                    connection = (HttpsURLConnection) new URL("https://www.googleapis.com/drive/v3/files/" + ids.get(i) + "/export?mimeType=text/plain").openConnection();
                                    connection.addRequestProperty("Authorization", token);
                                    if (connection.getResponseCode() == 200) {
                                        stream = connection.getInputStream();
                                        fileOutputStream = new FileOutputStream(new File(appDirectory, name));
                                        while ((c = stream.read(buffer)) != -1)
                                            fileOutputStream.write(buffer, 0, c);
                                        fileOutputStream.close();
                                        stream.close();
                                        publishProgress(name, null);
                                        publishProgress(null, "Downloading " + (i + 1) + "/" + ids.size() + " files");
                                        incompleteFiles.remove(name);
                                        if (i == 0 && (incompleteFiles.size() == 0 || name.compareTo(incompleteFiles.get(incompleteFiles.size() - 1)) >= 0))
                                            incompleteFiles.add(name);
                                        break;
                                    } else {
                                        getToken(connection, stream);
                                    }
                                } catch (IOException e) {
                                    Log.e(TAG, "download file " + names.get(i) + " exception");
                                    e.printStackTrace();
                                    publishProgress(null, "download file error, retrying");
                                    SystemClock.sleep(5000);
                                }
                            }
                        }
                        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putString("incompleteFiles", new Gson().toJson(incompleteFiles)).apply();
                        break;
                    } else {
                        stream = connection.getErrorStream();
                        s = new StringBuilder();
                        while ((c = stream.read()) != -1) s.append((char) c);
                        Log.e(TAG, "error stream: " + s.toString());
                        stream.close();
                        publishProgress(null, "searching files failed, reacquiring token");
                        getToken(connection, stream);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "searching files exception");
                    publishProgress(null, "searching files error, retrying");
                    e.printStackTrace();
                    SystemClock.sleep(5000);
                }
            }
            return null;
        }

        List<DataPoint> condData, phData, tempData;
        BufferedReader bufferedReader;
        String line;
        String[] tokens;
        double time;

        @Override
        protected void onProgressUpdate(String... values) {
            if (!isCancelled()) {
                if (values[0] == null) statusTextReference.get().setText(values[1]);
                else {
                    Log.e(TAG, "file " + values[0] + " done");
                    try {
                        bufferedReader = new BufferedReader(new FileReader(new File(appDirectory, values[0])));
                        bufferedReader.read();
                        while ((line = bufferedReader.readLine()) != null) {
                            tokens = line.split(",");
                            time = Double.parseDouble(tokens[0] + "000");
                            condData.add(new DataPoint(time, Double.parseDouble(tokens[1])));
                            phData.add(new DataPoint(time, Double.parseDouble(tokens[2])));
                            tempData.add(new DataPoint(time, Double.parseDouble(tokens[3])));
                        }
                        bufferedReader.close();
                    } catch (IOException e) {
                        Log.e(TAG, "file read error");
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void s) {
            TextView statusText = statusTextReference.get();
            statusText.setTag(token);
            if (condData.isEmpty()) statusText.setText("found no files");
            else {
                statusText.setText(null);
                Comparator<DataPoint> comparator = new Comparator<DataPoint>() {
                    @Override
                    public int compare(DataPoint o1, DataPoint o2) {
                        return o1.getX() < o2.getX() ? -1 : 1;
                    }
                };
                Collections.sort(condData, comparator);
                Collections.sort(phData, comparator);
                Collections.sort(tempData, comparator);
                graph.getViewport().setMaxX(condData.get(condData.size() - 1).getX());
                graph.getViewport().setMinX(condData.get(0).getX());
                condSeries.resetData(condData.toArray(new DataPoint[condData.size()]));
                phSeries.resetData(phData.toArray(new DataPoint[phData.size()]));
                tempSeries.resetData(tempData.toArray(new DataPoint[tempData.size()]));
            }
            buttonReference.get().setText("check");
        }

        @Override
        protected void onCancelled(Void s) {
            Button button = buttonReference.get();
            if (button != null) {
                button.setText("check");
                statusTextReference.get().setText(null);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (dataProcessingTask != null) dataProcessingTask.cancel(true);
        super.onDestroy();
    }
}
//https://accounts.google.com/o/oauth2/v2/auth?scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file&response_type=code&redirect_uri=urn:ietf:wg:oauth:2.0:oob&client_id=463875113005-1att4hu76j0ac7ta17mdjniinfgg2di2.apps.googleusercontent.com
//1/0gh8dGi_1bkC-g5BFGlvRRJHKFwUYjQMpW59nv56ZVA

//client_id=463875113005-icovngqrabn2hass5tug5ik5m436ks2k.apps.googleusercontent.com&client_secret=8PWn96NTst2-rbkaXToWoi6F&refresh_token=1/7C-dMwDk771wT5lads8os4_mziPZspcIU6ndw_ZJpi4&grant_type=refresh_token
//4/FwG9vGUJa37B48T-SRWo4_PxxBxJNg_vbWul86O7ICEQHqEgg36SjGYnl84Ls2VQBsw5-hYzbFlMDhtYjKaFlFs
//1/VRNPHjn46h-4vuR8Emw754daGgEx9VmCFWFWronfIO8