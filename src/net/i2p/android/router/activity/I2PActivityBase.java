package net.i2p.android.router.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import net.i2p.android.router.R;
import net.i2p.android.router.binder.RouterBinder;
import net.i2p.android.router.service.RouterService;
import net.i2p.android.router.util.Util;
import net.i2p.router.CommSystemFacade;
import net.i2p.router.NetworkDatabaseFacade;
import net.i2p.router.Router;
import net.i2p.router.RouterContext;
import net.i2p.router.TunnelManagerFacade;
import net.i2p.router.peermanager.ProfileOrganizer;
import net.i2p.router.transport.FIFOBandwidthLimiter;
import net.i2p.stat.StatManager;

public abstract class I2PActivityBase extends ActionBarActivity {
    protected String _myDir;
    protected boolean _isBound;
    protected boolean _triedBind;
    protected ServiceConnection _connection;
    protected RouterService _routerService;
    private SharedPreferences _sharedPrefs;

    private static final String SHARED_PREFS = "net.i2p.android.router";
    protected static final String PREF_AUTO_START = "autoStart";
    /** true leads to a poor install experience, very slow to paint the screen */
    protected static final boolean DEFAULT_AUTO_START = false;
    protected static final String PREF_INSTALLED_VERSION = "app.version";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Util.i(this + " onCreate called");
        super.onCreate(savedInstanceState);
        _myDir = getFilesDir().getAbsolutePath();
    }

    @Override
    public void onRestart()
    {
        Util.i(this + " onRestart called");
        super.onRestart();
    }

    @Override
    public void onStart()
    {
        Util.i(this + " onStart called");
        super.onStart();
        _sharedPrefs = getSharedPreferences(SHARED_PREFS, 0);
        if (_sharedPrefs.getBoolean(PREF_AUTO_START, DEFAULT_AUTO_START))
            startRouter();
        else
            bindRouter(false);
    }

    /** @param def default */
    protected String getPref(String pref, String def) {
        return _sharedPrefs.getString(pref, def);
    }

    /** @return success */
    protected boolean setPref(String pref, boolean val) {
        SharedPreferences.Editor edit = _sharedPrefs.edit();
        edit.putBoolean(pref, val);
        return edit.commit();
    }

    /** @return success */
    protected boolean setPref(String pref, String val) {
        SharedPreferences.Editor edit = _sharedPrefs.edit();
        edit.putString(pref, val);
        return edit.commit();
    }

    @Override
    public void onResume()
    {
        Util.i(this + " onResume called");
        super.onResume();
    }

    @Override
    public void onPause()
    {
        Util.i(this + " onPause called");
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        Util.i(this + " onSaveInstanceState called");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop()
    {
        Util.i(this + " onStop called");
        unbindRouter();
        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        Util.i(this + " onDestroy called");
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu1, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // add/hide items here
        RouterService svc = _routerService;
        boolean showStart = ((svc == null) || (!_isBound) || svc.canManualStart()) &&
                            Util.isConnected(this);
        MenuItem start = menu.findItem(R.id.menu_start);
        start.setVisible(showStart);
        start.setEnabled(showStart);

        boolean showStop = svc != null && _isBound && svc.canManualStop();
        MenuItem stop = menu.findItem(R.id.menu_stop);
        stop.setVisible(showStop);
        stop.setEnabled(showStop);

        boolean showHome = ! (this instanceof MainActivity);
        MenuItem home = menu.findItem(R.id.menu_home);
        home.setVisible(showHome);
        home.setEnabled(showHome);

        boolean showAddressbook = (this instanceof WebActivity);
        MenuItem addressbook = menu.findItem(R.id.menu_addressbook);
        addressbook.setVisible(showAddressbook);
        addressbook.setEnabled(showAddressbook);

        boolean showReload = showAddressbook || (this instanceof PeersActivity);
        MenuItem reload = menu.findItem(R.id.menu_reload);
        reload.setVisible(showReload);
        reload.setEnabled(showReload);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_settings:
            Intent intent = new Intent(I2PActivityBase.this, SettingsActivity.class);
            startActivity(intent);
            return true;

        case R.id.menu_home:
            Intent i2 = new Intent(I2PActivityBase.this, MainActivity.class);
            startActivity(i2);
            return true;

        case R.id.menu_addressbook:
            Intent i3 = new Intent(I2PActivityBase.this, AddressbookActivity.class);
            startActivity(i3);
            return true;

        case R.id.menu_start:
            RouterService svc = _routerService;
            if (svc != null && _isBound && svc.canManualStart()) {
                setPref(PREF_AUTO_START, true);
                svc.manualStart();
            } else {
                startRouter();
            }
            return true;

        case R.id.menu_stop:
            RouterService rsvc = _routerService;
            if (rsvc != null && _isBound && rsvc.canManualStop()) {
                setPref(PREF_AUTO_START, false);
                rsvc.manualStop();
            }
            return true;

        case R.id.menu_reload:  // handled in WebActivity
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    ////// Service stuff

    /**
     *  Start the service and bind to it
     */
    protected boolean startRouter() {
        Intent intent = new Intent();
        intent.setClassName(this, "net.i2p.android.router.service.RouterService");
        Util.i(this + " calling startService");
        ComponentName name = startService(intent);
        if (name == null)
            Util.i(this + " XXXXXXXXXXXXXXXXXXXX got from startService: " + name);
        Util.i(this + " got from startService: " + name);
        boolean success = bindRouter(true);
        if (!success)
            Util.i(this + " Bind router failed");
        return success;
    }

    /**
     *  Bind only
     */
    protected boolean bindRouter(boolean autoCreate) {
        Intent intent = new Intent();
        intent.setClassName(this, "net.i2p.android.router.service.RouterService");
        Util.i(this + " calling bindService");
        _connection = new RouterConnection();
        _triedBind = bindService(intent, _connection, autoCreate ? BIND_AUTO_CREATE : 0);
        Util.i(this + " bindService: auto create? " + autoCreate + " success? " + _triedBind);
        return _triedBind;
    }

    protected void unbindRouter() {
        Util.i(this + " unbindRouter called with _isBound:" + _isBound + " _connection:" + _connection + " _triedBind:" + _triedBind);
        if (_triedBind && _connection != null)
                unbindService(_connection);

        _triedBind = false;
        _connection = null;
        _routerService = null;
        _isBound = false;
    }

    protected class RouterConnection implements ServiceConnection {

        public void onServiceConnected(ComponentName name, IBinder service) {
            Util.i(this + " connected to router service");
            RouterBinder binder = (RouterBinder) service;
            RouterService svc = binder.getService();
            _routerService = svc;
            _isBound = true;
            onRouterBind(svc);
        }

        public void onServiceDisconnected(ComponentName name) {
            Util.i(this + " disconnected from router service!!!!!!!");
            // save memory
            _routerService = null;
            _isBound = false;
            onRouterUnbind();
        }
    }

    /** callback from ServiceConnection, override as necessary */
    protected void onRouterBind(RouterService svc) {}

    /** callback from ServiceConnection, override as necessary */
    protected void onRouterUnbind() {}

    ////// Router stuff

    protected RouterContext getRouterContext() {
        RouterService svc = _routerService;
        if (svc == null || !_isBound)
            return null;
        return svc.getRouterContext();
    }

    protected Router getRouter() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.router();
    }

    protected NetworkDatabaseFacade getNetDb() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.netDb();
    }

    protected ProfileOrganizer getProfileOrganizer() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.profileOrganizer();
    }

    protected TunnelManagerFacade getTunnelManager() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.tunnelManager();
    }

    protected CommSystemFacade getCommSystem() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.commSystem();
    }

    protected FIFOBandwidthLimiter getBandwidthLimiter() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.bandwidthLimiter();
    }

    protected StatManager getStatManager() {
        RouterContext ctx = getRouterContext();
        if (ctx == null)
            return null;
        return ctx.statManager();
    }
}
