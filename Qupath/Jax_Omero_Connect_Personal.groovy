/* To do:
 *  error handling with dialog box
 *  mass file download
 */

import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.log.SimpleLogger
import omero.gateway.facility.BrowseFacility
import ij.IJ
import ij.gui.GenericDialog
import javax.swing.*

#@ String(label="Username") USERNAME
#@ String(label="Password", style='password') PASSWORD
#@ String(label="Host", value='ctomero01lp.jax.org') HOST
#@ Integer(label="Dataset ID", value=2970) DATASETID
#@ String(label="File Name") FILENAME

finished = false;
options = [true, false]
	
while (!finished)
{
	gateway = connect_to_omero()
	exp = gateway.getLoggedInUser()
	group_id = exp.getGroupId()
	ctx = new SecurityContext(group_id)

	image_set = get_images(gateway, ctx, DATASETID)

	JTextArea text_area = new JTextArea("Are these the correct images to analze? ("+count+")\n"+image_names);

	finished = options[JOptionPane.showConfirmDialog(null, text_area, "Confirm Images", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)]

	if (!finished)
	{
		JPanel dsidafnp = new JPanel()
		JTextField dataset_id = new JTextField(String.valueOf(DATASETID), 50);
    	JTextField file_name = new JTextField(FILENAME, 50);
		dsidafnp.add(new JLabel("Dataset ID:"));
    	dsidafnp.add(dataset_id);
    	dsidafnp.add(Box.createHorizontalStrut(15)); // a spacer
    	dsidafnp.add(new JLabel("File Name:"));
    	dsidafnp.add(file_name);

    	int result = JOptionPane.showConfirmDialog(null, dsidafnp, "Please Enter Dataset ID and File Name Values", JOptionPane.OK_CANCEL_OPTION);
    	if (result == JOptionPane.OK_OPTION)
    	{
        	DATASETID = dataset_id.getText()
        	FILENAME = file_name.getText()
        }
        else
        {
        	image_set = [];
        	finished = true
        	continue
        }
	}
}
image_set.each() { im ->
	open_image_plus(HOST, USERNAME, PASSWORD, group_id, String.valueOf(im))
}

gateway.disconnect();

def connect_to_omero() {
    "Connect to OMERO"

    credentials = new LoginCredentials()
    credentials.getServer().setHostname(HOST)
    credentials.getUser().setUsername(USERNAME.trim())
    credentials.getUser().setPassword(PASSWORD.trim())
    simpleLogger = new SimpleLogger()
    gateway = new Gateway(simpleLogger)
    gateway.connect(credentials)
    return gateway

}

def get_images(gateway, ctx, dataset_id) {
	
	browse = gateway.getFacility(BrowseFacility)

    ids = new ArrayList(1)
	ids.add(new Long(dataset_id))
    
    images = browse.getImagesForDatasets(ctx, ids)
    count = 0
	image_ids = new ArrayList()
	image_names = ""
	images.each() { im ->
		name = im.getName().replaceAll("\\s.*", "")
		if (im.getName().contains(FILENAME))
		{
			count++
			image_ids.add(im.getId())
			image_names += im.getName()+"\n"
		}		
	}
	println (count+" Images Found")
	return image_ids.sort()
}

def open_image_plus(HOST, USERNAME, PASSWORD, group_id, image_id) {
    "Open the image using the Bio-Formats Importer"

    StringBuilder options = new StringBuilder()
    options.append("location=[OMERO] open=[omero:server=")
    options.append(HOST)
    options.append("\nuser=")
    options.append(USERNAME.trim())
    options.append("\nport=")
    options.append(4064)
    options.append("\npass=")
    options.append(PASSWORD.trim())
    options.append("\ngroupID=")
    options.append(group_id)
    options.append("\niid=")
    options.append(image_id)
    options.append("] ")
    options.append("autoscale windowless=true view=Hyperstack")
    IJ.runPlugIn("loci.plugins.LociImporter", options.toString())

}

//Not used since we only have one Omero server at the moment, but keeping it just in case
//def get_port(HOST) {
//    port = 4064
//    // check if websockets is used
//    if (HOST.startsWith("ws")) {
//        port = 443
//    }
//    return port
//}