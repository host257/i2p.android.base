package net.i2p.router.web;

/** set the theme */
public class ConfigUIHandler extends FormHandler {
    private boolean _shouldSave;
    private String _config;
    
    protected void processForm() {
        if (_shouldSave)
            saveChanges();
    }
    
    public void setShouldsave(String moo) { _shouldSave = true; }
    
    public void setTheme(String val) {
        _config = val;
    }
    
    private void saveChanges() {
        if (_config == null)
            return;
        if (_config.equals("default")) // obsolete
            _context.router().removeConfigSetting(CSSHelper.PROP_THEME_NAME);
        else
            _context.router().setConfigSetting(CSSHelper.PROP_THEME_NAME, _config);
        if (_context.router().saveConfig()) 
            addFormNotice("Theme change successfully saved (<a href=\"configui.jsp\">refresh page to view</a>)");
        else
            addFormNotice("Error saving the configuration (applied but not saved) - please see the error logs");
    }
}