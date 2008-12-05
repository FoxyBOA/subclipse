/*******************************************************************************
 * Copyright (c) 2004, 2006 Subclipse project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Subclipse project committers - initial API and implementation
 ******************************************************************************/
package org.tigris.subversion.subclipse.ui.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.wizard.WizardDialog;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.ui.ISVNUIConstants;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.subclipse.ui.operations.BranchTagOperation;
import org.tigris.subversion.subclipse.ui.wizards.BranchTagWizard;
import org.tigris.subversion.subclipse.ui.wizards.SizePersistedWizardDialog;
import org.tigris.subversion.svnclientadapter.SVNUrl;

public class BranchTagAction extends WorkbenchWindowAction {

    protected void execute(IAction action) throws InvocationTargetException, InterruptedException {
        if (action != null && !action.isEnabled()) { 
        	action.setEnabled(true);
        } 
        else {
	        IResource[] resources = getSelectedResources();
        	BranchTagWizard wizard = new BranchTagWizard(resources);
        	WizardDialog dialog = new SizePersistedWizardDialog(getShell(), wizard, "BranchTag"); //$NON-NLS-1$
        	if (dialog.open() == WizardDialog.OK) {	
        		SVNUrl[] sourceUrls = wizard.getUrls();
        		SVNUrl destinationUrl = wizard.getToUrl();
        		String message = wizard.getComment();
        		boolean createOnServer = wizard.isCreateOnServer();
	            BranchTagOperation branchTagOperation = new BranchTagOperation(getTargetPart(), getSelectedResources(), sourceUrls, destinationUrl, createOnServer, wizard.getRevision(), message);
	            branchTagOperation.setMakeParents(wizard.isMakeParents());
	            branchTagOperation.setMultipleTransactions(wizard.isSameStructure());
	            branchTagOperation.setNewAlias(wizard.getNewAlias());
	            branchTagOperation.switchAfterTagBranchOperation(wizard.isSwitchAfterBranchTag());
	            branchTagOperation.run();        		
        	}
//	        for (int i = 0; i < resources.length; i++) {
//	        	SvnWizardBranchTagPage branchTagPage = new SvnWizardBranchTagPage(resources[i]);
//	        	SvnWizard wizard = new SvnWizard(branchTagPage);
//		        SvnWizardDialog dialog = new SvnWizardDialog(getShell(), wizard);
//		        wizard.setParentDialog(dialog);    
//		        if (dialog.open() == SvnWizardDialog.OK) {
//		            SVNUrl sourceUrl = branchTagPage.getUrl();
//		            SVNUrl destinationUrl = branchTagPage.getToUrl();
//		            String message = branchTagPage.getComment();
//		            boolean createOnServer = branchTagPage.isCreateOnServer();
//		            BranchTagOperation branchTagOperation = new BranchTagOperation(getTargetPart(), getSelectedResources(), sourceUrl, destinationUrl, createOnServer, branchTagPage.getRevision(), message);
//		            branchTagOperation.setMakeParents(branchTagPage.isMakeParents());
//		            branchTagOperation.setNewAlias(branchTagPage.getNewAlias());
//		            branchTagOperation.switchAfterTagBranchOperation(branchTagPage.switchAfterTagBranch());
//		            branchTagOperation.run();
//		        }
//	        }
        }
    }
    
	/*
	 * (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.ui.actions.SVNAction#getErrorTitle()
	 */
	protected String getErrorTitle() {
		return Policy.bind("BranchTagAction.branch"); //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.ui.actions.WorkspaceAction#isEnabledForManagedResources()
	 */
	protected boolean isEnabledForManagedResources() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.ui.actions.WorkspaceAction#isEnabledForUnmanagedResources()
	 */
	protected boolean isEnabledForUnmanagedResources() {
		return false;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.ui.actions.WorkspaceAction#isEnabledForMultipleResources()
	 */
	protected boolean isEnabledForMultipleResources() {
		try {
			// Must all be from same repository.
			ISVNRepositoryLocation repository = null;
			IResource[] selectedResources = getSelectedResources();
			for (int i = 0; i < selectedResources.length; i++) {
				ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(selectedResources[i]);
				if (svnResource == null || !svnResource.isManaged()) return false;
				if (repository != null && !svnResource.getRepository().equals(repository)) return false;
				repository = svnResource.getRepository();
			}
			return true;
		} catch (Exception e) { return false; }
	}	   	        

	/*
	 * (non-Javadoc)
	 * @see org.tigris.subversion.subclipse.ui.actions.WorkspaceAction#isEnabledForAddedResources()
	 */
    protected boolean isEnabledForAddedResources() {
        return false;
    }

	/*
	 * @see org.tigris.subversion.subclipse.ui.actions.ReplaceableIconAction#getImageId()
	 */
	protected String getImageId() {
		return ISVNUIConstants.IMG_MENU_BRANCHTAG;
	}
}
