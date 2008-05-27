package org.tigris.subversion.subclipse.ui.subscriber;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.team.core.synchronize.FastSyncInfoFilter;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.SynchronizeModelAction;
import org.eclipse.team.ui.synchronize.SynchronizeModelOperation;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;

public class ShowHistorySynchronizeAction extends SynchronizeModelAction {

    public ShowHistorySynchronizeAction(String text, ISynchronizePageConfiguration configuration) {
        super(text, configuration);
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.SynchronizeModelAction#getSyncInfoFilter()
	 */
	protected FastSyncInfoFilter getSyncInfoFilter() {
		return new FastSyncInfoFilter() {
			public boolean select(SyncInfo info) {
			    IStructuredSelection selection = getStructuredSelection();
			    if (selection.size() != 1) return false;
		        ISynchronizeModelElement element = (ISynchronizeModelElement)selection.getFirstElement();
			    IResource resource = element.getResource();
                ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);			    
                try {
                    return !svnResource.getStatus().isAdded() && svnResource.getStatus().isManaged() && resource.exists();
                } catch (SVNException e) {
                    return false;
                }
			}
		};
	}    

    protected SynchronizeModelOperation getSubscriberOperation(ISynchronizePageConfiguration configuration, IDiffElement[] elements) {
        ISynchronizeModelElement element = (ISynchronizeModelElement)getStructuredSelection().getFirstElement();
        IResource resource = element.getResource();
        return new ShowHistorySynchronizeOperation(configuration, elements, resource);
    }

}