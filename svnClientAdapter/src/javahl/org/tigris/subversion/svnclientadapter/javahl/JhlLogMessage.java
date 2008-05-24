/*******************************************************************************
 * Copyright (c) 2003, 2006 svnClientAdapter project and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     svnClientAdapter project committers - initial API and implementation
 ******************************************************************************/
package org.tigris.subversion.svnclientadapter.javahl;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.tigris.subversion.javahl.ChangePath;
import org.tigris.subversion.javahl.LogDate;
import org.tigris.subversion.svnclientadapter.ISVNLogMessage;
import org.tigris.subversion.svnclientadapter.ISVNLogMessageChangePath;
import org.tigris.subversion.svnclientadapter.SVNRevision;

/**
 * A JavaHL based implementation of {@link ISVNLogMessage}.
 * Actually just an adapter from {@link org.tigris.subversion.javahl.LogMessage}
 *  
 * @author philip schatz
 */
public class JhlLogMessage implements ISVNLogMessage {

	private List children;
	private boolean hasChildren;
	private ISVNLogMessageChangePath[] changedPaths;
	private SVNRevision.Number revision;
	private Map revprops;
	private LogDate logDate;

	public JhlLogMessage(ChangePath[] changedPaths, long revision, Map revprops, boolean hasChildren) {
		this.changedPaths = JhlConverter.convert(changedPaths);
		this.revision = new SVNRevision.Number(revision);
		this.revprops = revprops;
		this.hasChildren = hasChildren;
		try {
			logDate = new LogDate((String) revprops.get(DATE));
		} catch (ParseException e) {
		}
	}

	public void addChild(JhlLogMessage msg) {
		if (children == null)
			children = new ArrayList();
		children.add(msg);
	}
	
	/* (non-Javadoc)
	 * @see org.tigris.subversion.svnclientadapter.ISVNLogMessage#getRevision()
	 */
	public SVNRevision.Number getRevision() {
		return revision;
	}

	/* (non-Javadoc)
	 * @see org.tigris.subversion.svnclientadapter.ISVNLogMessage#getAuthor()
	 */
	public String getAuthor() {
        return (String) revprops.get(AUTHOR);
	}

	/* (non-Javadoc)
	 * @see org.tigris.subversion.svnclientadapter.ISVNLogMessage#getDate()
	 */
	public Date getDate() {
		if (logDate == null)
			return new Date(0L);
        return logDate.getDate();
	}

	/* (non-Javadoc)
	 * @see org.tigris.subversion.svnclientadapter.ISVNLogMessage#getMessage()
	 */
	public String getMessage() {
        return (String) revprops.get(MESSAGE);
	}

    /* (non-Javadoc)
     * @see org.tigris.subversion.svnclientadapter.ISVNLogMessage#getChangedPaths()
     */
    public ISVNLogMessageChangePath[] getChangedPaths() {
    	return changedPaths;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return getMessage();
    }

	public ISVNLogMessage[] getChildMessages() {
		if (hasChildren && children != null) {
			ISVNLogMessage[] childArray = new JhlLogMessage[children.size()];
			children.toArray(childArray);
			return childArray;
		} else
			return null;
	}

	public long getNumberOfChildren() {
		if (hasChildren && children != null)
			return children.size();
		else
			return 0L;
	}

	public long getTimeMillis() {
		if (logDate == null)
			return 0L;
        return logDate.getTimeMillis();
	}
	
	public long getTimeMicros() {
		if (logDate == null) 
			return 0L;
		return logDate.getTimeMicros();
	}

}
