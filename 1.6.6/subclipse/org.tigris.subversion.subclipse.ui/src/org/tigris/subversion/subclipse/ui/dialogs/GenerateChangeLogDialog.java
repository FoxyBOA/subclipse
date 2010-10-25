package org.tigris.subversion.subclipse.ui.dialogs;

import java.io.File;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.subclipse.ui.SVNUIPlugin;

public class GenerateChangeLogDialog extends TrayDialog {
	private Button svnLogButton;
	private Button svnLogAndPathsButton;
	private Button gnuButton;
	private Button clipboardButton;
	private Button fileButton;
	private Text fileText;
	private Button browseButton;
	private Button okButton;
	private IDialogSettings settings;
	private int lastFormat;
	private int lastOutput;
	private int format;
	private int output;
	private String filePath;
	
	public final static int CLIPBOARD = 0;
	public final static int FILESYSTEM = 1;
	private final static String LAST_OUTPUT = "GenerateChangeLogDialog.lastOutput";
	
	public final static int SVN_LOG = 0;
	public final static int SVN_LOG_WITH_PATHS = 1;
	public final static int GNU = 2;
	private final static String LAST_FORMAT = "GenerateChangeLogDialog.lastFormat";

	public GenerateChangeLogDialog(Shell shell) {
		super(shell);
		settings = SVNUIPlugin.getPlugin().getDialogSettings();
		try {
			lastFormat = settings.getInt(LAST_FORMAT);
		} catch (Exception e) {}		
		try {
			lastOutput = settings.getInt(LAST_OUTPUT);
		} catch (Exception e) {}
	}
	
	protected Control createDialogArea(Composite parent) {
		getShell().setText(Policy.bind("GenerateChangeLogDialog.title")); //$NON-NLS-1$
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		Group formatGroup = new Group(composite, SWT.NULL);
		formatGroup.setText(Policy.bind("GenerateChangeLogDialog.outputFormat")); //$NON-NLS-1$
		GridLayout formatLayout = new GridLayout();
		formatLayout.numColumns = 1;
		formatGroup.setLayout(formatLayout);
		GridData data = new GridData(GridData.FILL_BOTH);
		formatGroup.setLayoutData(data);
		
		svnLogButton = new Button(formatGroup, SWT.RADIO);
		svnLogButton.setText(Policy.bind("GenerateChangeLogDialog.svnLog")); //$NON-NLS-1$
		svnLogAndPathsButton = new Button(formatGroup, SWT.RADIO);
		svnLogAndPathsButton.setText(Policy.bind("GenerateChangeLogDialog.svnLogAndPaths")); //$NON-NLS-1$
		gnuButton = new Button(formatGroup, SWT.RADIO);
		gnuButton.setText(Policy.bind("GenerateChangeLogDialog.gnu")); //$NON-NLS-1$	
		
		Group outputGroup = new Group(composite, SWT.NULL);
		outputGroup.setText(Policy.bind("GenerateChangeLogDialog.saveTo")); //$NON-NLS-1$
		GridLayout outputLayout = new GridLayout();
		outputLayout.numColumns = 2;
		outputGroup.setLayout(outputLayout);
		data = new GridData(GridData.FILL_BOTH);
		outputGroup.setLayoutData(data);	
		
		clipboardButton = new Button(outputGroup, SWT.RADIO);
		clipboardButton.setText(Policy.bind("GenerateChangeLogDialog.clipboard")); //$NON-NLS-1$
		data = new GridData();
		data.horizontalSpan = 2;
		clipboardButton.setLayoutData(data);
		
		fileButton = new Button(outputGroup, SWT.RADIO);
		fileButton.setText(Policy.bind("GenerateChangeLogDialog.fileSystem")); //$NON-NLS-1$
		data = new GridData();
		data.horizontalSpan = 2;
		fileButton.setLayoutData(data);
		
		fileText = new Text(outputGroup, SWT.BORDER);
		data = new GridData();
		data.widthHint = 450;
		fileText.setLayoutData(data);
		
		fileText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				okButton.setEnabled(canFinish());
			}	
		});
		
		browseButton = new Button(outputGroup, SWT.NULL);
		browseButton.setText(Policy.bind("GenerateSVNDiff.Browse")); //$NON-NLS-1$
		
		browseButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				FileDialog d = new FileDialog(getShell(), SWT.PRIMARY_MODAL | SWT.SAVE);
				d.setText(Policy.bind("GenerateChangeLogDialog.saveAs")); //$NON-NLS-1$
				d.setFileName(Policy.bind("GenerateChangeLogDialog.changeLogTxt")); //$NON-NLS-1$
				String file = d.open();
				if(file!=null) {
					IPath path = new Path(file);
					fileText.setText(path.toOSString());
				}							
			}
		});
		
		if (lastOutput == FILESYSTEM) {
			fileText.setFocus();
		} else {
			fileText.setEnabled(false);
			browseButton.setEnabled(false);
		}
		
		SelectionListener selectionListener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (e.getSource() == clipboardButton || e.getSource() == fileButton) {
					if (fileButton.getSelection()) settings.put(LAST_OUTPUT, FILESYSTEM);
					else settings.put(LAST_OUTPUT, CLIPBOARD);
					fileText.setEnabled(fileButton.getSelection());
					if (e.getSource() == fileButton && fileButton.getSelection()) fileText.setFocus();
					browseButton.setEnabled(fileButton.getSelection());
					okButton.setEnabled(canFinish());
				} else {
					if (svnLogButton.getSelection()) settings.put(LAST_FORMAT, SVN_LOG);
					else if (svnLogAndPathsButton.getSelection()) settings.put(LAST_FORMAT, SVN_LOG_WITH_PATHS);
					else settings.put(LAST_FORMAT, GNU);
				}
			}		
		};
		
		svnLogButton.addSelectionListener(selectionListener);
		svnLogAndPathsButton.addSelectionListener(selectionListener);
		gnuButton.addSelectionListener(selectionListener);
		clipboardButton.addSelectionListener(selectionListener);
		gnuButton.addSelectionListener(selectionListener);
		
		switch (lastFormat) {
		case SVN_LOG:
			svnLogButton.setSelection(true);
			break;
		case SVN_LOG_WITH_PATHS:
			svnLogAndPathsButton.setSelection(true);
			break;
		case GNU:
			gnuButton.setSelection(true);
			break;			
		default:
			svnLogButton.setSelection(true);
			break;
		}
		
		switch (lastOutput) {
		case CLIPBOARD:
			clipboardButton.setSelection(true);
			break;
		case FILESYSTEM:
			fileButton.setSelection(true);
			break;
		default:
			clipboardButton.setSelection(true);
			break;
		}
		
		FocusListener focusListener = new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				((Text)e.getSource()).selectAll();
			}
			public void focusLost(FocusEvent e) {
				((Text)e.getSource()).setText(((Text)e.getSource()).getText());
			}					
		};
		fileText.addFocusListener(focusListener);
		
		return composite;
	}
	
	protected void okPressed() {
		if (svnLogButton.getSelection()) format = SVN_LOG;
		else if (svnLogAndPathsButton.getSelection()) format = SVN_LOG_WITH_PATHS;
		else if (gnuButton.getSelection()) format = GNU;
		if (clipboardButton.getSelection()) output = CLIPBOARD;
		else if (fileButton.getSelection()) {
			output = FILESYSTEM;
			filePath = fileText.getText().trim();
			File file = new File(filePath);
			if (file.exists()) {
				String title = Policy.bind("GenerateSVNDiff.overwriteTitle"); //$NON-NLS-1$
				String msg = Policy.bind("GenerateSVNDiff.overwriteMsg"); //$NON-NLS-1$
				final MessageDialog messageDialog = new MessageDialog(Display.getDefault().getActiveShell(), title, null, msg, MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
				if (!(messageDialog.open() == MessageDialog.OK)) return;			
			}		
		}
		super.okPressed();
	}

	protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
        Button button = super.createButton(parent, id, label, defaultButton);
		if (id == IDialogConstants.OK_ID) {
			okButton = button; 
			if (lastOutput == FILESYSTEM) okButton.setEnabled(false);
		}
        return button;
    }
	
	private boolean canFinish() {
		if (fileButton.getSelection()) {
			if (fileText.getText().trim().length() == 0) return false;
			File file = new File(fileText.getText().trim());
			return isValidFile(file);
		}		
		return true;
	}
	
	private boolean isValidFile(File file) {
		if (!file.isAbsolute()) return false;
		if (file.isDirectory()) return false;
		File parent = file.getParentFile();
		if (parent==null) return false;
		if (!parent.exists()) return false;
		if (!parent.isDirectory()) return false;
		return true;
	}

	public int getFormat() {
		return format;
	}

	public int getOutput() {
		return output;
	}	
	
	public File getFile() {
		return new File(filePath);
	}

}