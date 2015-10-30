package io.honeyqa.example;

import io.honeyqa.client.HoneyQAClient;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Created by devholic on 15. 10. 16..
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        HoneyQAClient.InitializeAndStartSession(getApplicationContext(), "");
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/NanumBarunGothicUltraLight.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build());
    }
}
