package org.tigris.subversion.subclipse.ui.dialogs;

import java.net.MalformedURLException;
import java.text.ParseException;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.help.WorkbenchHelp;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.ui.IHelpContextIds;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.subclipse.ui.util.UrlCombo;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;

public class SwitchDialog extends Dialog {
    
    private static final int REVISION_WIDTH_HINT = 40;
 
    private IResource resource;
    
    private UrlCombo urlCombo;
    private Text revisionText;
    private Button headButton;
    private Button revisionButton;
    
    private Button okButton;
    
    private SVNUrl url;
    private SVNRevision revision;

    public SwitchDialog(Shell parentShell, IResource resource) {
        super(parentShell);
        this.resource = resource;
    }
    
	/*
	 * @see Dialog#createDialogArea(Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		getShell().setText(Policy.bind("SwitchDialog.title")); //$NON-NLS-1$
		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(data);
		
		Label urlLabel = new Label(composite, SWT.NONE);
		urlLabel.setText(Policy.bind("SwitchDialog.url")); //$NON-NLS-1$
		
		urlCombo = new UrlCombo(composite, resource.getProject().getName());
		ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
		try {
            String anUrl = svnResource.getStatus().getUrlString();
            if (anUrl != null) urlCombo.setText(anUrl);
        } catch (SVNException e1) {}
        urlCombo.getCombo().addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                setOkButtonStatus();
            }         
        });
		
		Button browseButton = new Button(composite, SWT.PUSH);
		browseButton.setText(Policy.bind("SwitchDialog.browse")); //$NON-NLS-1$
		browseButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                ChooseUrlDialog dialog = new ChooseUrlDialog(getShell(), resource);
                if ((dialog.open() == ChooseUrlDialog.OK) && (dialog.getUrl() != null)) {
                    urlCombo.setText(dialog.getUrl());
                    setOkButtonStatus();
                }
            }
		});

		Group revisionGroup = new Group(composite, SWT.NULL);
		revisionGroup.setText(Policy.bind("SwitchDialog.revision")); //$NON-NLS-1$
		GridLayout revisionLayout = new GridLayout();
		revisionLayout.numColumns = 2;
		revisionGroup.setLayout(revisionLayout);
		data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 3;
		revisionGroup.setLayoutData(data);
		
		headButton = new Button(revisionGroup, SWT.RADIO);
		headButton.setText(Policy.bind("SwitchDialog.head")); //$NON-NLS-1$
		data = new GridData();
		data.horizontalSpan = 2;
		headButton.setLayoutData(data);
		
		revisionButton = new Button(revisionGroup, SWT.RADIO);
		revisionButton.setText(Policy.bind("SwitchDialog.revision")); //$NON-NLS-1$
		
		headButton.setSelection(true);
		
		revisionText = new Text(revisionGroup, SWT.BORDER);
		data = new GridData();
		data.widthHint = REVISION_WIDTH_HINT;
		revisionText.setLayoutData(data);
		revisionText.setEnabled(false);
		
		revisionText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                setOkButtonStatus();
            }		    
		});
		
		SelectionListener listener = new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                revisionText.setEnabled(revisionButton.getSelection());
                setOkButtonStatus();
                if (revisionButton.getSelection()) {
                    revisionText.selectAll();
                    revisionText.setFocus();
                }
            }
		};
		
		headButton.addSelectionListener(listener);
		revisionButton.addSelectionListener(listener);

		// Add F1 help
		WorkbenchHelp.setHelp(composite, IHelpContextIds.SWITCH_DIALOG);
		
		return composite;
	}
	
    protected void okPressed() {
        urlCombo.saveUrl();
        try {
            url = new SVNUrl(urlCombo.getText());
            if (headButton.getSelection()) revision = SVNRevision.HEAD;
            else {
                try {
                    revision = SVNRevision.getRevision(revisionText.getText().trim());
                } catch (ParseException e1) {
                  MessageDialog.openError(getShell(), Policy.bind("SwitchDialog.title"), Policy.bind("SwitchDialog.invalid")); //$NON-NLS-1$ //$NON-NLS-2$
                  return;   
                }
            }
        } catch (MalformedURLException e) {
            MessageDialog.openError(getShell(), Policy.bind("SwitchDialog.title"), e.getMessage()); //$NON-NLS-1$
            return;
        }
        super.okPressed();
    }

    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
        Button button = super.createButton(parent, id, label, defaultButton);
		if (id == IDialogConstants.OK_ID) okButton = button;    
        return button;
    }
    
    private void setOkButtonStatus() {
        okButton.setEnabled((urlCombo.getText().length() > 0) && (headButton.getSelection() || (revisionText.getText().trim().length() > 0)));
    }
    
    public SVNRevision getRevision() {
        return revision;
    }
    public SVNUrl getUrl() {
        return url;
    }

}
