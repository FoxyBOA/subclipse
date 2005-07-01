/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 * 	   Korros Panagiotis - pkorros@tigris.org
 *******************************************************************************/
package org.tigris.subversion.subclipse.core.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ISynchronizer;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamStatus;
import org.eclipse.team.core.subscribers.ISubscriberChangeEvent;
import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.subscribers.SubscriberChangeEvent;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.variants.IResourceVariantComparator;
import org.eclipse.team.core.variants.ResourceVariantByteStore;
import org.eclipse.team.core.variants.SessionResourceVariantByteStore;
import org.tigris.subversion.subclipse.core.IResourceStateChangeListener;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.Policy;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.subclipse.core.client.StatusAndInfoCommand;
import org.tigris.subversion.subclipse.core.client.StatusAndInfoCommand.InformedStatus;
import org.tigris.subversion.subclipse.core.resources.LocalResourceStatus;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.core.sync.SVNStatusSyncInfo.StatusInfo;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.SVNClientException;

public class SVNWorkspaceSubscriber extends Subscriber implements IResourceStateChangeListener {

	/** Name used for identifying SVN synchronization data in Resource>ResourceInfo#syncInfo storage */
	private static final QualifiedName qualifiedName = new QualifiedName(SVNProviderPlugin.ID, "svn-remote-resource-key");
	
	private static SVNWorkspaceSubscriber instance; 
	
	/**
	 * Return the file system subscriber singleton.
	 * @return the file system subscriber singleton.
	 */
	public static synchronized SVNWorkspaceSubscriber getInstance() {
		if (instance == null) {
			instance = new SVNWorkspaceSubscriber();
			ResourcesPlugin.getWorkspace().getSynchronizer().add(qualifiedName);
		}
		return instance;
	}

	protected SVNRevisionComparator comparator = new SVNRevisionComparator();

	protected ResourceVariantByteStore remoteSyncStateStore = new SessionResourceVariantByteStore();

	public SVNWorkspaceSubscriber() {
	    SVNProviderPlugin.addResourceStateChangeListener(this);
	}

    /* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#getResourceComparator()
     */
    public IResourceVariantComparator getResourceComparator() {
        return comparator;
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#getName()
     */
    public String getName() {
        return "SVNStatusSubscriber"; //$NON-NLS-1$
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#roots()
     */
    public IResource[] roots() {
		List result = new ArrayList();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			IProject project = projects[i];
			if(project.isAccessible()) {
				RepositoryProvider provider = RepositoryProvider.getProvider(project, SVNProviderPlugin.PROVIDER_ID);
				if(provider != null) {
					result.add(project);
				}
			}
		}
		return (IProject[]) result.toArray(new IProject[result.size()]);
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#isSupervised(org.eclipse.core.resources.IResource)
     */
    public boolean isSupervised(IResource resource) throws TeamException {
		try {
			RepositoryProvider provider = RepositoryProvider.getProvider(resource.getProject(), SVNProviderPlugin.getTypeId());
			if (provider == null) return false;
			// TODO: what happens for resources that don't exist?
			// TODO: is it proper to use ignored here?
			ISVNLocalResource svnThing = SVNWorkspaceRoot.getSVNResourceFor(resource);
			if (svnThing.isIgnored()) {
				// An ignored resource could have an incoming addition (conflict)
				return false;//getRemoteTree().hasResourceVariant(resource);
			}
			return true;
		} catch (TeamException e) {
			// If there is no resource in coe this measn there is no local and no remote
			// so the resource is not supervised.
			if (e.getStatus().getCode() == IResourceStatus.RESOURCE_NOT_FOUND) {
				return false;
			}
			throw e;
		}
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#members(org.eclipse.core.resources.IResource)
     */
    public IResource[] members(IResource resource) throws TeamException {
		if(resource.getType() == IResource.FILE) {
			return new IResource[0];
		}	
		try {
			Set allMembers = new HashSet();
			try {
				allMembers.addAll(Arrays.asList(((IContainer)resource).members(true)));
			} catch (CoreException e) {
				if (e.getStatus().getCode() == IResourceStatus.RESOURCE_NOT_FOUND) {
					// The resource is no longer exists so ignore the exception
				} else {
					throw e;
				}
			}
			//add remote changed resources (they may not exist locally)
			allMembers.addAll(Arrays.asList( remoteSyncStateStore.members( resource ) ) );

			return (IResource[]) allMembers.toArray(new IResource[allMembers.size()]);
		} catch (CoreException e) {
			throw TeamException.asTeamException(e);
		}
    }

	/* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#getSyncInfo(org.eclipse.core.resources.IResource)
     */
    public SyncInfo getSyncInfo(IResource resource) throws TeamException {
        if( ! isSupervised( resource ) )
            return null;
        
        //LocalResourceStatus localStatus = SVNWorkspaceRoot.getSVNResourceFor( resource );
        LocalResourceStatus localStatus = SVNProviderPlugin.getPlugin().getStatusCacheManager().getStatus(resource);

        StatusInfo remoteStatusInfo = null;
        byte[] remoteBytes = remoteSyncStateStore.getBytes( resource );
        if( remoteBytes != null )
            remoteStatusInfo = StatusInfo.fromBytes(remoteBytes);
        else {
            if( localStatus.hasRemote() )
                remoteStatusInfo = ensureBaseStatusInfo(resource, localStatus, ResourcesPlugin.getWorkspace().getSynchronizer());
        }

        SyncInfo syncInfo = new SVNStatusSyncInfo(resource, new StatusInfo(localStatus), remoteStatusInfo, comparator);
        syncInfo.init();

        return syncInfo;
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.core.subscribers.Subscriber#refresh(org.eclipse.core.resources.IResource[], int, org.eclipse.core.runtime.IProgressMonitor)
     */
    public void refresh(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {
		monitor = Policy.monitorFor(monitor);
		List errors = new ArrayList();
		try {
			monitor.beginTask("Refresing subversion resources", 1000 * resources.length);
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];

				monitor.subTask(resource.getName());
				IStatus status = refresh(resource, depth, monitor);
				if (!status.isOK()) {
					errors.add(status);
				}
			}
		} finally {
			monitor.done();
		} 
		if (!errors.isEmpty()) {
			int numSuccess = resources.length - errors.size();
			throw new TeamException(new MultiStatus(SVNProviderPlugin.ID, 0, 
					(IStatus[]) errors.toArray(new IStatus[errors.size()]), 
					Policy.bind("ResourceVariantTreeSubscriber.1", new Object[] {getName(), Integer.toString(numSuccess), Integer.toString(resources.length)}), null)); //$NON-NLS-1$
		}
    }
	
	private IStatus refresh(IResource resource, int depth, IProgressMonitor monitor) {
		try {
			refreshResourceSyncInfo(resource, monitor);
			monitor.worked(300);

			monitor.setTaskName("Retrieving synchronization data");
			IResource[] changedResources = findChanges(resource, depth);
			monitor.worked(400);

			fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, changedResources));
			monitor.worked(300);
			return Status.OK_STATUS;
		} catch (TeamException e) {
			return new TeamStatus(IStatus.ERROR, SVNProviderPlugin.ID, 0, Policy.bind("ResourceVariantTreeSubscriber.2", resource.getFullPath().toString(), e.getMessage()), e, resource); //$NON-NLS-1$
		} 
	}

	protected void refreshResourceSyncInfo(final IResource resource, final IProgressMonitor monitor) throws TeamException	
	{
		try {
			SVNProviderPlugin.getPlugin().getStatusCacheManager().refreshStatus(resource, IResource.DEPTH_INFINITE);
			final ISynchronizer synchronizer = ResourcesPlugin.getWorkspace().getSynchronizer();
			resource.accept(new IResourceVisitor() {
				public boolean visit(IResource resource) throws CoreException {
					monitor.subTask(resource.getName());
					//LocalResourceStatus status = SVNWorkspaceRoot.getSVNResourceFor( resource );
					LocalResourceStatus status = SVNProviderPlugin.getPlugin().getStatusCacheManager().getStatus(resource);
					ensureBaseStatusInfo(resource, status, synchronizer);
					return true;
				}
			});
			monitor.subTask(" ");			
		} catch (CoreException e) {
			SVNProviderPlugin.log(e.getStatus());
			throw TeamException.asTeamException(e);
		}

	}

	/**
	 * Answer a StatusInfo created from the base(pristine) copy of resource.
	 * Ensure that this info is present in syncInfo of ResourceInfo of the resource.
	 * @param resource IResource of status is determined
	 * @param status prepared LocalResourceStatus of the supplied resource
	 * @param synchronizer ISynchronizer instance used to store syncInfo data to resource 
	 * @return	a StatusInfo representing status of the base copy
	 * @throws TeamException
	 */
	protected StatusInfo ensureBaseStatusInfo(IResource resource, LocalResourceStatus status, ISynchronizer synchronizer) throws TeamException
	{
		try {
			StatusInfo baseStatusInfo = null;
			if( synchronizer.getSyncInfo(qualifiedName, resource) == null ) {
				if( status.hasRemote() ) {
					baseStatusInfo = new StatusInfo(status);
					synchronizer.setSyncInfo(qualifiedName, resource, baseStatusInfo.asBytes());
				}
				else {
					baseStatusInfo = StatusInfo.NONE;
				}
			}
			else
			{
				if( !status.hasRemote() ) 
				{
					//This should not normally happen, but just to be sure ...
					synchronizer.setSyncInfo(qualifiedName, resource, null);
				}
			}
			return baseStatusInfo;
		}
		catch (CoreException e)
		{
			throw TeamException.asTeamException(e);
		}
	}		

	protected void setBaseStatusInfo(IResource resource, LocalResourceStatus status, ISynchronizer synchronizer) throws TeamException
	{
		try {
			if (status.hasRemote()) {
				synchronizer.setSyncInfo(qualifiedName, resource, new StatusInfo(status).asBytes());
			} else {
				synchronizer.flushSyncInfo(qualifiedName, resource,	IResource.DEPTH_ZERO);
			}
		}
		catch (CoreException e)
		{
			throw TeamException.asTeamException(e);
		}
	}		

    private IResource[] findChanges(IResource resource, int depth) throws TeamException {
        System.out.println("SVNWorkspaceSubscriber.refresh()"+resource+" "+depth);		

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot workspaceRoot = workspace.getRoot();
        
        remoteSyncStateStore.flushBytes(resource, depth);

        ISVNClientAdapter client = SVNProviderPlugin.getPlugin().createSVNClient();

        boolean descend = (depth == IResource.DEPTH_INFINITE)? true : false;
        try {
            StatusAndInfoCommand cmd = new StatusAndInfoCommand(SVNWorkspaceRoot.getSVNResourceFor( resource ), descend, false, true );
            cmd.execute( client );

            InformedStatus[] statuses = cmd.getInformedStatuses();

            IResource[] result = new IResource[statuses.length];
            for (int i = 0; i < statuses.length; i++) {				
            	result[i] = statuses[i].getResource();
				
                if (isSupervised(result[i]))
                {
                    StatusInfo remoteInfo = new StatusInfo(cmd.getRevision(), statuses[i].getRepositoryTextStatus(), statuses[i].getRepositoryPropStatus() );
                    remoteSyncStateStore.setBytes( statuses[i].getResource(), remoteInfo.asBytes() );
                }					
                //System.out.println(cmd.getRevision()+" "+changedResource+" R:"+status.getLastChangedRevision()+" L:"+status.getTextStatus()+" R:"+status.getRepositoryTextStatus());
			}
            
            return result;
        } catch (SVNClientException e) {
            throw new TeamException("Error getting status for resource "+resource + " " + e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.tigris.subversion.subclipse.core.IResourceStateChangeListener#resourceSyncInfoChanged(org.eclipse.core.resources.IResource[])
     */
    public void resourceSyncInfoChanged(IResource[] changedResources) {
    	ISynchronizer synchronizer = ResourcesPlugin.getWorkspace().getSynchronizer();
    	try
		{
    		for (int i = 0; i < changedResources.length; i++) {
    			//setBaseStatusInfo(changedResources[i], SVNWorkspaceRoot.getSVNResourceFor( changedResources[i] ).getStatus(), synchronizer );
    			setBaseStatusInfo(changedResources[i], SVNProviderPlugin.getPlugin().getStatusCacheManager().getStatus(changedResources[i]), synchronizer );
    		}
	    }
    	catch (TeamException e)
		{
			SVNProviderPlugin.log(e);
		}    	
		
        internalResourceChanged(changedResources);
    }

    /* (non-Javadoc)
     * @see org.tigris.subversion.subclipse.core.IResourceStateChangeListener#resourceModified(org.eclipse.core.resources.IResource[])
     */
    public void resourceModified(IResource[] changedResources) {
        internalResourceChanged(changedResources);
    }

	/**
     * @param changedResources
     */
    private void internalResourceChanged(IResource[] changedResources) {
        fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, changedResources));
    }

    /* (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.core.IResourceStateChangeListener#projectConfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectConfigured(IProject project) {
		SubscriberChangeEvent delta = new SubscriberChangeEvent(this, ISubscriberChangeEvent.ROOT_ADDED, project);
		fireTeamResourceChange(new SubscriberChangeEvent[] {delta});
	}

	/* (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.core.IResourceStateChangeListener#projectDeconfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectDeconfigured(IProject project) {
		SubscriberChangeEvent delta = new SubscriberChangeEvent(this, ISubscriberChangeEvent.ROOT_REMOVED, project);
		fireTeamResourceChange(new SubscriberChangeEvent[] {delta});
	}
	
	public void updateRemote(IResource[] resources) throws TeamException {
	    for (int i = 0; i < resources.length; i++) {
	        remoteSyncStateStore.flushBytes(resources[i], IResource.DEPTH_INFINITE);
	    }
	}
}
