package info.nightscout.androidaps.plugins.NSClientInternal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class NSClientInternalPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(NSClientInternalPlugin.class);

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;

    static public Handler handler;
    static private HandlerThread handlerThread;

    public List<EventNSClientNewLog> listLog = new ArrayList<EventNSClientNewLog>();
    public Spanned textLog = Html.fromHtml("");

    public boolean paused = false;
    public boolean autoscroll = true;

    public String status = "";

    public NSClientService nsClientService = null;

    public NSClientInternalPlugin() {
        MainApp.bus().register(this);
        paused = SP.getBoolean(R.string.key_nsclientinternal_paused, false);
        autoscroll = SP.getBoolean(R.string.key_nsclientinternal_autoscroll, true);

        if (handler == null) {
            handlerThread = new HandlerThread(NSClientInternalPlugin.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, NSClientService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return NSClientInternalFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.nsclientinternal);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.nsclientinternal_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
    }

    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            nsClientService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            NSClientService.LocalBinder mLocalBinder = (NSClientService.LocalBinder) service;
            nsClientService = mLocalBinder.getServiceInstance();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        if (nsClientService != null)
            MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        //TODO
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientNewLog ev) {
        addToLog(ev);
        log.debug(ev.action + " " + ev.logText);
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientStatus ev) {
        status = ev.status;
        MainApp.bus().post(new EventNSClientUpdateGUI());
    }

    public void clearLog() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                listLog = new ArrayList<EventNSClientNewLog>();
                updateLog();
            }
        });
    }

    private void addToLog(final EventNSClientNewLog ev) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                listLog.add(ev);
                // remove the first line if log is too large
                if (listLog.size() >= Constants.MAX_LOG_LINES) {
                    listLog.remove(0);
                }
                updateLog();
            }
        });
    }

    private void updateLog() {
        try {
            Spanned newTextLog = Html.fromHtml("");
            for (EventNSClientNewLog log : listLog) {
                newTextLog = (Spanned) TextUtils.concat(newTextLog, log.toHtml());
            }
            textLog = newTextLog;
            MainApp.bus().post(new EventNSClientUpdateGUI());
        } catch (OutOfMemoryError e) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Out of memory!\nStop using this phone !!!", R.raw.error);
        }
    }

    public void resend(String reason) {
        if (nsClientService != null)
            nsClientService.resend(reason);
    }

    public UploadQueue queue() {
        return NSClientService.uploadQueue;
    }

    public String url() {
        return NSClientService.nsURL;
    }
}
