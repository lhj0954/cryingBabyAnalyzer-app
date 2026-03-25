package com.example.cryingbabyanalyzerapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    Button btnHealthCheck;
    TextView txtResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnHealthCheck = findViewById(R.id.btnHealthCheck);
        txtResult = findViewById(R.id.txtResult);

        btnHealthCheck.setOnClickListener(v -> {
            txtResult.setText("요청 보내는 중...");

            new Thread(() -> {
                try {
                    URL url = new URL("http://10.0.2.2:8000/health");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream())
                    );

                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }

                    reader.close();
                    conn.disconnect();

                    runOnUiThread(() -> txtResult.setText(result.toString()));

                } catch (Exception e) {
                    runOnUiThread(() -> txtResult.setText("오류: " + e.getMessage()));
                }
            }).start();
        });
    }
}