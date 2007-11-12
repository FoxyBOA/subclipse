/*******************************************************************************
 * Copyright (c) 2005, 2006 Subclipse project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Subclipse project committers - initial API and implementation
 ******************************************************************************/
package org.tigris.subversion.subclipse.core;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;


/**
 * Preference Initializer.
 * Called at startup by Eclipse to initialize any default preferences.
 * 
 * @author markphip
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    public PreferenceInitializer() {
        super();
    }

    public void initializeDefaultPreferences() {
        SVNProviderPlugin.getPlugin().getPluginPreferences().setDefault(ISVNCoreConstants.PREF_RECURSIVE_STATUS_UPDATE, true);
        SVNProviderPlugin.getPlugin().getPluginPreferences().setDefault(ISVNCoreConstants.PREF_SHOW_OUT_OF_DATE_FOLDERS, false);
    }

}
	
