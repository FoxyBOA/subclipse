package org.tigris.subversion.subclipse.graph.editors;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.XYAnchor;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.MouseWheelHandler;
import org.eclipse.gef.MouseWheelZoomHandler;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;
import org.eclipse.gef.editparts.ScalableRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.gef.ui.actions.ZoomComboContributionItem;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.gef.ui.parts.ScrollingGraphicalViewer;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.subclipse.ui.operations.SVNOperation;
import org.tigris.subversion.sublicpse.graph.cache.Cache;
import org.tigris.subversion.sublicpse.graph.cache.CacheException;
import org.tigris.subversion.sublicpse.graph.cache.Graph;
import org.tigris.subversion.sublicpse.graph.cache.WorkListener;
import org.tigris.subversion.sublicpse.graph.cache.Node;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.ISVNLogMessage;
import org.tigris.subversion.svnclientadapter.ISVNLogMessageCallback;
import org.tigris.subversion.svnclientadapter.SVNRevision;

public class RevisionGraphEditor extends EditorPart {

	private GraphicalViewer viewer;
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
		ShowGraphBackgroundTask task =
			new ShowGraphBackgroundTask(getSite().getPart(), viewer, resource);
		try {
			task.run();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Object getAdapter(Class adapter) {
		if(adapter == GraphicalViewer.class || adapter == EditPartViewer.class) {
			return viewer;
		} else if(adapter == ZoomManager.class) {
			return ((ScalableRootEditPart) viewer.getRootEditPart()).getZoomManager();
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
		viewer.setEditPartFactory(new GraphEditPartFactory());
		viewer.setContents("Loading graph... This can take several minutes");
		IEditorInput input = getEditorInput();
		if(input instanceof FileEditorInput) {
			FileEditorInput fileEditorInput = (FileEditorInput) input;
			showGraphFor(fileEditorInput.getFile());
		} else if(input instanceof RevisionGraphEditorInput) {
			RevisionGraphEditorInput editorInput = (RevisionGraphEditorInput) input;
			showGraphFor(editorInput.getResource());
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
		// toolbar
		IToolBarManager mgr = getEditorSite().getActionBars().getToolBarManager();
		mgr.add(new ZoomComboContributionItem(getSite().getPage()));
		// menu
		// mouse wheel
		viewer.setProperty(MouseWheelHandler.KeyGenerator.getKey(SWT.MOD1),
				MouseWheelZoomHandler.SINGLETON);
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

} class ShowGraphBackgroundTask extends SVNOperation {
	
	private IResource resource;
	private GraphicalViewer viewer;

	private static final int TOTAL_STEPS = Integer.MAX_VALUE;
	private static final int SHORT_TASK_STEPS = TOTAL_STEPS / 50; // 2%
	private static final int VERY_LONG_TASK = TOTAL_STEPS / 2; // 50%
	private static final int TASK_STEPS = TOTAL_STEPS / 10; // 10%
	
	protected ShowGraphBackgroundTask(IWorkbenchPart part, GraphicalViewer viewer, IResource resource) {
		super(part);
		this.viewer = viewer;
		this.resource = resource;
	}

	protected void execute(IProgressMonitor monitor) throws SVNException,
			InterruptedException {
		Cache cache = null;
		monitor.beginTask("Calculating graph information", TOTAL_STEPS);
		monitor.worked(SHORT_TASK_STEPS);
		try {
			ISVNClientAdapter client = SVNProviderPlugin.getPlugin().getSVNClient();
			ISVNInfo info = client.getInfoFromWorkingCopy(resource.getRawLocation().toFile());
			
			long revision = info.getRevision().getNumber();
			String path = info.getUrl().toString().substring(info.getRepository().toString().length());
			
			monitor.setTaskName("Initializating cache");
			cache = getCache(resource, info.getUuid());
			monitor.worked(SHORT_TASK_STEPS);
			
			// update the cache
			long latestRevisionStored = cache.getLatestRevision();
			SVNRevision latest = null;
			monitor.setTaskName("Connecting to the repository");
			long latestRevisionInRepository = client.getInfo(info.getRepository()).getRevision().getNumber();
			monitor.worked(SHORT_TASK_STEPS);

			if(latestRevisionInRepository > latestRevisionStored) {
				if(latestRevisionStored == 0)
					latest = SVNRevision.START;
				else
					latest = new SVNRevision.Number(latestRevisionStored);

				try {
					monitor.setTaskName("Retrieving revision history");
					int unitWork = VERY_LONG_TASK / (int) (latestRevisionInRepository - latestRevisionStored);
					
					cache.startUpdate();
					client.getLogMessages(info.getRepository(),
							latest,
							latest,
							SVNRevision.HEAD,
							false, true, 0, false, null,
							new CallbackUpdater(cache, monitor, unitWork));
					cache.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
			updateView(monitor, cache, path, revision);
			monitor.done();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		} finally {
//			if(cache != null)
//				cache.close();
			// TODO: clean up ISVNClientAdapter ?
		}
	}
	
	private void updateView(IProgressMonitor monitor, Cache cache, String path, long revision) {
		monitor.setTaskName("Finding root node");
		
		int unitWork = TASK_STEPS / (int)(revision);
		if(unitWork < 1) unitWork = 1;
		Node root = cache.findRootNode(path, revision,
				new WorkMonitorListener(monitor, unitWork));
		
		monitor.setTaskName("Calculating graph");
		unitWork = TASK_STEPS / (int)(revision - root.getRevision());
		if(unitWork < 1) unitWork = 1;
		final Graph graph = cache.createGraph(
				root.getPath(),
				root.getRevision(),
				new WorkMonitorListener(monitor, unitWork));
		monitor.setTaskName("Drawing graph");
		
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				viewer.setContents(graph);
			}
		});
	}
	
	private Cache getCache(IResource file, String uuid) {
		File database = file.getWorkspace().getRoot().getRawLocation().toFile();
		database = new File(database, ".metadata");
		database = new File(database, ".plugins");
		database = new File(database, "org.tigris.subversion.subclipse.graph");
		database = new File(database, uuid);
		return new Cache(database);
	}

	protected String getTaskName() {
		return "Calculating graph information";
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
	private int count;
	private CacheUpdaterThread thread;
	private int unitWork;

	private static final int MAX_BATCH_SIZE = 200;
	
	public CallbackUpdater(Cache cache, IProgressMonitor monitor, int unitWork) {
		this.cache = cache;
		this.monitor = monitor;
		this.unitWork = unitWork;
	}

	public void singleMessage(ISVNLogMessage message) {
		cache.update(message);
		monitor.worked(unitWork);
		count++;
		if(count > MAX_BATCH_SIZE) {
			if(thread != null) {
				try {
					thread.join();
				} catch (InterruptedException e) {
					throw new CacheException("Error while updating cache");
				}
			}
			thread = new CacheUpdaterThread(cache);
			thread.run();
//			cache.executeUpdate();
//			cache.startUpdate();
			count = 0;
		}
	}

} class CacheUpdaterThread extends Thread {
	
	private Cache cache;
	
	public CacheUpdaterThread(Cache cache) {
		this.cache = cache;
	}

	public void run() {
		cache.executeUpdate();
		cache.startUpdate();
	}

} class GraphEditPartFactory implements EditPartFactory {

	public EditPart createEditPart(EditPart editPart, Object node) {
		if (node instanceof String) {
			final String s = (String) node;
			return new AbstractGraphicalEditPart() {
				protected IFigure createFigure() {
					return new Label(s);
				}

				protected void createEditPolicies() {
				}
			};
		} else if (node instanceof Graph) {
			return new GraphEditPart((Graph) node);
		}
		throw new RuntimeException("cannot create EditPart for "+node.getClass().getName()+" class");
	}

} class MyXYAnchor extends XYAnchor {
	
	private IFigure f;

	public MyXYAnchor(Point point, IFigure f) {
		super(point);
		this.f = f;
	}
	
	public Point getLocation(Point reference) {
		Point p = super.getLocation(reference).getCopy();
		f.translateToAbsolute(p);
		return p;
	}
	
	public IFigure getOwner() {
		return f;
	}
	
}
/*
class Branch {
	
	private static final Comparator c = new Comparator() {
		public int compare(Object a, Object b) {
			long ra;
			long rb;
			if(a instanceof Long) {
				ra = ((Long) a).longValue();
			} else if(a instanceof NodeFigure) {
				ra = ((NodeFigure) a).getNode().getRevision();
			} else {
				throw new RuntimeException();
			}
			if(b instanceof Long) {
				rb = ((Long) b).longValue();
			} else if(b instanceof NodeFigure) {
				rb = ((NodeFigure) b).getNode().getRevision();
			} else {
				throw new RuntimeException();
			}
			if(ra < rb) {
				return -1;
			} else if(ra > rb) {
				return 1;
			}
			return 0;
		}
	};
	
	private BranchFigure branch;
	private List nodes = new ArrayList();
	private Figure last = null;
	
	public Branch(BranchFigure branch) {
		this.branch = branch;
		this.last = branch;
	}
	
	public void addNode(NodeFigure f) {
		nodes.add(f);
		last = f;
	}
	
	public Figure getLast() {
		return last;
	}
	
	public NodeFigure get(long revision) {
		int index = Collections.binarySearch(nodes, new Long(revision), c);
		if(index < 0) {
			index = -index-2;
			if(index < 0) {
				return null;
			}
		}
		return (NodeFigure) nodes.get(index);
	}
	
	public BranchFigure getBranchFigure() {
		return branch;
	}
	
	public List getNodes() {
		return nodes;
	}
	
}
*/