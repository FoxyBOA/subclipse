/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     C?dric Chabanois (cchabanois@ifrance.com) - modified for Subversion 
 *******************************************************************************/
package org.tigris.subversion.subclipse.core.resources;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.tigris.subversion.subclipse.core.ISVNFolder;
import org.tigris.subversion.subclipse.core.ISVNRemoteFolder;
import org.tigris.subversion.subclipse.core.ISVNRemoteResource;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.ISVNResource;
import org.tigris.subversion.subclipse.core.Policy;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.subclipse.core.SVNStatus;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;

/**
 * This class provides the implementation of ISVNRemoteFolder
 * 
 */
public class RemoteFolder extends RemoteResource implements ISVNRemoteFolder, ISVNFolder {

    protected ISVNRemoteResource[] children;
	
	/**
	 * Constructor for RemoteFolder.
	 * @param parent
	 * @param repository
	 * @param url
	 * @param revision
	 * @param lastChangedRevision
	 * @param date
	 * @param author
	 */
	public RemoteFolder(RemoteFolder parent, 
        ISVNRepositoryLocation repository,
        SVNUrl url,
        SVNRevision revision,
        SVNRevision.Number lastChangedRevision,
        Date date,
        String author) {
		super(parent, repository, url, revision, lastChangedRevision, date, author);
	}

    /**
     * Constructor (from url and revision)
     * @param repository
     * @param url
     * @param revision
     */
    public RemoteFolder(ISVNRepositoryLocation repository, SVNUrl url, SVNRevision revision) {
        super(repository, url,revision);
    }
	
	/**
	 * Constructor (from byet[])
	 * @param resource
	 * @param bytes
	 */
	public RemoteFolder(IResource resource, byte[] bytes) {		
		super(resource, bytes);
	}

	public RemoteFolder(RemoteResourceStatus remoteStatusInfo)
	{
        this( null,
        		remoteStatusInfo.getRepository(),
				remoteStatusInfo.getUrl(), 
				remoteStatusInfo.getRevision(),
				remoteStatusInfo.getLastChangedRevision(), 
				remoteStatusInfo.getLastChangedDate(), 
				remoteStatusInfo.getLastCommitAuthor());
	}
	
	/* (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.core.ISVNRemoteResource#exists(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean exists(IProgressMonitor monitor) throws TeamException {
		try {
			getMembers(monitor);
			return true;
		} catch (SVNException e) {
			if (e.getStatus().getCode() == SVNStatus.DOES_NOT_EXIST) {
				return false;
			} else {
				throw e;
			}
		}
	}

	/**
	 * Check whether the given child exists
	 * @param child the child resource to check for existence
	 * @param monitor a progress monitor
	 * @return true when the child resource exists 
	 */
	protected boolean exists(final ISVNRemoteResource child, IProgressMonitor monitor) throws SVNException {
        ISVNRemoteResource[] children;
        try {
            children = getMembers(monitor);
        } catch (SVNException e) {
            if (e.getStatus().getCode() == SVNStatus.DOES_NOT_EXIST) {
                return false;
            } else {
                throw e;
            }
        }
        
        for (int i = 0; i < children.length;i++) {
            if (children[i].equals(child))
                return true;
        }
        return false;
	}

    /* (non-Javadoc)
     * @see org.tigris.subversion.subclipse.core.ISVNRemoteFolder#refresh()
     */
    public void refresh() {
        children = null;
    }

    /**
     * Get the members of this folder at the same revision than this resource
     * @param monitor a progress monitor
     * @return ISVNRemoteResource[] an array of child remoteResources
     */
	protected ISVNRemoteResource[] getMembers(IProgressMonitor monitor) throws SVNException {

		final IProgressMonitor progress = Policy.monitorFor(monitor);
		progress.beginTask(Policy.bind("RemoteFolder.getMembers"), 100); //$NON-NLS-1$
        
		//Try to hit the cache first.
        if (children != null)
        {
            progress.done();
            return children;
        }
		
		try {
            ISVNClientAdapter client = getRepository().getSVNClient();
				
			ISVNDirEntry[] list = client.getList(url, getRevision(), false);
			List result = new ArrayList(list.length);

			// directories first				
			for (int i=0;i<list.length;i++)
			{
                ISVNDirEntry entry = list[i];
                if (entry.getNodeKind() == SVNNodeKind.DIR)
				{
				    result.add(new RemoteFolder(this, getRepository(),
				       url.appendPath(entry.getPath()),
                       getRevision(),
                       entry.getLastChangedRevision(),
                       entry.getLastChangedDate(),
                       entry.getLastCommitAuthor()));
				}
			}

			// files then				
			for (int i=0;i<list.length;i++)
			{
				ISVNDirEntry entry = list[i];
				if (entry.getNodeKind() == SVNNodeKind.FILE)
				{
					result.add(new RemoteFile(this, getRepository(),
						url.appendPath(entry.getPath()),
                        getRevision(),
                        entry.getLastChangedRevision(),
                        entry.getLastChangedDate(),
                        entry.getLastCommitAuthor()));
			     }					 	
			}

			//Save it to the cache
			children = (ISVNRemoteResource[]) result.toArray(new ISVNRemoteResource[result.size()]);
            return children;
        } catch (SVNClientException e)
		{
            throw new SVNException(new SVNStatus(SVNStatus.ERROR, SVNStatus.DOES_NOT_EXIST, Policy.bind("RemoteFolder.doesNotExist", getRepositoryRelativePath()))); //$NON-NLS-1$
        } finally {
			progress.done();
		}	 	
	}


	/* (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.core.ISVNFolder#members(org.eclipse.core.runtime.IProgressMonitor, int)
	 */
	public ISVNResource[] members(IProgressMonitor monitor, int flags) throws SVNException {		
		final List result = new ArrayList();
		ISVNRemoteResource[] resources = getMembers(monitor);

		// RemoteFolders never have phantom members
		if ((flags & EXISTING_MEMBERS) == 0 && (flags & PHANTOM_MEMBERS) == 1) {
			return new ISVNResource[0];
		}
		boolean includeFiles = (((flags & FILE_MEMBERS) != 0) || ((flags & (FILE_MEMBERS | FOLDER_MEMBERS)) == 0));
		boolean includeFolders = (((flags & FOLDER_MEMBERS) != 0) || ((flags & (FILE_MEMBERS | FOLDER_MEMBERS)) == 0));
		boolean includeManaged = (((flags & MANAGED_MEMBERS) != 0) || ((flags & (MANAGED_MEMBERS | UNMANAGED_MEMBERS | IGNORED_MEMBERS)) == 0));
	
		for (int i = 0; i < resources.length; i++) {
			ISVNResource svnResource = resources[i];
			if ((includeFiles && ( ! svnResource.isFolder())) 
					|| (includeFolders && (svnResource.isFolder()))) {
				if (includeManaged) {
					result.add(svnResource);
				}						
			}		
		}
		return (ISVNResource[]) result.toArray(new ISVNResource[result.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.core.ISVNResource#isFolder()
	 */
	public boolean isFolder() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.variants.IResourceVariant#isContainer()
	 */
	public boolean isContainer() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.core.ISVNRemoteResource#members(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public ISVNRemoteResource[] members(IProgressMonitor progress) throws TeamException {
		return getMembers(progress);
	}

    /* (non-Javadoc)
     * @see org.tigris.subversion.subclipse.core.ISVNRemoteFolder#createRemoteFolder(java.lang.String, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
     */
    public void createRemoteFolder(String folderName, String message, IProgressMonitor monitor) throws SVNException {
        IProgressMonitor progress = Policy.monitorFor(monitor);
        progress.beginTask(Policy.bind("RemoteFolder.createRemoteFolder"), 100); //$NON-NLS-1$
        
        try {
            ISVNClientAdapter svnClient = getRepository().getSVNClient();
            svnClient.mkdir( getUrl().appendPath(folderName), message);
            refresh();
            SVNProviderPlugin.getPlugin().getRepositoryResourcesManager().remoteResourceCreated(this, folderName);
        } catch (SVNClientException e) {
            throw SVNException.wrapException(e);
        } finally {
            progress.done();
        }
    }    

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.variants.CachedResourceVariant#fetchContents(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void fetchContents(IProgressMonitor monitor)
	{	
		//Do nothing. Folders do not have contents
	}

}