package io.honeyqa.client.exception;

import io.honeyqa.client.collector.ErrorReport;
import io.honeyqa.client.collector.ErrorReportFactory;
import io.honeyqa.client.network.Sender;
import io.honeyqa.client.data.HoneyQAData;
import io.honeyqa.client.network.NetworkResource;
import io.honeyqa.client.rank.ErrorRank;

public class UncaughtExceptionHandler implements
        Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler mDefaultExceptionHandler = null;
    private Thread.UncaughtExceptionHandler mUncaughtExceptionHandler = null;

    public UncaughtExceptionHandler() {
        mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public void setUncaughtExceptionHandler(
            Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        mUncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            ErrorReport report = ErrorReportFactory.createErrorReport(ex, "",
                    ErrorRank.Unhandle, HoneyQAData.APP_CONTEXT);
            // TODO : URL validation
            Sender.sendException(report, NetworkResource.EXCEPTION_URL);
            if (mUncaughtExceptionHandler != null)
                mUncaughtExceptionHandler.uncaughtException(thread, ex);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            mDefaultExceptionHandler.uncaughtException(thread, ex);
        }
    }
}
