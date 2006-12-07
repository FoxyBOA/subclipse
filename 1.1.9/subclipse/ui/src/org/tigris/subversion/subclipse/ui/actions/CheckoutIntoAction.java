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
package org.tigris.subversion.subclipse.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.tigris.subversion.subclipse.core.ISVNRemoteFolder;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.subclipse.ui.WorkspacePathValidator;
import org.tigris.subversion.subclipse.ui.operations.CheckoutAsProjectOperation;
import org.tigris.subversion.subclipse.ui.util.PromptingDialog;
import org.tigris.subversion.svnclientadapter.SVNRevision;



public class CheckoutIntoAction extends CheckoutAsProjectAction {
	
	protected IPath intoDir;
	private ISVNRemoteFolder[] selectedFolders;
	private String projectName;
	private String intoDirectory;
	private SVNRevision svnRevision = SVNRevision.HEAD;
	
	public CheckoutIntoAction(ISVNRemoteFolder[] selectedFolders, String projectName, String intoDirectory, Shell shell) {
		super();
		this.selectedFolders = selectedFolders;
		this.projectName = projectName;
		this.intoDirectory = intoDirectory;
		this.shell = shell;
	}

	/*
	 * @see SVNAction#execute()
	 */
	public void execute(IAction action) throws InvocationTargetException, InterruptedException {
	    if (intoDirectory == null) {
			if (!WorkspacePathValidator.validateWorkspacePath()) return;
		    DirectoryDialog intoDirDia = new DirectoryDialog(shell);
		    intoDirDia.setMessage(Policy.bind("CheckoutInto.message"));
	    	String intoDirString = intoDirDia.open();
	    	if (intoDirString==null) {
	    		return;
	    	}
	    	intoDir = new Path(intoDirString);
	    } else intoDir = new Path(intoDirectory);
	    checkoutSelectionIntoWorkspaceDirectory();
	}
	
	/**
     * checkout into a workspace directory, ie as a project
     * @throws InvocationTargetException
     * @throws InterruptedException
     */	
	protected void checkoutSelectionIntoWorkspaceDirectory() throws InvocationTargetException, InterruptedException { 
	    run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
			    try {
					final ISVNRemoteFolder[] folders = getSelectedRemoteFolders();
							
					List targetProjects = new ArrayList();
					Map targetFolders = new HashMap();

					monitor.beginTask(null, 100);
					for (int i = 0; i < folders.length; i++) {
					    proceed = true;
					    if (folders[i].getRepository().getRepositoryRoot().toString().equals(folders[i].getUrl().toString())) {
						    shell.getDisplay().syncExec(new Runnable() {
	                            public void run() {
	        					     proceed = MessageDialog.openQuestion(shell, Policy.bind("CheckoutAsProjectAction.title"), Policy.bind("AddToWorkspaceAction.checkingOutRoot")); //$NON-NLS-1$                               
	                            }					        
						    });					        
					    }
					    if (proceed) {
					    	IProject project;
					    	if (projectName == null)
					    		project = SVNWorkspaceRoot.getProject(folders[i],monitor);
					    	else
					    		project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
					    	targetFolders.put(project.getName(), folders[i]);
							targetProjects.add(project);
					    } else return;
					}
					

					projects = (IResource[]) targetProjects.toArray(new IResource[targetProjects.size()]);
					
					// if a project with the same name already exist, we ask the user if he want to overwrite it
					PromptingDialog prompt = new PromptingDialog(getShell(), projects, 
																  getOverwriteLocalAndFileSystemPrompt(), 
																  Policy.bind("ReplaceWithAction.confirmOverwrite"));//$NON-NLS-1$
					projects = prompt.promptForMultiple();
															
					if (projects.length != 0) {
						localFolders = new IProject[projects.length];
						remoteFolders = new ISVNRemoteFolder[projects.length];
						for (int i = 0; i < projects.length; i++) {
							localFolders[i] = (IProject)projects[i];
							remoteFolders[i] = (ISVNRemoteFolder)targetFolders.get(projects[i].getName());
						}
					}
				} catch (Exception e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		}, true /* cancelable */, PROGRESS_DIALOG);
	    if (proceed) {
	    	CheckoutAsProjectOperation checkoutAsProjectOperation = new CheckoutAsProjectOperation(getTargetPart(), remoteFolders, localFolders, intoDir);
	    	checkoutAsProjectOperation.setSvnRevision(svnRevision);
	    	checkoutAsProjectOperation.run();
	    }
	}

	protected ISVNRemoteFolder[] getSelectedRemoteFolders() {
		if (selectedFolders != null) return selectedFolders;
		return super.getSelectedRemoteFolders();
	}

	public void setSvnRevision(SVNRevision svnRevision) {
		this.svnRevision = svnRevision;
	}

}
