package com.example.livestream;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private int REQUEST_PERMISSION_CAMERA_CODE = 0x66;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.liveButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    Intent liveIntent = new Intent(MainActivity.this, LiveActivity.class);
                    startActivity(liveIntent);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_PERMISSION_CAMERA_CODE);
                }
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode==REQUEST_PERMISSION_CAMERA_CODE) {
            if (grantResults.length >=1) {
                int cameraResult = grantResults[0];
                if (cameraResult == PackageManager.PERMISSION_GRANTED) {
                    Intent liveIntent = new Intent(MainActivity.this, LiveActivity.class);
                    startActivity(liveIntent);
                }
            }
        }
    }
}

