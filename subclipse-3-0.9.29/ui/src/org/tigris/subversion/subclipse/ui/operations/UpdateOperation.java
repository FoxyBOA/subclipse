/*
 * Created on 29 ��� 2004
 */
package org.tigris.subversion.subclipse.ui.operations;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.ui.IWorkbenchPart;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNTeamProvider;
import org.tigris.subversion.subclipse.core.commands.UpdateResourcesCommand;
import org.tigris.subversion.subclipse.core.sync.SVNWorkspaceSubscriber;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.svnclientadapter.SVNRevision;

/**
 * @author Panagiotis K
 */
public class UpdateOperation extends RepositoryProviderOperation {
	private final SVNRevision revision;

    /**
     * @param part
     * @param resources
     */
    public UpdateOperation(IWorkbenchPart part, IResource[] resources, SVNRevision revision) {
        super(part, resources);
        this.revision = revision;
    }

    /* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#getTaskName()
	 */
	protected String getTaskName() {
		return Policy.bind("UpdateOperation.taskName"); //$NON-NLS-1$;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.RepositoryProviderOperation#getTaskName(org.eclipse.team.internal.ccvs.core.CVSTeamProvider)
	 */
	protected String getTaskName(SVNTeamProvider provider) {
		return Policy.bind("UpdateOperation.0", provider.getProject().getName()); //$NON-NLS-1$
	}


    /* (non-Javadoc)
     * @see org.tigris.subversion.subclipse.ui.operations.RepositoryProviderOperation#execute(org.tigris.subversion.subclipse.core.SVNTeamProvider, org.eclipse.core.resources.IResource[], org.eclipse.core.runtime.IProgressMonitor)
     */
    protected void execute(SVNTeamProvider provider, IResource[] resources, IProgressMonitor monitor) throws SVNException, InterruptedException {
        monitor.beginTask(null, 100);
		try {			
		    SVNWorkspaceSubscriber.getInstance().updateRemote(resources);
	    	UpdateResourcesCommand command = new UpdateResourcesCommand(provider.getSVNWorkspaceRoot(),resources, revision);
	        command.run(Policy.subMonitorFor(monitor,1000));
			//updateWorkspaceSubscriber(provider, resources, Policy.subMonitorFor(monitor, 5));
		} catch (SVNException e) {
		    collectStatus(e.getStatus());
		} catch (TeamException e) {
		    collectStatus(e.getStatus());
        } finally {
			monitor.done();
		}
    }
}