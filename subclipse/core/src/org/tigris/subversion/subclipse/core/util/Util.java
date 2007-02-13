/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     C�dric Chabanois (cchabanois@ifrance.com) - modified for Subversion 
 *******************************************************************************/
package org.tigris.subversion.subclipse.core.util;


import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.tigris.subversion.subclipse.core.ISVNLocalFolder;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;

/**
 * Unsorted static helper-methods 
 */
public class Util {
	public static final String CURRENT_LOCAL_FOLDER = "."; //$NON-NLS-1$
	public static final String SERVER_SEPARATOR = "/"; //$NON-NLS-1$
	
	/**
	 * Return the last segment of the given path
	 * <br>
	 * Do not abuse this unnecesarily !
	 * When there is a SVNUrl instance available use direct
	 * {@link SVNUrl#getLastPathSegment()}
	 * @param path
	 * @return String
	 */
	public static String getLastSegment(String path) {
		int index = path.lastIndexOf(SERVER_SEPARATOR);
		if (index == -1)
			return path;
		else
			return path.substring(index + 1);
		
	}
	
	/**
	 * Append the prefix and suffix to form a valid SVN path.
	 * <br>
	 * Do not abuse this unnecesarily !
	 * When there is a SVNUrl instance available use direct
	 * {@link SVNUrl#appendPath(java.lang.String)}
	 */
	public static String appendPath(String prefix, String suffix) {
		if (prefix.length() == 0 || prefix.equals(CURRENT_LOCAL_FOLDER)) {
			return suffix;
		} else if (prefix.endsWith(SERVER_SEPARATOR)) {
			if (suffix.startsWith(SERVER_SEPARATOR))
				return prefix + suffix.substring(1);
			else
				return prefix + suffix;
		} else if (suffix.startsWith(SERVER_SEPARATOR))
			return prefix + suffix;
		else
			return prefix + SERVER_SEPARATOR + suffix;
	}

	public static void logError(String message, Throwable throwable) {
		SVNProviderPlugin.log(new Status(IStatus.ERROR, SVNProviderPlugin.ID, IStatus.ERROR, message, throwable));
	}
	
	/**
	 * Get the url string of the parent resource
	 * @param svnResource
	 * @return parent's url, null if none of parents has an url
	 * @throws SVNException
	 */
	public static String getParentUrl(ISVNLocalResource svnResource) throws SVNException {
        ISVNLocalFolder parent = svnResource.getParent();
        while (parent != null) {
            String url = parent.getStatus().getUrlString();
            if (url != null) return url;
            parent = parent.getParent();
        }
        return null;
    }


	/**
	 * unescape UTF8/URL encoded strings
	 * 
	 * @param s
	 * @return
	 */
	public static String unescape(String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return s;
		}
	}	
}
