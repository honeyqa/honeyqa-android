package io.honeyqa.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import io.honeyqa.client.HoneyQAClient;

/**
 * Created by devholic on 15. 10. 16..
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    RelativeLayout c1, c2, c3, c4, c5, crash;
    TextView s;
    String stack = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setResources();
    }

    private void setResources() {
        c1 = (RelativeLayout) findViewById(R.id.crumbA);
        c2 = (RelativeLayout) findViewById(R.id.crumbB);
        c3 = (RelativeLayout) findViewById(R.id.crumbC);
        c4 = (RelativeLayout) findViewById(R.id.crumbD);
        c5 = (RelativeLayout) findViewById(R.id.crumbE);
        crash = (RelativeLayout) findViewById(R.id.makeCrash);
        s = (TextView) findViewById(R.id.stack);
        c1.setOnClickListener(this);
        c2.setOnClickListener(this);
        c3.setOnClickListener(this);
        c4.setOnClickListener(this);
        c5.setOnClickListener(this);
        crash.setOnClickListener(this);
    }

    private void updateStack(String c) {
        stack += c;
        s.setText(stack);
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
                HoneyQAClient.leaveBreadcrumb("breadcrumbA");
                updateStack("A -> ");
                break;
            case R.id.crumbB:
                HoneyQAClient.leaveBreadcrumb("breadcrumbB");
                updateStack("B -> ");
                break;
            case R.id.crumbC:
                HoneyQAClient.leaveBreadcrumb("breadcrumbC");
                updateStack("C -> ");
                break;
            case R.id.crumbD:
                HoneyQAClient.leaveBreadcrumb("breadcrumbD");
                updateStack("D -> ");
                break;
            case R.id.crumbE:
                HoneyQAClient.leaveBreadcrumb("breadcrumbE");
                updateStack("E -> ");
                break;
            case R.id.makeCrash:
                crash();
                break;
        }
    }
}
