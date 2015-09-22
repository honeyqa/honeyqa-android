package io.honeyqa.client;

import java.io.File;

import org.json.JSONException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import io.honeyqa.client.collector.DeviceCollector;
import io.honeyqa.client.collector.ErrorReport;
import io.honeyqa.client.collector.ErrorReportFactory;
import io.honeyqa.client.common.Encryptor;
import io.honeyqa.client.common.Sender;
import io.honeyqa.client.common.HoneyQAData;
import io.honeyqa.client.eventpath.EventPathManager;
import io.honeyqa.client.library.UncaughtExceptionHandler;
import io.honeyqa.client.library.model.Authentication;
import io.honeyqa.client.rank.ErrorRank;


public final class HoneyQAController {

    /**
     * Create breadcrumb for log tracing
     */
    public static void leaveBreadcrumb() {
        EventPathManager.CreateEventPath(2, "");
    }

    /**
     * Create tagged breadcrumb for log tracing
     */
    public static void leaveBreadcrumb(String tag) {
        EventPathManager.CreateEventPath(2, tag);
    }

    /**
     * Create native error report and send to server
     *
     * @return int 0
     */
    public static int NativeCrashCallback(String fileName) {
        ErrorReport report = ErrorReportFactory.CreateNativeErrorReport(HoneyQAData.AppContext);
        Sender.sendExceptionWithNative(report, Sender.NATIVE_EXCEPTION_URL, fileName);
        return 0;
    }

    /**
     * Reset Token
     */
    public static void resetTokens(Context context) {
        try {
            // request token
            Encryptor.requestToken(context);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /***/
    @SuppressLint("NewApi")
    public static void SendSession(Context context, String APIKEY) {
        sendSession(context, APIKEY);
    }

    // 세션 초기화 / 시작
    @SuppressLint("NewApi")
    public static void InitializeAndStartSession(Context context, String APIKEY) {
        if (HoneyQAData.FirstConnect) {
            HoneyQAData.AppContext = context;
            HoneyQAData.FirstConnect = false;
            HoneyQAData.APIKEY = APIKEY;
            new UncaughtExceptionHandler();
            sendSession(context, APIKEY);
        }

        //about init encrytion
        SharedPreferences prefs = context.getSharedPreferences(Encryptor.ENCRYPTION, Context.MODE_PRIVATE);
        String baseKey = prefs.getString(Encryptor.ENCRYPTION_BASE_KEY, null);
        String token = prefs.getString(Encryptor.ENCRYPTION_TOKEN, null);
        if (baseKey == null) {
            try {
                Log.e(HoneyQAData.HONEYQA_SDK_LOG, "request Token");
                Encryptor.requestToken(context);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            Encryptor.token = token;
            Encryptor.baseKey = baseKey;
        }
        EventPathManager.ClearEvent();
    }

    @SuppressLint("NewApi")
    public static void InitializeAndStartSession(Context context, String APIKEY, boolean isEncrypt) {
        HoneyQAData.isEncrypt = isEncrypt;
        InitializeAndStartSession(context, APIKEY);
    }

    private static void sendSession(Context context, String apiKey) {
        Authentication authentication = new Authentication();
        authentication.setKey(HoneyQAData.APIKEY);
        authentication.setAppVersion(DeviceCollector.GetAppVersion(context));
        authentication.setAndroidVersion(DeviceCollector.getVersionRelease());
        authentication.setModel(DeviceCollector.getDeviceModel());
        authentication.setManufacturer(DeviceCollector.getManufacturer());
        authentication.setCountryCode(DeviceCollector.getCountry(context));
        authentication.setDeviceId(DeviceCollector.getDeviceId(context, apiKey));
        authentication.setCarrierName(DeviceCollector.getCarrierName(context));
        Sender.sendSession(authentication, Sender.SESSION_URL);
    }

    public static void SendException(Exception e, String Tag, ErrorRank rank) {
        ErrorReport report = ErrorReportFactory.CreateErrorReport(e, Tag, rank,
                HoneyQAData.AppContext);

        try {
            Sender.sendException(report, Sender.EXCEPTION_URL);
        } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

    }

    public static void SendException(Exception e) {
        SendException(e, "", ErrorRank.Critical);
    }

    public static void SendException(Exception e, String Tag) {
        SendException(e, Tag, ErrorRank.Critical);
    }

    public static void SetLogCat(boolean toggleLog) {
        HoneyQAData.ToggleLogCat = toggleLog;
    }

    public static void SetLogging(int Line, String Filter) {
        HoneyQAData.TransferLog = true;
        HoneyQAData.LogLine = Line;
        HoneyQAData.LogFilter = Filter;
    }

    public static void SetLogging(int Line) {
        HoneyQAData.TransferLog = true;
        HoneyQAData.LogLine = Line;
    }

    public static int v(String tag, String Msg, Throwable tr) {
        return log(LogLevel.Verbose, tag, Msg, tr);
    }

    public static int v(String tag, String Msg) {
        return log(LogLevel.Verbose, tag, Msg, null);
    }

    public static int d(String tag, String Msg, Throwable tr) {
        return log(LogLevel.Debug, tag, Msg, tr);
    }

    public static int d(String tag, String Msg) {
        return log(LogLevel.Debug, tag, Msg, null);
    }

    public static int i(String tag, String Msg, Throwable tr) {
        return log(LogLevel.Info, tag, Msg, tr);
    }

    public static int i(String tag, String Msg) {
        return log(LogLevel.Info, tag, Msg, null);
    }

    public static int w(String tag, String Msg, Throwable tr) {
        return log(LogLevel.Warning, tag, Msg, tr);
    }

    public static int w(String tag, String Msg) {
        return log(LogLevel.Warning, tag, Msg, null);
    }

    public static int e(String tag, String Msg, Throwable tr) {
        return log(LogLevel.Error, tag, Msg, tr);
    }

    public static int e(String tag, String Msg) {
        return log(LogLevel.Error, tag, Msg, null);
    }

    enum LogLevel {
        Verbose, Debug, Info, Warning, Error
    }

    private static int loglevel(LogLevel level, String tag, String Msg,
                                Throwable tr) {
        if (tr != null) {
            switch (level) {
                case Verbose:
                    return Log.v(tag, Msg, tr);
                case Debug:
                    return Log.d(tag, Msg, tr);
                case Info:
                    return Log.i(tag, Msg, tr);
                case Warning:
                    return Log.w(tag, Msg, tr);
                case Error:
                    return Log.e(tag, Msg, tr);
                default:
                    return 0;
            }
        } else {
            switch (level) {
                case Verbose:
                    return Log.v(tag, Msg);
                case Debug:
                    return Log.d(tag, Msg);
                case Info:
                    return Log.i(tag, Msg);
                case Warning:
                    return Log.w(tag, Msg);
                case Error:
                    return Log.e(tag, Msg);
                default:
                    return 0;
            }
        }

    }

    private static int log(LogLevel level, String tag, String Msg, Throwable tr) {
        EventPathManager.CreateEventPath(3, "");

        if (HoneyQAData.ToggleLogCat)
            return loglevel(level, tag, Msg, tr);
        else
            return 0;
    }

    private static String GetCachePath() {
        File cachefile = HoneyQAData.AppContext.getCacheDir();
        return cachefile.getAbsolutePath();
    }

}
