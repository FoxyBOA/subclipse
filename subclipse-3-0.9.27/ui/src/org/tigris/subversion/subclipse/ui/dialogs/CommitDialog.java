package org.tigris.subversion.subclipse.ui.dialogs;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.team.core.TeamException;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.internal.util.SWTResourceUtil;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.resources.LocalResourceStatus;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.ui.IHelpContextIds;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.subclipse.ui.SVNUIPlugin;
import org.tigris.subversion.subclipse.ui.comments.CommitCommentArea;
import org.tigris.subversion.subclipse.ui.settings.ProjectProperties;
import org.tigris.subversion.subclipse.ui.util.TableSetter;
import org.tigris.subversion.svnclientadapter.SVNStatusKind;

public class CommitDialog extends Dialog {
    
	private static final int WIDTH_HINT = 500;
	private final static int SELECTION_HEIGHT_HINT = 100;
    
    private CommitCommentArea commitCommentArea;
    private IResource[] resourcesToCommit;
    private String url;
    private boolean unaddedResources;
    private ProjectProperties projectProperties;
    private Object[] selectedResources;
    private CheckboxTableViewer listViewer;
    private Text issueText;
    private String issue;
    
    private IDialogSettings settings;
    private TableSetter setter;
    private int sorterColumn = 1;
    private boolean sorterReversed = false;

    public CommitDialog(Shell parentShell, IResource[] resourcesToCommit, String url, boolean unaddedResources, ProjectProperties projectProperties) {
        super(parentShell);
		int shellStyle = getShellStyle();
		setShellStyle(shellStyle | SWT.RESIZE);
		commitCommentArea = new CommitCommentArea(this, null);
		this.resourcesToCommit = resourcesToCommit;
		this.url = url;
		this.unaddedResources = unaddedResources;
		this.projectProperties = projectProperties;
		settings = SVNUIPlugin.getPlugin().getDialogSettings();
		setter = new TableSetter();
    }
    
	/*
	 * @see Dialog#createDialogArea(Composite)
	 */
	protected Control createDialogArea(Composite parent) {
	    
		if (url == null) getShell().setText(Policy.bind("CommitDialog.commitTo") + " " + Policy.bind("CommitDialog.multiple")); //$NON-NLS-1$
		else getShell().setText(Policy.bind("CommitDialog.commitTo") + " " + url);
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		if (projectProperties != null) {
		    addBugtrackingArea(composite);
		}

		commitCommentArea.createArea(composite);
		commitCommentArea.addPropertyChangeListener(new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty() == CommitCommentArea.OK_REQUESTED)
					okPressed();
			}
		});

		addResourcesArea(composite);
				
		// set F1 help
		WorkbenchHelp.setHelp(composite, IHelpContextIds.RELEASE_COMMENT_DIALOG);	
		
		return composite;
	}
	
    private void addResourcesArea(Composite composite) {
	    
		// add a description label
		Label label = createWrappingLabel(composite);
		label.setText(Policy.bind("CommitDialog.resources")); //$NON-NLS-1$
		// add the selectable checkbox list
		Table table = new Table(composite, 
                SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | 
                SWT.MULTI | SWT.CHECK | SWT.BORDER);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		TableLayout layout = new TableLayout();
		table.setLayout(layout);
		
		listViewer = new CheckboxTableViewer(table);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.heightHint = SELECTION_HEIGHT_HINT;
		data.widthHint = WIDTH_HINT;
		listViewer.getTable().setLayoutData(data);
		createColumns(table, layout);
		// set the contents of the list
		listViewer.setLabelProvider(new ITableLabelProvider() {
			public String getColumnText(Object element, int columnIndex) {
			   String result = null;
			   switch (columnIndex) {
				case 0 :
	    			result = ""; 
					break;			
	            case 1:
	                if (url == null) result = ((IResource)element).getFullPath().toString();
	                else result = getResource((IResource)element);
	                if (result.length() == 0) result = ((IResource)element).getFullPath().toString();
	                break;
	            case 2:
				    result = getStatus((IResource)element);
	                break;
	            case 3:
				    result = getPropertyStatus((IResource)element);
	                break;	                
	            default:
	                result = "";
	                break;
	            }

			   return result;
			}
			// Strip off segments of path that are included in URL.
			private String getResource(IResource resource) {
			    String[] segments = resource.getFullPath().segments();
			    StringBuffer path = new StringBuffer();
			    for (int i = 0; i < segments.length; i++) {
			        path.append("/" + segments[i]);
			        if (url.endsWith(path.toString())) {
			            if (i == (segments.length - 2)) 
			                return resource.getFullPath().toString().substring(path.length() + 1);
			            else 
			                return resource.getFullPath().toString().substring(path.length());
			        }
			    }
                return resource.getFullPath().toString();
            }
            public Image getColumnImage(Object element, int columnIndex) {
			    if (columnIndex == 1) {
			        if (element instanceof IAdaptable) {
						IWorkbenchAdapter adapter = (IWorkbenchAdapter) ((IAdaptable) element).getAdapter(
								IWorkbenchAdapter.class);
						if (adapter == null) {
							return null;
						}
						ImageDescriptor descriptor = adapter.getImageDescriptor(element);
						if (descriptor == null) return null;
						Image image = (Image) SWTResourceUtil.getImageTable().get(descriptor);
						if (image == null) {
							image = descriptor.createImage();
							SWTResourceUtil.getImageTable().put(descriptor, image);
						}
						return image;						
			        }
			    }
				return null;
			}
            public void addListener(ILabelProviderListener listener) {
            }
            public void dispose() {
            }
            public boolean isLabelProperty(Object element, String property) {
                return false;
            }
            public void removeListener(ILabelProviderListener listener) {
            }
		});

		int sort = setter.getSorterColumn("CommitDialog"); //$NON-NLS-1$
		if (sort != -1) sorterColumn = sort;
		CommitSorter sorter = new CommitSorter(sorterColumn);
		sorter.setReversed(setter.getSorterReversed("CommitDialog")); //$NON-NLS-1$
		listViewer.setSorter(sorter);
		
		listViewer.setContentProvider(new IStructuredContentProvider() {
            public Object[] getElements(Object inputElement) {
                return resourcesToCommit;
            }
            public void dispose() {
            }
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            }	    
		});
		listViewer.setInput(new AdaptableResourceList(resourcesToCommit));
		if (selectedResources == null) {
		    setChecks();
		} else {
			listViewer.setCheckedElements(selectedResources);
		}
		listViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				selectedResources = listViewer.getCheckedElements();
			}
		});
		
		addSelectionButtons(composite);
		
    }
	
	private void addBugtrackingArea(Composite composite) {
		Composite bugtrackingComposite = new Composite(composite, SWT.NULL);
		GridLayout bugtrackingLayout = new GridLayout();
		bugtrackingLayout.numColumns = 2;
		bugtrackingComposite.setLayout(bugtrackingLayout);
		
		Label label = new Label(bugtrackingComposite, SWT.NONE);
		label.setText(projectProperties.getLabel());
		issueText = new Text(bugtrackingComposite, SWT.BORDER);
		GridData data = new GridData();
		data.widthHint = 150;
		issueText.setLayoutData(data);
    }
	
    protected void okPressed() {
        saveLocation();
        if (projectProperties != null) {
            issue = issueText.getText().trim();
            if (projectProperties.isWarnIfNoIssue() && (issueText.getText().trim().length() == 0)) {
                if (!MessageDialog.openQuestion(getShell(), Policy.bind("CommitDialog.title"), Policy.bind("CommitDialog.0", projectProperties.getLabel()))) {
                    issueText.setFocus();
                    return; //$NON-NLS-1$
                }
            }
            if (issueText.getText().trim().length() > 0) {
                String issueError = projectProperties.validateIssue(issueText.getText().trim());
                if (issueError != null) {
                    MessageDialog.openError(getShell(), Policy.bind("CommitDialog.title"), issueError); //$NON-NLS-1$
                    issueText.selectAll();
                    issueText.setFocus();
                    return;
                }
            }
        }
        super.okPressed();
    }
    
    protected void cancelPressed() {
        saveLocation();
        super.cancelPressed();
    }

    private void saveLocation() {
        int x = getShell().getLocation().x;
        int y = getShell().getLocation().y;
        settings.put("CommitDialog.location.x", x); //$NON-NLS-1$
        settings.put("CommitDialog.location.y", y); //$NON-NLS-1$
        x = getShell().getSize().x;
        y = getShell().getSize().y;
        settings.put("CommitDialog.size.x", x); //$NON-NLS-1$
        settings.put("CommitDialog.size.y", y); //$NON-NLS-1$   
        TableSetter setter = new TableSetter();
        setter.saveColumnWidths(listViewer.getTable(), "CommitDialog"); //$NON-NLS-1$
        setter.saveSorterColumn("CommitDialog", sorterColumn); //$NON-NLS-1$
        setter.saveSorterReversed("CommitDialog", sorterReversed); //$NON-NLS-1$
    }

    private static String getStatus(IResource resource) {
	    ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
        String result = null;
	       try {
	           LocalResourceStatus status = svnResource.getStatus();
	           if (status.isTextConflicted())
	               result = Policy.bind("CommitDialog.conflicted"); //$NON-NLS-1$
	           else
	           if (status.isAdded())
                   result = Policy.bind("CommitDialog.added"); //$NON-NLS-1$
               else
               if (status.isDeleted())
                   result = Policy.bind("CommitDialog.deleted"); //$NON-NLS-1$
               else
               if (status.isTextModified())
                   result = Policy.bind("CommitDialog.modified"); //$NON-NLS-1$				           
               else
               if (!status.isManaged())
                   result = Policy.bind("CommitDialog.unversioned"); //$NON-NLS-1$
               else
                   result = "";
			} catch (TeamException e) {
			    result = "";
			}                   
	    return result;
    }
	
	private static String getPropertyStatus(IResource resource) {
	    ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
        String result = null;
	       try {
	            LocalResourceStatus status = svnResource.getStatus();
	            if (status.isPropConflicted())
	                result = Policy.bind("CommitDialog.conflicted"); //$NON-NLS-1$	
	            else if ((svnResource.getStatus() != null) &&
	                (svnResource.getStatus().getPropStatus() != null) &&
	                (svnResource.getStatus().getPropStatus().equals(SVNStatusKind.MODIFIED)))
	                result = Policy.bind("CommitDialog.modified"); //$NON-NLS-1$		
                else
                    result = "";
			} catch (TeamException e) {
			    result = "";
			}                   
	    return result;
    }	

    /**
	 * Method createColumns.
	 * @param table
	 * @param layout
	 * @param viewer
	 */
	private void createColumns(Table table, TableLayout layout) {
	    // sortable table
		SelectionListener headerListener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// column selected - need to sort
				int column = listViewer.getTable().indexOf((TableColumn) e.widget);
				CommitSorter oldSorter = (CommitSorter) listViewer.getSorter();
				if (oldSorter != null && column == oldSorter.getColumnNumber()) {
				    oldSorter.setReversed(!oldSorter.isReversed());
				    sorterReversed = oldSorter.isReversed();
				    listViewer.refresh();
				} else {
					listViewer.setSorter(new CommitSorter(column));
					sorterColumn = column;
				}
			}
		};
		
		int[] widths = setter.getColumnWidths("CommitDialog", 4); //$NON-NLS-1$

		TableColumn col;
		// check
		col = new TableColumn(table, SWT.NONE);
    	col.setResizable(false);
		layout.addColumnData(new ColumnPixelData(20, false));
		col.addSelectionListener(headerListener);

		// resource
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(Policy.bind("PendingOperationsView.resource")); //$NON-NLS-1$
		layout.addColumnData(new ColumnPixelData(widths[1], true));
		col.addSelectionListener(headerListener);

		// text status
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(Policy.bind("CommitDialog.status")); //$NON-NLS-1$
		layout.addColumnData(new ColumnPixelData(widths[2], true));
		col.addSelectionListener(headerListener);
		
		// property status
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(Policy.bind("CommitDialog.property")); //$NON-NLS-1$
		layout.addColumnData(new ColumnPixelData(widths[3], true));
		col.addSelectionListener(headerListener);		

	}	
	
	/**
	 * Add the selection and deselection buttons to the dialog.
	 * @param composite org.eclipse.swt.widgets.Composite
	 */
	private void addSelectionButtons(Composite composite) {
	
		Composite buttonComposite = new Composite(composite, SWT.RIGHT);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		buttonComposite.setLayout(layout);
		GridData data =
			new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_HORIZONTAL);
		data.grabExcessHorizontalSpace = true;
		composite.setData(data);
	
		Button selectButton = createButton(buttonComposite, IDialogConstants.SELECT_ALL_ID, Policy.bind("ReleaseCommentDialog.selectAll"), false); //$NON-NLS-1$
		SelectionListener listener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				listViewer.setAllChecked(true);
				selectedResources = null;
			}
		};
		selectButton.addSelectionListener(listener);
	
		Button deselectButton = createButton(buttonComposite, IDialogConstants.DESELECT_ALL_ID, Policy.bind("ReleaseCommentDialog.deselectAll"), false); //$NON-NLS-1$
		listener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				listViewer.setAllChecked(false);
				selectedResources = new Object[0];
			}
		};
		deselectButton.addSelectionListener(listener);

		if (unaddedResources) {
		    Button deselectUnaddedButton = new Button(buttonComposite, SWT.PUSH);
		    deselectUnaddedButton.setText(Policy.bind("CommitDialog.deselectUnadded")); //$NON-NLS-1$
		    deselectUnaddedButton.addSelectionListener(new SelectionAdapter() {
                public void widgetSelected(SelectionEvent e) {
                    TableItem[] items = listViewer.getTable().getItems();
                    for (int i = 0; i < items.length; i++) {
                       IResource resource = (IResource)items[i].getData();
                       ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
                       try {
                        if (!svnResource.isManaged()) items[i].setChecked(false);
	                   } catch (SVNException e1) {}
                    }
                    selectedResources = listViewer.getCheckedElements();
                }
		    });
		}
	}
	
    protected Point getInitialLocation(Point initialSize) {
	    try {
	        int x = settings.getInt("CommitDialog.location.x"); //$NON-NLS-1$
	        int y = settings.getInt("CommitDialog.location.y"); //$NON-NLS-1$
	        return new Point(x, y);
	    } catch (NumberFormatException e) {}
        return super.getInitialLocation(initialSize);
    }
    
    protected Point getInitialSize() {
	    try {
	        int x = settings.getInt("CommitDialog.size.x"); //$NON-NLS-1$
	        int y = settings.getInt("CommitDialog.size.y"); //$NON-NLS-1$
	        return new Point(x, y);
	    } catch (NumberFormatException e) {}
        return super.getInitialSize();
    }	

    /**
	 * Returns the comment.
	 * @return String
	 */
	public String getComment() {
	    if ((projectProperties != null) && (issue != null) && (issue.length() > 0)) {
	        if (projectProperties.isAppend()) 
	            return commitCommentArea.getComment() + "\n" + projectProperties.getResolvedMessage(issue) + "\n";
	        else
	            return projectProperties.getResolvedMessage(issue) + "\n" + commitCommentArea.getComment();
	    }
		return commitCommentArea.getComment();
	}
	
	/**
	 * Returns the selected resources.
	 * @return IResource[]
	 */
	public IResource[] getSelectedResources() {
		if (selectedResources == null) {
			return resourcesToCommit;
		} else {
			List result = Arrays.asList(selectedResources);
			return (IResource[]) result.toArray(new IResource[result.size()]);
		}
	}	
	
	protected static final int LABEL_WIDTH_HINT = 400;
	protected Label createWrappingLabel(Composite parent) {
		Label label = new Label(parent, SWT.LEFT | SWT.WRAP);
		GridData data = new GridData();
		data.horizontalSpan = 1;
		data.horizontalAlignment = GridData.FILL;
		data.horizontalIndent = 0;
		data.grabExcessHorizontalSpace = true;
		data.widthHint = LABEL_WIDTH_HINT;
		label.setLayoutData(data);
		return label;
	}
	
	private void setChecks() {
	    listViewer.setAllChecked(true);
		selectedResources = listViewer.getCheckedElements();
	}
	
	private static class CommitSorter extends ViewerSorter {
		private boolean reversed = false;
		private int columnNumber;
		private static final int NUM_COLUMNS = 4;
		private static final int[][] SORT_ORDERS_BY_COLUMN = {
		    {0, 1, 2, 3}, 	/* check */    
			{1, 0, 2, 3},	/* resource */ 
			{2, 0, 1, 3},	/* status */
			{3, 0, 1, 2},	/* prop status */
		};
		
		public CommitSorter(int columnNumber) {
			this.columnNumber = columnNumber;
		}
		
		public int compare(Viewer viewer, Object e1, Object e2) {
			IResource r1 = (IResource)e1;
			IResource r2 = (IResource)e2;
			int[] columnSortOrder = SORT_ORDERS_BY_COLUMN[columnNumber];
			int result = 0;
			for (int i = 0; i < NUM_COLUMNS; ++i) {
				result = compareColumnValue(columnSortOrder[i], r1, r2);
				if (result != 0)
					break;
			}
			if (reversed)
				result = -result;
			return result;
		}
		
		private int compareColumnValue(int columnNumber, IResource r1, IResource r2) {
			switch (columnNumber) {
				case 0: /* check */
					return 0;
				case 1: /* resource */
					return collator.compare(r1.getFullPath().toString(), r2.getFullPath().toString());					
				case 2: /* status */
					return collator.compare(getStatus(r1), getStatus(r2));
				case 3: /* prop status */
					return collator.compare(getPropertyStatus(r1), getPropertyStatus(r2));					
				default:
					return 0;
			}
		}
	
		public int getColumnNumber() {
			return columnNumber;
		}

		public boolean isReversed() {
			return reversed;
		}

		public void setReversed(boolean newReversed) {
			reversed = newReversed;
		}

	}	
    
}