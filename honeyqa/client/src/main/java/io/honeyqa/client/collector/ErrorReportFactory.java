package io.honeyqa.client.collector;

import android.content.Context;

import java.util.Calendar;
import java.util.TimeZone;

import io.honeyqa.client.data.CallStackData;
import io.honeyqa.client.json.JSONErrorData;
import io.honeyqa.client.data.HoneyQAData;
import io.honeyqa.client.eventpath.EventPathManager;
import io.honeyqa.client.rank.ErrorRank;

public class ErrorReportFactory {

    public static ErrorReport createErrorReport(Throwable e, String tag, ErrorRank rank, Context context) {
        ErrorReport report = new ErrorReport();
        report.ErrorData = createErrorData(e, tag, rank, context);
        report.LogData = LogCollector.getLog(context);
        report.mId = getId();
        report.mHoneyQAVersion = getHoneyQAVersion();
        return report;
    }

    public static ErrorReport createNativeErrorReport(Context context) {
        ErrorReport report = new ErrorReport();
        report.ErrorData = createNativeErrorData(context);
        report.LogData = LogCollector.getLog(context);
        report.mId = getId();
        report.mHoneyQAVersion = getHoneyQAVersion();
        return report;
    }

    private static long getId() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        return calendar.getTimeInMillis();
    }

    private static String getHoneyQAVersion() {
        return HoneyQAData.SDKVersion;
    }

    private static JSONErrorData createNativeErrorData(Context context) {
        JSONErrorData senddata = new JSONErrorData();
        senddata.apikey = HoneyQAData.APIKEY;
        senddata.datetime = DateCollector.GetDateYYMMDDHHMMSS(context);
        senddata.device = DeviceCollector.getDeviceModel();
        senddata.country = DeviceCollector.getCountry(context);
        senddata.appversion = DeviceCollector.getAppVersion(context);
        senddata.osversion = DeviceCollector.getVersionRelease();
        senddata.gpson = (DeviceCollector.getGps(context)) ? 1 : 0;
        senddata.wifion = (DeviceCollector.getWiFiNetwork(context)) ? 1 : 0;
        senddata.mobileon = (DeviceCollector.getMobileNetwork(context)) ? 1 : 0;
        senddata.scrwidth = DeviceCollector.getWidthScreenSize(context);
        senddata.scrheight = DeviceCollector.getHeightScreenSize(context);
        senddata.batterylevel = DeviceCollector.getBatteryLevel(context);
        senddata.availsdcard = DeviceCollector.BytetoMegaByte(DeviceCollector.getAvailableExternalMemorySize());
        senddata.rooted = (DeviceCollector.CheckRoot()) ? 1 : 0;
        senddata.appmemtotal = DeviceCollector.BytetoMegaByte(DeviceCollector.getTotalMemory());
        senddata.appmemfree = DeviceCollector.BytetoMegaByte(DeviceCollector.getFreeMemory());
        senddata.appmemmax = DeviceCollector.BytetoMegaByte(DeviceCollector.getMaxMemory());
        senddata.kernelversion = DeviceCollector.GetLinuxKernelVersion();
        senddata.xdpi = DeviceCollector.getXDPI(context);
        senddata.ydpi = DeviceCollector.getYDPI(context);
        senddata.scrorientation = DeviceCollector.getOrientation(context);
        senddata.sysmemlow = (DeviceCollector.getSystemLowMemory()) ? 1 : 0;
        senddata.eventpaths = EventPathManager.GetErrorEventPath();
        senddata.locale = DeviceCollector.getLocale(context);
        senddata.mCarrierName = DeviceCollector.getCarrierName(context);
        senddata.mDeviceId = DeviceCollector.getDeviceId(context, HoneyQAData.APIKEY);
        senddata.rank = ErrorRank.Native.value();
        return senddata;
    }

    private static JSONErrorData createErrorData(Throwable e, String tag, ErrorRank rank, Context context) {
        JSONErrorData senddata = new JSONErrorData();
        String CallStack = CallStackCollector.GetCallStack(e);
        CallStackData data = CallStackCollector.ParseStackTrace(e, CallStack);
        senddata.apikey = HoneyQAData.APIKEY;
        senddata.datetime = DateCollector.GetDateYYMMDDHHMMSS(context);
        senddata.device = DeviceCollector.getDeviceModel();
        senddata.country = DeviceCollector.getCountry(context);
        senddata.errorname = data.ErrorName;
        senddata.errorclassname = data.ClassName;
        senddata.linenum = data.Line;
        senddata.lastactivity = data.ActivityName;
        senddata.callstack = CallStack;
        senddata.appversion = DeviceCollector.getAppVersion(context);
        senddata.osversion = DeviceCollector.getVersionRelease();
        senddata.gpson = (DeviceCollector.getGps(context)) ? 1 : 0;
        senddata.wifion = (DeviceCollector.getWiFiNetwork(context)) ? 1 : 0;
        senddata.mobileon = (DeviceCollector.getMobileNetwork(context)) ? 1 : 0;
        senddata.scrwidth = DeviceCollector.getWidthScreenSize(context);
        senddata.scrheight = DeviceCollector.getHeightScreenSize(context);
        senddata.batterylevel = DeviceCollector.getBatteryLevel(context);
        senddata.availsdcard = DeviceCollector.BytetoMegaByte(DeviceCollector.getAvailableExternalMemorySize());
        senddata.rooted = (DeviceCollector.CheckRoot()) ? 1 : 0;
        senddata.appmemtotal = DeviceCollector.BytetoMegaByte(DeviceCollector.getTotalMemory());
        senddata.appmemfree = DeviceCollector.BytetoMegaByte(DeviceCollector.getFreeMemory());
        senddata.appmemmax = DeviceCollector.BytetoMegaByte(DeviceCollector.getMaxMemory());
        senddata.kernelversion = DeviceCollector.GetLinuxKernelVersion();
        senddata.xdpi = DeviceCollector.getXDPI(context);
        senddata.ydpi = DeviceCollector.getYDPI(context);
        senddata.scrorientation = DeviceCollector.getOrientation(context);
        senddata.sysmemlow = (DeviceCollector.getSystemLowMemory()) ? 1 : 0;
        senddata.tag = tag;
        senddata.rank = rank.value();
        senddata.eventpaths = EventPathManager.GetErrorEventPath();
        senddata.locale = DeviceCollector.getLocale(context);
        senddata.mCarrierName = DeviceCollector.getCarrierName(context);
        senddata.mDeviceId = DeviceCollector.getDeviceId(context, HoneyQAData.APIKEY);
        return senddata;
    }
}