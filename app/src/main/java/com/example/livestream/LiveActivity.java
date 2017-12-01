package com.example.livestream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LiveActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback{
    // Open Camera
    private Camera myCamera;
    private SurfaceView myView;
    private SurfaceHolder myHolder;
    private int myCameraId = 0;
    private int width = 1280;
    private int height = 720;
    private Button shootButton;

    private String name = "sjtu";
    private String password = "25d55ad283aa400af464c76d713c07ad";
    private String session_id = null;
    private String LOGIN_URL = "http://120.55.164.30:11180/business/api/login";
    private String REPO_URL = "http://120.55.164.30:11180/business/api/repository";
    private String DETECT_URL = "http://120.55.164.30:11180/business/api/repository/picture/synchronized";

    // Push Stream
    //private String ffmpeg_link = Environment.getExternalStorageDirectory() + "/test.flv";
    private String ffmpeg_link = "rtmp://59.78.30.20:9090/live/stream";
    private FFmpegFrameRecorder recorder;
    private Frame yuvImage;
    private boolean isRecording = false;
    private long startTime = 0;
    private int sampleRate  = 44100;
    private int frameRate = 30;

    private int w = 0;
    private int h = 0;
    private int x = 0;
    private int y = 0;

    private ByteArrayOutputStream out = null;

    /*
    private Thread audioThread;
    private boolean runAudioThread = true;
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);

        shootButton = (Button) findViewById(R.id.shoot_button);
        shootButton.setText("start");
        shootButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    Log.v("hhh","start start");
                    startRecord();
                    Log.v("hhh","end start");
                    shootButton.setText("Stop");
                } else {
                    Log.v("hhh","start stop");
                    stopRecord();
                    Log.v("hhh","end stop");
                    shootButton.setText("Start");
                }
            }
        });

        myView = (SurfaceView) findViewById(R.id.liveView);
        myHolder = myView.getHolder();
        myHolder.setFixedSize(width, height);
        myHolder.addCallback(this);
        Log.v("hhh","Create success");
    }

    public void startRecord() {
        yuvImage = new Frame(width, height, Frame.DEPTH_UBYTE, 2);

        recorder = new FFmpegFrameRecorder(ffmpeg_link, width, height, 1);

        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("flv");
        recorder.setSampleRate(sampleRate);
        recorder.setFrameRate(frameRate);
        //recorder.setVideoBitrate(800000);
/*
        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
        runAudioThread = true;
        */
        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            //audioThread.start();
            isRecording = true;
        } catch (FFmpegFrameRecorder.Exception e){
            e.printStackTrace();
        }

        // login and get session_id
        String params = "{\"name\":\"" + name + "\", \"password\":\"" + password + "\"}";
        try {
            String rst = new login().execute(params, session_id).get();
            JSONObject jsonObject = new JSONObject(rst);
            session_id = jsonObject.getString("session_id");
            Log.v("aaa", session_id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new detect(out, session_id).execute();
            }
        }, 0, 3000);
    }

    public void stopRecord() {
        /*
        runAudioThread = false;
        try {
            audioThread.join();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        audioRecordRunnable = null;
        audioThread = null;*/

        if (recorder != null && isRecording) {
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
            isRecording = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Quit for back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isRecording) {
                stopRecord();
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (yuvImage != null && isRecording) {
            Camera.Parameters parameters = camera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;

            // YUV -> ARGB
            YuvImage yuv = new YuvImage(bytes, parameters.getPreviewFormat(), width, height, null);
            out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            Bitmap bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.toByteArray().length, options);


            // Add box
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(x, y, x+w, y+h, paint);

            // ARGB -> YUV
            int [] argb = new int[width * height];
            bmp.getPixels(argb, 0, width, 0, 0, width, height);
            byte [] yuv2 = new byte[width*height*3/2];
            encodeYUV420SP(yuv2, argb, width, height);
            bmp.recycle();

            long videoTimestamp = 1000 * (System.currentTimeMillis() - startTime);
            ((ByteBuffer)yuvImage.image[0].position(0)).put(yuv2);
            try {
                recorder.setTimestamp(videoTimestamp);
                Log.v("hhh","to record");
                recorder.record(yuvImage);
                Log.v("hhh","recorded");
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        initCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        destroyCamera();
    }

    private void initCamera() {
        try {
            myCamera = Camera.open(myCameraId);
            myCamera.setPreviewDisplay(myHolder);
            myCamera.setDisplayOrientation(90);
            Camera.Parameters params = myCamera.getParameters();
            params.setPreviewSize(width, height);
            params.setPictureSize(width, height);
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            myCamera.setParameters(params);
            myCamera.setPreviewCallback(this);
            myCamera.startPreview();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void destroyCamera() {
        if (myCamera == null) {
            return;
        }

        myCamera.setPreviewCallback(null);
        myCamera.stopPreview();
        myCamera.release();
        myCamera = null;
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                }

                index ++;
            }
        }
    }
/*
    private class AudioRecordRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioData = new short[bufferSize];

            audioRecord.startRecording();

            while (runAudioThread) {
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    if (isRecording) {
                        try {
                            recorder.recordSamples(ShortBuffer.wrap(audioData, 0,
                                    bufferReadResult));
                        } catch (FFmpegFrameRecorder.Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        }
    }
*/

    private class login extends AsyncTask<String, Void, String> {
        RequestHandler rh = new RequestHandler();

        @Override
        protected String doInBackground(String... params) {
            return rh.sendPostRequest(LOGIN_URL, params[0], params[1]);
        }
    }

    private class get_repo extends AsyncTask<String, Void, String> {
        RequestHandler rh = new RequestHandler();

        @Override
        protected String doInBackground(String... params) {
            return rh.sendGetRequest("http://120.55.164.30:11180/business/api/repository",
                    params[0]);
        }
    }

    private class detect extends AsyncTask<Void, Void, Void> {
        ByteArrayOutputStream out;
        String session_id;

        detect(ByteArrayOutputStream out, String session_id) {
            this.out = out;
            this.session_id = session_id;
        }

        RequestHandler rh = new RequestHandler();
        @Override
        protected Void doInBackground(Void... voids) {
            if (session_id != null && out != null) {
                String image = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                try {
                    String params = "{\"picture_image_content_base64\": \"" + image + "\"}";
                    String rst = rh.sendPostRequest(DETECT_URL, params, session_id);
                    JSONObject jsonObject = new JSONObject(rst);
                    if (!jsonObject.isNull("results")) {
                        w = jsonObject.getJSONArray("results").getJSONObject(0).getJSONObject("face_rect").getInt("w");
                        h = jsonObject.getJSONArray("results").getJSONObject(0).getJSONObject("face_rect").getInt("h");
                        x = jsonObject.getJSONArray("results").getJSONObject(0).getJSONObject("face_rect").getInt("x");
                        y = jsonObject.getJSONArray("results").getJSONObject(0).getJSONObject("face_rect").getInt("y");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            } else {
                return null;
            }
        }
    }

    private class RequestHandler {
        private String sendPostRequest(String requestURL, String postData, String session_id) {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/octet-stream");
            RequestBody body = RequestBody.create(mediaType, postData);
            Request request;
            if (session_id == null) {
                request = new Request.Builder()
                        .url(requestURL)
                        .post(body)
                        .build();
            } else {
                request = new Request.Builder()
                        .url(requestURL)
                        .post(body)
                        .addHeader("session_id", session_id)
                        .build();
            }
            try {
                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Error";
        }
        private String sendGetRequest(String requestURL, String session_id) {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(requestURL)
                    .addHeader("session_id", session_id)
                    .build();
            try {
                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Error";
        }
    }
}
