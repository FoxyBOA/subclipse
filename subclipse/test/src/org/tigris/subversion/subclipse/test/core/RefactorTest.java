/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     C�dric Chabanois (cchabanois@ifrance.com) - modified for Subversion 
 *******************************************************************************/
package org.tigris.subversion.subclipse.test.core;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.SVNTeamProvider;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.test.SubclipseTest;
import org.tigris.subversion.subclipse.test.TestProject;
import org.tigris.subversion.svnclientadapter.ISVNStatus.Kind;

public class RefactorTest extends SubclipseTest {

	public RefactorTest(String name) {
		super(name);
	}

	public void testRename() throws Exception {
		TestProject testProject = new TestProject("testProject");
		shareProject(testProject.getProject());
		
		// create a file
		IPackageFragment package1 = testProject.createPackage("pack1");
		IType type = testProject.createJavaType(package1,"AClass.java",
			"public class AClass { \n" +
			"  public void m() {}\n" +
			"}");
			
		IFile resource = testProject.getProject().getFile(new Path("src/pack1/AClass.java"));
		
		SVNTeamProvider provider = getProvider(testProject.getProject());
		
		// add it to repository
		provider.add(new IResource[] {resource},IResource.DEPTH_ZERO, null);
			
		// commit it
		provider.checkin(new IResource[] {resource},"committed",IResource.DEPTH_ZERO,null);

		// let's rename the resource
		resource.move(new Path("AClassRenamed.java"),false, null);
		
		// make sure the initial resource is not there anymore
		assertFalse(resource.exists());
		
		// the initial resource should have "DELETED" status
		ISVNLocalResource svnResource;
		svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
		assertEquals(svnResource.getStatus().getTextStatus(), Kind.DELETED);
		
		// the renamed resource should exist now
		resource = testProject.getProject().getFile(new Path("src/pack1/AClassRenamed.java"));
		assertTrue(resource.exists());
		
		// and should have "ADDED" status
		svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
		assertEquals(svnResource.getStatus().getTextStatus(), Kind.ADDED);
	}

}
