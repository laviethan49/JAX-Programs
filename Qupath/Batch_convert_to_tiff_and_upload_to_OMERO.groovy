#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@File(label="Folder for saving",style="directory") folder

/* = CODE DESCRIPTION =
 * This code import images on OMERO
 * Images are open in Fiji and saved as tiff in a temporary location.
 * Then, they are imported on OMERO in the user specified project / dataset
 * 
 * == INPUTS ==
 *  - credentials 
 *  - Folder containing WesternBlot images 
 * 
 * == OUTPUTS ==
 *  - Imporation of images on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 28.03.2023
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Connection to server
Client user_client = new Client()
host = "omero-server.epfl.ch"
port = 4064

user_client.connect(host, port, USERNAME, PASSWORD.toCharArray());

if (user_client.isConnected()){
	println "Connection to "+host+" : Success"
	println("Images read from :  "+folder.getAbsolutePath());
	
	try{
		// get the userID
		def userId = user_client.getId()
		
		// get user's projects
		def projectList = user_client.getBrowseFacility().getProjects(user_client.getCtx(), userId)
		
		// get project's name
		def projectNames = (String[])projectList.stream().map(ProjectData::getName).toArray()
		
		// generate the dialog box
		def dialog = new Dialog(user_client, projectNames, projectList, userId)

		while(!dialog.getEnterPressed()){
       		// Wait an answer from the user (Ok or Cancel)
    	}
    	
    	// If Ok
    	if(dialog.getValidated()){
    		// get user's answer
    		def project = dialog.getSelectedProject()
    		def dataset = dialog.getSelectedDataset()
    		
    		println("Images will be imported in project '"+project+ "' ; dataset '"+dataset+"'.");
    		
    		// get project in OMERO
    		def projectDataId = projectList.find{it.getName() == project}.getId()
    		def projectWrapper = user_client.getProject(projectDataId)
    		
    		// get dataset in OMERO
    		def datasetWrapperList = projectWrapper.getDatasets()
    		def datasetWrapper = datasetWrapperList.find{it.getName() == dataset}
    		
    		String home = Prefs.getHomeDir();
    		
    		println("*****************");
    		folder.listFiles().each{
    			println("Process image "+it.getName());
    			
    			// open the image in ImageJ
    			def imp = IJ.openImage(it.getAbsolutePath())
    			
    			 // save the image
    			FileSaver fs = new FileSaver(imp);
    			def idx = imp.getTitle().lastIndexOf(".")
		        File correctImageTiff = new File(home, imp.getTitle().substring(0, idx) + ".tif");
		        boolean hasBeenSaved = fs.saveAsTiff(correctImageTiff.toString());
		        
		        if(hasBeenSaved){
		        	println("Temporary file saved ");
			     	// import the image on OMERO
	    			try{
			        	datasetWrapper.importImage(user_client, correctImageTiff.getAbsolutePath())
			        	println("OMERO importation : Done ! ");
	    			}finally{
	    				// delete the file after upload
	        			boolean hasBeenDeleted = correctImageTiff.delete();
	        			if(hasBeenDeleted)
	        				println("Temporary file deleted ");
	        			else
	        				println("Cannot delete temporary file");
	    			}
		        } else println("Cannot save temporary file ; image " + it.getName() + " is not imported on OMERO");
		        println("*****************");
    		}
    		
    	} else println("Process has been canceled by the user ; no image will be imported on OMERO");
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * 
 * Create the Dialog asking for the project and dataset
 * 
 * */
public class Dialog extends JFrame {
	
	private JComboBox<String> cmbProject;
    private JComboBox<String> cmbDataset;
    private JButton bnOk = new JButton("Ok");
    private JButton bnCancel = new JButton("Cancel");
    private DefaultComboBoxModel<String> modelCmbProject;
    private DefaultComboBoxModel<String> modelCmbDataset;
    	
	Client client;
	def userId;
	def project_list;
	boolean enterPressed;
	boolean validated;
	String selected_project;
	String selected_dataset;
	
	public Dialog(user_client, project_names, project_list, userId){
		client = user_client
		this.userId = userId
		this.project_list = project_list
		
		myDialog(project_names)
	}
	
	// getters
	public boolean getEnterPressed(){return this.enterPressed}
	public boolean getValidated(){return this.validated}
	public String getSelectedProject(){return this.selected_project}
	public String getSelectedDataset(){return this.selected_dataset}
	
	// generate the dialog box
	public void myDialog(project_names) {
		// set general frame
		this.setTitle("Select your import location on OMERO")
	    this.setVisible(true);
	    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	    this.setPreferredSize(new Dimension(400, 200));
	    
	    // get the screen size
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double width = screenSize.getWidth();
        double height = screenSize.getHeight();
        
        // set location in the middle of the screen
	    this.setLocation((int)((width - 400)/2), (int)((height - 200)/2));
		
		// build project combo model
		modelCmbProject = new DefaultComboBoxModel<>(project_names);
        cmbProject = new JComboBox<>(modelCmbProject);
        
        // build dataset combo model
		modelCmbDataset = new DefaultComboBoxModel<>(new String[]{});
        cmbDataset = new JComboBox<>(modelCmbDataset);

		JSeparator separator1 = new JSeparator();
		JSeparator separator2 = new JSeparator();
		
        // build Combo project
        JPanel boxComboProject = new JPanel();
        JLabel projectLabel = new JLabel("Project");
        boxComboProject.add(projectLabel);
        boxComboProject.add(cmbProject);
        boxComboProject.setLayout(new FlowLayout());
        
        // build Combo dataset
        JPanel boxComboDataset = new JPanel();
        JLabel datasetLabel = new JLabel("Dataset");
        boxComboDataset.add(datasetLabel);
        boxComboDataset.add(cmbDataset);
        boxComboDataset.setLayout(new FlowLayout());
        
        // build buttons
        JPanel boxButton = new JPanel();
        boxButton.add(bnOk);
        boxButton.add(bnCancel);
        boxButton.setLayout(new FlowLayout());
        
        // general panel
        JPanel windowNLGeneral = new JPanel();
        windowNLGeneral.setLayout(new BoxLayout(windowNLGeneral, BoxLayout.Y_AXIS));
        windowNLGeneral.add(boxComboProject);
        windowNLGeneral.add(separator1);
        windowNLGeneral.add(boxComboDataset);
        windowNLGeneral.add(separator2);
        windowNLGeneral.add(boxButton);
        
        // add listener on project combo box
        cmbProject.addItemListener(
			new ItemListener(){
				    @Override
			    public void itemStateChanged(ItemEvent e) {
					// get the datasets corresponding to the selected project
			        def chosen_project = (String) cmbProject.getSelectedItem()
					def project = project_list.find{it.getName() == chosen_project}
					def dataset_list = project.getDatasets()
					def dataset_names = (String[])dataset_list.stream().map(DatasetData::getName).toArray()

					// update the dataset combo box
					modelCmbDataset.removeAllElements();
        			for (String dataset : dataset_names) modelCmbDataset.addElement(dataset);
        			cmbDataset.setSelectedIndex(0);
			       
			    }
			}
		);
		
		// add listener on Ok and Cancel button
		bnOk.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				enterPressed = true
    				validated = true;
    				this.selected_project = (String) cmbProject.getSelectedItem()
    				this.selected_dataset = (String) cmbDataset.getSelectedItem()
    				this.dispose()
    			}
			}
		)
		
		bnCancel.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				enterPressed = true
    				validated = false;
    				this.dispose()
    			}
			}
		)
		
		 // set main interface parameters
		this.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {

            }

            @Override
            public void windowClosed(WindowEvent e) {
				enterPressed = true
    			validated = false;
            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        });

        this.setContentPane(windowNLGeneral);
        this.pack();
    }
} 


// imports
import fr.igred.omero.*
import omero.gateway.model.ProjectData
import omero.gateway.model.DatasetData
import javax.swing.*;
import java.awt.FlowLayout;
import javax.swing.BoxLayout
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import java.awt.AWTEvent;
import java.util.stream.Collectors
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import ij.Prefs;
import ij.io.FileSaver;
import ij.IJ;
import java.awt.Dimension;
import java.awt.Toolkit;
