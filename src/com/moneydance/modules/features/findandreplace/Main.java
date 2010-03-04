/************************************************************\
 *       Copyright (C) 2001 Appgen Personal Software        *
 \************************************************************/

package com.moneydance.modules.features.findandreplace;

import com.moneydance.apps.md.controller.FeatureModule;
import com.moneydance.apps.md.controller.FeatureModuleContext;
import com.moneydance.apps.md.controller.PreferencesListener;
import com.moneydance.apps.md.model.RootAccount;

import javax.swing.JOptionPane;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.text.MessageFormat;

/**
 * <p>Pluggable module used to give users access to a Find and Replace
 * interface to Moneydance.</p>
 *
 * <p>This code is released as open source under the Apache 2.0 License:<br/>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">
 * http://www.apache.org/licenses/LICENSE-2.0</a><br />

 * @author Kevin Menningen
 * @version 1.4
 * @since 1.0
 */
public class Main extends FeatureModule
{
    static final String VERSION = "1.40";
    static final String BUILD = "58";
    
    private final PreferencesListener _prefListener = new FarPreferencesListener();
    private IFindAndReplaceController _controller = null;
    private FarHomeView _homePageView = null;
    private ResourceBundle _resources;

    private boolean _testMode;
    private RootAccount _testRootAccount;

    public void init()
    {
        super.init();

        // the first thing we will do is register this module to be invoked
        // via the application toolbar
        FeatureModuleContext context = getContext();
        try
        {
            if ( context != null )
            {
                loadResources();
                context.registerFeature(this, N12EFindAndReplace.INVOKE_COMMAND,
                                        getIcon(),
                                        getName());

                addPreferencesListener();

                // setup the home page view
                _homePageView = new FarHomeView(this);
                getContext().registerHomePageView(this, _homePageView);
            }
        }
        catch (Exception error)
        {
            handleException(error);
        }
    }

    public void cleanup()
    {
        cleanupFarComponent();
        removePreferencesListener();
    }

    void setTestMode(final boolean testing)
    {
        _testMode = testing;
    }

    void loadResources()
    {
        Locale locale = ((com.moneydance.apps.md.controller.Main) getContext())
                .getPreferences().getLocale();
        _resources = ResourceBundle.getBundle(N12EFindAndReplace.RESOURCES, locale,
                new XmlResourceControl());

    }

    void setTestData(RootAccount rootTestAccount)
    {
        _testRootAccount = rootTestAccount;
    }

    String getString(final String key)
    {
        if (_resources == null)
        {
            return null;
        }
        return _resources.getString( key );
    }

    Image getImage(final String resourceKey)
    {
        if (_resources == null)
        {
            return null;
        }
        String urlName = _resources.getString( resourceKey );
        try
        {
            java.io.InputStream inputStream = getClass().getResourceAsStream(urlName);
            if (inputStream != null)
            {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1000);
                byte buffer[] = new byte[1024];
                int count;
                while ((count = inputStream.read(buffer, 0, buffer.length)) >= 0)
                {
                    outputStream.write(buffer, 0, count);
                }
                return Toolkit.getDefaultToolkit().createImage(outputStream.toByteArray());
            }
        }
        catch (IOException error)
        {
            handleException(error);
        }
        return null;
    }

    IFindAndReplaceController getFarController()
    {
        if (_controller == null)
        {
            _controller = FindAndReplace.getInstance( this );
        }
        return _controller;
    }

    /**
     * Load an icon from resources.
     * @return The icon, or null if an error occurred.
     */
    Image getIcon()
    {
        return getImage(L10NFindAndReplace.FAR_IMAGE);
    }


    /**
     * Process an invokation of this module with the given URI
     * The format is {command}?{parameters} or {command}:
     */
    public void invoke(String uri)
    {
        String command = uri;
        int theIdx = uri.indexOf('?');
        if (theIdx >= 0)
        {
            command = uri.substring(0, theIdx);
        }
        else
        {
            theIdx = uri.indexOf(':');
            if (theIdx >= 0)
            {
                command = uri.substring(0, theIdx);
            }
        }

        if (N12EFindAndReplace.INVOKE_COMMAND.equals(command))
        {
            showFarDialog();
        }
    }

    public String getName()
    {
        String name = getString(L10NFindAndReplace.TITLE);
        if ((name == null) || (name.length() == 0))
        {
            name = N12EFindAndReplace.TITLE;
        }
        return name;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////////
    private void addPreferencesListener()
    {
        if (getContext() != null)
        {
            ((com.moneydance.apps.md.controller.Main) getContext()).getPreferences()
                    .addListener(_prefListener);
        }
    }

    private void removePreferencesListener()
    {
        if (getContext() != null)
        {
            ((com.moneydance.apps.md.controller.Main) getContext()).getPreferences()
                    .removeListener(_prefListener);
        }
    }

    private synchronized void showFarDialog()
    {
        try
        {
            // obtain the data if possible
            FeatureModuleContext context = getUnprotectedContext();
            RootAccount root = null;
            if (context != null)
            {
                root = getUnprotectedContext().getRootAccount();
            }
            else if (_testMode)
            {
                root = _testRootAccount;
            }

            if (root != null)
            {
                getFarController().loadData(root);
                getFarController().setInitialFreeText(null);
                getFarController().show();
            }
            else
            {
                // can't show the dialog without a file (nothing to search)
                final String title = _resources.getString(L10NFindAndReplace.ERROR_TITLE);
                final String message = _resources.getString(L10NFindAndReplace.ERROR_NO_DATA);
                JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
            }
        }
        catch (Exception error)
        {
            handleException(error);
        }
    }


    void handleException(Exception error)
    {
        final PrintStream output = System.err;
        output.println(N12EFindAndReplace.ERROR_LOADING);
        fullDumpStackTrace(error, output, 0);
        if (_resources != null)
        {
            final String title = _resources.getString(L10NFindAndReplace.ERROR_TITLE);
            final String format = _resources.getString(L10NFindAndReplace.ERROR_LOAD_FMT);
            final String message = MessageFormat.format(format, error.getLocalizedMessage());
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
        }
        else
        {
            // can't localize
            JOptionPane.showMessageDialog(null, N12EFindAndReplace.ERROR_LOADING,
                    N12EFindAndReplace.ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fullDumpStackTrace(final Throwable error, final PrintStream output, int level)
    {
        output.println(error.getMessage());
        for (StackTraceElement element : error.getStackTrace())
        {
            output.print("    ");
            output.println(element.toString());
        }
        Throwable cause = error.getCause();
        if ((cause != null) && (level < 10))
        {
            output.print("Caused by: ");
            fullDumpStackTrace(cause, output, level + 1);
        }
    }

    FeatureModuleContext getUnprotectedContext()
    {
        return getContext();
    }

    synchronized void cleanupFarComponent()
    {
        if (_controller != null)
        {
            _controller.cleanUp();
            _controller = null;
            System.gc();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Inner Classes
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Listen for changes in the locale and reload everything in the new locale.
     */
    private class FarPreferencesListener implements PreferencesListener
    {
        public void preferencesUpdated()
        {
            // reload
            cleanupFarComponent();
            loadResources();
            if (_homePageView != null)
            {
                _homePageView.refresh();
            }
        }
    }
}

