package io.honeyqa.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rengwuxian.materialedittext.MaterialEditText;

import io.honeyqa.client.HoneyQAClient;

/**
 * Created by devholic on 15. 10. 16..
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    boolean isInitialized = false;
    MaterialEditText api;
    RelativeLayout c1, c2, c3, c4, c5, crash, update;
    TextView s;
    String stack = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setResources();
    }

    private void setResources() {
        api = (MaterialEditText) findViewById(R.id.apikey);
        c1 = (RelativeLayout) findViewById(R.id.crumbA);
        c2 = (RelativeLayout) findViewById(R.id.crumbB);
        c3 = (RelativeLayout) findViewById(R.id.crumbC);
        c4 = (RelativeLayout) findViewById(R.id.crumbD);
        c5 = (RelativeLayout) findViewById(R.id.crumbE);
        crash = (RelativeLayout) findViewById(R.id.makeCrash);
        update = (RelativeLayout) findViewById(R.id.updateKey);
        s = (TextView) findViewById(R.id.stack);
        c1.setOnClickListener(this);
        c2.setOnClickListener(this);
        c3.setOnClickListener(this);
        c4.setOnClickListener(this);
        c5.setOnClickListener(this);
        crash.setOnClickListener(this);
        update.setOnClickListener(this);
    }

    private void crumb(String c) {
        if (isInitialized) {
            HoneyQAClient.leaveBreadcrumb("breadcrumb" + c);
            updateStack(c + " -> ");
        } else {
            Toast.makeText(MainActivity.this, "Please initialize HoneyQAClient with API Key", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStack(String c) {
        stack += c;
        s.setText(stack);
    }

    private void updateKey(String key) {
        if (key.length() != 0) {
            HoneyQAClient.InitializeAndStartSession(getApplicationContext(), key);
            isInitialized = true;
            // Clear Stack
            stack = "";
            s.setText("empty");
        } else {
            Toast.makeText(MainActivity.this, "Wrong API Key", Toast.LENGTH_SHORT).show();
        }
    }

    private void crash() {
        try {
            int y = 11 / 0;
        } catch (Exception e) {
            HoneyQAClient.SendException(e);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.crumbA:
                crumb("A");
                break;
            case R.id.crumbB:
                crumb("B");
                break;
            case R.id.crumbC:
                crumb("C");
                break;
            case R.id.crumbD:
                crumb("D");
                break;
            case R.id.crumbE:
                crumb("E");
                break;
            case R.id.updateKey:
                updateKey(api.getText().toString());
                break;
            case R.id.makeCrash:
                crash();
                break;
        }
    }
}
