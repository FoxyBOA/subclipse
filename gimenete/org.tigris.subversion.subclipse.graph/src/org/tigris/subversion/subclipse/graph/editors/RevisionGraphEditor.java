package org.tigris.subversion.subclipse.graph.editors;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.MouseWheelHandler;
import org.eclipse.gef.MouseWheelZoomHandler;
import org.eclipse.gef.RootEditPart;
import org.eclipse.gef.editparts.ScalableRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.gef.ui.parts.ScrollingGraphicalViewer;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.tigris.subversion.sublicpse.graph.cache.Cache;
import org.tigris.subversion.sublicpse.graph.cache.WorkListener;
import org.tigris.subversion.svnclientadapter.ISVNLogMessage;
import org.tigris.subversion.svnclientadapter.ISVNLogMessageCallback;

public class RevisionGraphEditor extends EditorPart {

	private OverviewOutlinePage overviewOutlinePage;
	private ScrollingGraphicalViewer viewer;

	private ActionRegistry actionRegistry;

	public ActionRegistry getActionRegistry() {
		if (actionRegistry == null)
			actionRegistry = new ActionRegistry();
		return actionRegistry;
	}

	public void setFocus() {
	}
	
	public void showGraphFor(IResource resource) {
		setPartName(resource.getName()+" revision graph");
//		setContentDescription("Revision graph for "+resource.getName());
		GraphBackgroundTask task =
			new GraphBackgroundTask(getSite().getPart(), viewer, this, resource);
		try {
			task.run();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void showGraphFor(RevisionGraphEditorInput editorInput) {
		setPartName(editorInput.getName() + " revision graph");
		GraphBackgroundTask task;
		if (editorInput.getResource() == null) task = new GraphBackgroundTask(getSite().getPart(), viewer, this, editorInput.getRemoteResource());
		else task = new GraphBackgroundTask(getSite().getPart(), viewer, this, editorInput.getResource());
		try {
			task.run();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Object getAdapter(Class adapter) {
		if(adapter == GraphicalViewer.class ||
				adapter == EditPartViewer.class) {
			return viewer;
		} else if(adapter == ZoomManager.class) {
			return ((ScalableRootEditPart) viewer.getRootEditPart()).getZoomManager();
		} else if (adapter == IContentOutlinePage.class) {
			return getOverviewOutlinePage();
		}
		return super.getAdapter(adapter);
	}
	
	public void createPartControl(Composite parent) {
		GC gc = new GC(parent);
		gc.setAntialias(SWT.ON);
		viewer = new ScrollingGraphicalViewer();
		viewer.createControl(parent);
		ScalableRootEditPart root = new ScalableRootEditPart();
		viewer.setRootEditPart(root);
		viewer.setEditPartFactory(new GraphEditPartFactory(viewer));
		viewer.setContents("Loading graph... This can take several minutes");
		ContextMenuProvider cmProvider = new RevisionGraphMenuProvider(viewer, this);
		viewer.setContextMenu(cmProvider);
//		getSite().registerContextMenu(cmProvider, viewer);
		IEditorInput input = getEditorInput();
		if(input instanceof FileEditorInput) {
			FileEditorInput fileEditorInput = (FileEditorInput) input;
			showGraphFor(fileEditorInput.getFile());
		} else if(input instanceof RevisionGraphEditorInput) {
			RevisionGraphEditorInput editorInput = (RevisionGraphEditorInput) input;
			showGraphFor(editorInput);
		}
		
		// zoom stuff
		ZoomManager zoomManager = ((ScalableRootEditPart) viewer.getRootEditPart()).getZoomManager();
		IAction zoomIn = new ZoomInAction(zoomManager);
		IAction zoomOut = new ZoomOutAction(zoomManager);
		getActionRegistry().registerAction(zoomIn);
		getActionRegistry().registerAction(zoomOut);
		// keyboard
		getSite().getKeyBindingService().registerAction(zoomIn); // FIXME, deprecated
		getSite().getKeyBindingService().registerAction(zoomOut); // FIXME, deprecated
		List zoomContributions = Arrays.asList(new String[] { 
			     ZoomManager.FIT_ALL, 
			     ZoomManager.FIT_HEIGHT, 
			     ZoomManager.FIT_WIDTH });
		zoomManager.setZoomLevelContributions(zoomContributions);
		// mouse wheel
		viewer.setProperty(MouseWheelHandler.KeyGenerator.getKey(SWT.MOD1),
				MouseWheelZoomHandler.SINGLETON);
	}
	
	public void refresh() {
		viewer.setContents("Loading graph... This can take several minutes");
		showGraphFor((RevisionGraphEditorInput)getEditorInput());
	}

	public void doSave(IProgressMonitor monitor) {
	}

	public void doSaveAs() {
	}

	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		setSite(site);
		setInput(input);
	}

	public boolean isDirty() {
		return false;
	}

	public boolean isSaveAsAllowed() {
		return false;
	}
	
	public ScrollingGraphicalViewer getViewer() {
		return viewer;
	}
	
	protected OverviewOutlinePage getOverviewOutlinePage() {
		if(null == overviewOutlinePage && null != viewer) {
			RootEditPart rootEditPart = viewer.getRootEditPart();
			if(rootEditPart instanceof ScalableRootEditPart) {
				overviewOutlinePage =
					new OverviewOutlinePage(
							(ScalableRootEditPart) rootEditPart);
			}
		}
		return overviewOutlinePage;
	}

} class WorkMonitorListener implements WorkListener {
	
	private IProgressMonitor monitor;
	private int unitWork;
	
	public WorkMonitorListener(IProgressMonitor monitor, int unitWork) {
		this.monitor = monitor;
		this.unitWork = unitWork;
	}

	public void worked() {
		monitor.worked(unitWork);
	}

} class CallbackUpdater implements ISVNLogMessageCallback {
	
	private Cache cache;
	private IProgressMonitor monitor;
	private int unitWork;
	
	public CallbackUpdater(Cache cache, IProgressMonitor monitor, int unitWork) {
		this.cache = cache;
		this.monitor = monitor;
		this.unitWork = unitWork;
	}

	public void singleMessage(ISVNLogMessage message) {
		cache.update(message);
		monitor.worked(unitWork);
	}

}