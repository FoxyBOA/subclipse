package org.tigris.subversion.subclipse.ui.subscriber;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.team.core.synchronize.FastSyncInfoFilter;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.FastSyncInfoFilter.SyncInfoDirectionFilter;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.SynchronizeModelAction;
import org.eclipse.team.ui.synchronize.SynchronizeModelOperation;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.core.util.Util;

public class RevertSynchronizeAction extends SynchronizeModelAction {

    public RevertSynchronizeAction(String text, ISynchronizePageConfiguration configuration) {
        super(text, configuration);
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.SynchronizeModelAction#getSyncInfoFilter()
	 */
	protected FastSyncInfoFilter getSyncInfoFilter() {
		return new SyncInfoDirectionFilter(new int[] {SyncInfo.OUTGOING, SyncInfo.CONFLICTING});
	}

    protected SynchronizeModelOperation getSubscriberOperation(ISynchronizePageConfiguration configuration, IDiffElement[] elements) {
		String url = null;
	    IStructuredSelection selection = getStructuredSelection();
	    if (selection.size() == 1) {
	        ISynchronizeModelElement element = (ISynchronizeModelElement)selection.getFirstElement();
		    IResource resource = element.getResource();
		    ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
            try {
                url = svnResource.getStatus().getUrlString();
                if ((url == null) || (resource.getType() == IResource.FILE)) url = Util.getParentUrl(svnResource);
            } catch (SVNException e) {
                e.printStackTrace();
            }	    
	    }
	    return new RevertSynchronizeOperation(configuration, elements, url);
    }
}