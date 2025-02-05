/*
* Copyright 2020, The Jackson Laboratory, Bar Harbor, Maine
* J. Peterson
* 
* v1.7 and on by E. Saville
*
* To do:
*     - Add comment blocks to functions - Done for now, but always a to do.
*	  - Error Handling - Tissue mask not being found
*/

/* 
 *  Initializing variables to be used later, most are self explanatory
 *  Variables starting with b are "buttons", and s are "samples", v are Array variables
 */
var Version = "version 1.07";
var OutputDirectory = "C:/Users/Public/Documents/";

var bFinished = false; //Asking to finish the script when processing images sequentially
var bAutoPut = false; //Asking in first dialog to set the output directory automatically or not, good if you need to step away from the computer

var InputImageNoChoice = "< re-populate image list >";
var InputImage = InputImageNoChoice;
var ChannelNames = newArray("RFP", "GFP", "DAPI");
var SampleNumber = 1;

var vEar = newArray("<select ear notch>", "N", "R", "L", "2R", "2L", "3R", "3L");
var vSex = newArray("<select sex>", "M", "F");
var vPortion = newArray("<select portion>", "beginning", "mid", "end");
var vTissue = newArray("<select tissue>","brain","lymph","eye","diaphragm","gastrocnemius","heart","thymus","skin","lung","bladder","testes","epididymis","spleen","liver","kidney","adrenal","pancreas","small","stomach","large","ovary","uterus");

var sAnimal = "";
var sEar = vEar[0];
var sSex = vSex[0];
var sPortion = vPortion[0];
var sTissue = "N/A";

main();
exit();

function main()
{
    requires("1.53f"); //Version of FIJI/ ImageJ

	//Creates stats table if not open already
    if (!isOpen("Stats"))
    {
        create_empty_table();
    }

	//Creates the dialog with options for how to run the program
    Dialog.createNonBlocking("SCGE.ijm - "+Version);
    mode_list = newArray("Interactively Process Open Images", "Consecutively Process Open Images", "Process .Lif File Consecutively", "Process Images From Omero Interactively", "Process Images From Omero Consecutively");
    Dialog.addRadioButtonGroup("How Would You Like to Process The Images?", mode_list, 5, 1, mode_list[4]);
    Dialog.addCheckbox("Would you like to automatically create the output directory? It will be saved in C:/Users/Public/Documents/<file name here>. Only for Consecutive Processing", false);
    Dialog.show();
    bInteractive = Dialog.getRadioButton();
    bAutoPut = Dialog.getCheckbox();

    //Turns off opening the images when etting from Omero and when processing (background mode)
    setBatchMode(true);

	//Switch scenario for the processing routes to take based on selection of above
    if (bInteractive.contains("Interactively Process Open Images"))
    {
        process_interactively();
    }
    else if (bInteractive.contains("Process .Lif File Consecutively"))
    {
        process_lif_file();
    }
    else if (bInteractive.contains("Process Images From Omero Interactively"))
    {
    	process_interactively_omero();
    }
    else if(bInteractive.contains("Consecutively Process Open Images"))
    {
    	process_consecutively();
    }
    else
    {
    	process_file_omero();
    }
}


//Create the empty Measurements table.
function create_empty_table()
{
    Table.create("Stats");

    Table.set("Sample", 0, 0);
    Table.set("Animal", 0, " ");
    Table.set("Ear", 0, " ");
    Table.set("Sex", 0, " ");
    Table.set("Portion", 0, 0);
    Table.set("DAPI_count", 0, 0);
    Table.set("Tissue", 0, 0);
    Table.set("tissue_area", 0, 0);
    Table.set("units", 0, " ");
    Table.set("RFP_area", 0, 0);
    Table.set("GFP_area", 0, 0);
    Table.set("DAPI_area", 0, 0);

    Table.setLocationAndSize(20, 20, 900, 200) ;
    Table.update();
}

//This mode processes images that are already open. The user selects the image of interest from a list of open images.
function process_interactively()
{
    while (bFinished == false)
    {
        emptyList = newArray(InputImageNoChoice);
        imageList = getList("image.titles"); //Titles of all open images go into new arry to iterate over
        imageList = Array.concat(emptyList, imageList); //Adding the two arrays together to add the "re-populate" option to the start
        if (InputImage == InputImageNoChoice)
        {
            n = 0;
            if (lengthOf(imageList) > 1)
            {
                n =  1;
            }
            InputImage = imageList[n];
        }

        Dialog.createNonBlocking("SCGE.ijm - "+Version);

        Dialog.addChoice("Image", imageList, InputImage);
        Dialog.addDirectory("Output Directory", OutputDirectory+mouse_from_name(InputImage));
        Dialog.addCheckbox("Finished", false);
        
        Dialog.show();

        InputImage = Dialog.getChoice();
        if (InputImage == InputImageNoChoice)
        {
            continue;
        }
        OutputDirectory = check_dir(Dialog.getString());
        File.makeDirectory(OutputDirectory);
        bFinished = Dialog.getCheckbox();

		if (bFinished)
		{
			continue;
		}
		
        process_image(InputImage);
    }
    clean_up(InputImage);
}

// This mode processes images from Omero sequentially. The user selects the image of interest from a list of open images.
function process_interactively_omero()
{
	run("Jax Omero Connect Personal");
	setBatchMode("exit & display");
	setBatchMode(true);

    while (bFinished == false)
    {
        emptyList = newArray(InputImageNoChoice);
        imageList = getList("image.titles");
        imageList = Array.concat(emptyList, imageList);
        if (InputImage == InputImageNoChoice)
        {
            n = 0;
            if (lengthOf(imageList) > 1)
            {
                n =  1;
            }
            InputImage = imageList[n];
        }

        Dialog.createNonBlocking("SCGE.ijm - "+Version);

        Dialog.addChoice("Image", imageList, InputImage);
        Dialog.addDirectory("Output Directory", OutputDirectory+mouse_from_name(InputImage));
        Dialog.addCheckbox("Finished", false);
        
        Dialog.show();

        InputImage = Dialog.getChoice();
        if (InputImage == InputImageNoChoice)
        {
            continue;
        }
        OutputDirectory = check_dir(Dialog.getString());
        File.makeDirectory(OutputDirectory);
        bFinished = Dialog.getCheckbox();

		if (bFinished)
		{
			continue;
		}
		
        process_image(InputImage);
    }
    
    clean_up(InputImage);
}

// This mode processes all series in an lif or similar file. The BioFormats plugin is used to open the file.
function process_lif_file()
{
    close("*");
    Dialog.createNonBlocking("SCGE.ijm - "+Version);
    Dialog.addFile("input file", "");
    Dialog.addDirectory("Output Directory", OutputDirectory);

    Dialog.show();

    input_file = Dialog.getString();
    OutputDirectory = check_dir(Dialog.getString());
    File.makeDirectory(OutputDirectory);
    
    import_cmd = "Bio-Formats Importer";
    for (i=0; i<50; i++) //Iterates over the images in a lif file, upt to 50 of them
    {
        series = "series_" + toString(i+1);
        import_params = "open='" + input_file + "' " + series;
        run(import_cmd, import_params);
        InputImage = getTitle();
        if (i == 0)
        {
            first_image = InputImage;
        }
        if ((i > 0) && (InputImage == first_image)) //Checks if the loop has completed
        {
            close();
            break;
        }
        process_image(InputImage);
    }
    clean_up(InputImage);
}

// This mode processes all series in the file uploaded to Omero. The BioFormats plugin is used to open the file
function process_file_omero()
{
	run("Jax Omero Connect Personal");
	setBatchMode("exit & display");
	setBatchMode(true);

    imageList = getList("image.titles");
    imageCount = imageList.length;

	if(!bAutoPut)
	{
		Dialog.createNonBlocking("SCGE.ijm - "+Version);
   		Dialog.addDirectory("Output Directory", OutputDirectory+mouse_from_name(imageList[0]));

    	Dialog.show();

    	OutputDirectory = check_dir(Dialog.getString());
    	File.makeDirectory(OutputDirectory);
	}
	else
	{
		OutputDirectory = check_dir(OutputDirectory+mouse_from_name(imageList[0]));
		File.makeDirectory(OutputDirectory);
	}

    for (i=0; i<imageCount; i++)
    {
        series = "series_" + toString(i+1);
        InputImage = imageList[i];
        if (i == 0)
        {
            first_image = InputImage;
        }
        if ((i > 0) && (InputImage == first_image))
        {
            close();
            break;
        }
        process_image(InputImage);
    }
	clean_up(InputImage);
}

// Iterates over the currently open images to process them.
function process_consecutively()
{	
    imageList = getList("image.titles");
    imageCount = imageList.length;
    
    if(!bAutoPut)
	{
		Dialog.createNonBlocking("SCGE.ijm - "+Version);
   		Dialog.addDirectory("Output Directory", OutputDirectory+mouse_from_name(imageList[0]));

    	Dialog.show();

    	OutputDirectory = check_dir(Dialog.getString());
    	File.makeDirectory(OutputDirectory);
	}
	else
	{
		OutputDirectory = check_dir(OutputDirectory+mouse_from_name(imageList[0]));
		File.makeDirectory(OutputDirectory);
	}
	
    for (i=0; i<imageCount; i++)
    {
        series = "series_" + toString(i+1);
        InputImage = imageList[i];
        if (i == 0)
        {
            first_image = InputImage;
        }
        if ((i > 0) && (InputImage == first_image))
        {
            close();
            break;
        }
        process_image(InputImage);
    }
	clean_up(InputImage);
}

// Function to process the images with various methods
function process_image(InputImage)
{
	//Returning all values here by parsing the file name, with each variable relating to what it is
    sAnimal = mouse_from_name(InputImage);
    sEar = ear_from_name(InputImage);
    sSex = sex_from_name(InputImage);
    sPortion = section_from_name(InputImage);
    sTissue = tissue_type_from_name(InputImage);

    getPixelSize(units, pixelWidth, pixelHeight); //Relating pixel size to real world size
    selectWindow("Stats");

	//Preparing and filling the table with headers
    row = Table.size();
    if (Table.get("Sample", row-1) == 0)
    {
        row -= 1;
    }
    Table.set("Sample", row, row+1);
    Table.set("Animal", row, sAnimal);
    Table.set("Ear", row, sEar);
    Table.set("Sex", row, sSex);
    Table.set("Portion", row, sPortion);
    Table.set("Tissue", row, sTissue);
    Table.set("units", row, units);
    
    // Split the channels and pre-process. Making sure there are none open already.
    for (i=0; i<3; i++)
    {
        close(ChannelNames[i]);
    }
    split_channels(InputImage, tissue_type_from_name(InputImage));
    
    // Compute areas. Making sure no duplicate images are open already. Makes mask for images and saves them to the output directory.
    for (i=0; i<3; i++)
    {
        close(ChannelNames[i]+"_mask");
    }

    vArea = newArray(3);
    for (i=0; i<3; i++)
    {
    	win = ChannelNames[i];
    	save_validation_file(row, sTissue, win);
        a = make_channel_mask(ChannelNames[i]);
        a *= (pixelWidth * pixelHeight);
        vArea[i] = round(a);

        win = ChannelNames[i] + "_mask";
        save_validation_file(row, sTissue, win);
    }
    
    a = make_combined_mask();
    a *= (pixelWidth * pixelHeight);
    total_area = round(a);
    save_validation_file(row, sTissue, "tissue_mask");
    
    Table.set("tissue_area", row, total_area);
    Table.set("RFP_area", row, vArea[0]);
    Table.set("GFP_area", row, vArea[1]);
    Table.set("DAPI_area", row, vArea[2]);

    // Uses stardist and watershed to count the DAPI blobs, and saves the files generated
    for (i=0; i<3; i++)
    {
    	close(ChannelNames[i]+"_validate");
        if (i == 2)  // DAPI
        {
        	win = ChannelNames[i];// + "_mask";
            count = get_dapi_count(win, sTissue);
            table_label = ChannelNames[i] + "_count";
            Table.set(table_label, row, count);

            save_validation_file(row, sTissue, win);
            close(win);
        }
		close(ChannelNames[i]+"_mask");
        close(ChannelNames[i]);
    }

    //Updates the table and closes the last image generated
    Table.update();
    close("tissue_mask");
}

// Input a sample number, tissue type, and the window and this saves it to the output directory
function save_validation_file(sample_num, sTissue, win)
{
    if (OutputDirectory != File.separator())
    {
        sample_str = add_leading_zeros(sample_num+1, 4);
        dir_name = OutputDirectory + "sample" + sample_str + "_" + sTissue;
        dir_name = add_separator(dir_name);
        File.makeDirectory(dir_name);
        selectWindow(win);
        file = dir_name + win + ".tif";
        saveAs("Tiff", file);
        rename(win);
    }
}

// Checks if folder exists and if it does, makes a new folder with a number appended that is one above what the current directory has (nothing -> 1, 1 -> 2...)
function check_dir(dir)
{
	increment = 0;
	folder = dir;
	while (File.exists(folder))
	{
		increment++;
		folder = dir + "_" + increment;
	}
	return folder + File.separator();
}

// Adds a directory separator at the end of a directory name, if needed.
function add_separator(dir)
{
    tmp = dir;
    if (!endsWith(tmp, File.separator()))
    {
        tmp = tmp + File.separator();
    }
    return tmp;
}

// Adds leading zeros (0) to a numeric string, making a string of a specified length.
function add_leading_zeros(num, length)
{
    num_string = toString(num);
    l = lengthOf(num_string);
    z = "";
    for (i=0; i<length-l; i++)
    {
        z = z + "0";
    }
    s = z + num_string;
    return s;
}

//Splits the channels of an RGB picture into the grayscale formate of each in new image. Also performs some pre-processing.
function split_channels(win, tissue)
{
    setOption("ScaleConversions", false);
    selectWindow(win);
    run("Duplicate...", "duplicate title=tmp");
    run("Split Channels");
    for (i=1; i<=3; i++)
    {
    	name = "C" + toString(i) + "-tmp";
       	selectWindow(name);

       	//This pre-processing removes random noise speckles, and enhances local area contrasts - depending on tissue type as well
       	run("Despeckle");
       	sFast = "fast_(less_accurate)";
    	if(tissue.contains("lymph") || tissue.contains("thymus"))
    	{
    		run("Multiply...", "value=16");
    		run("Enhance Local Contrast (CLAHE)", "blocksize=75 histogram=256 maximum=10 mask=*None* "+sFast);
	    }
	    else if (tissue.contains("eye"))
	    {
			run("Multiply...", "value=2");
			run("Enhance Local Contrast (CLAHE)", "blocksize=25 histogram=256 maximum=1 mask=*None* "+sFast);
    	}
    	else
    	{
    		run("Multiply...", "value=8");
    		run("Enhance Local Contrast (CLAHE)", "blocksize=75 histogram=256 maximum=5 mask=*None* "+sFast);
    	}
       	rename(ChannelNames[i-1]);
    }
}

// Function to make the mask for a given channel, highlighting where the tissue is
function make_channel_mask(win)
{
    selectWindow(win);
    mask_name = win + "_mask";
    run("Duplicate...", "title="+mask_name);
    run("Auto Threshold", "method=Yen white"); // This is very important. It finds the highest contrasted areas and highlights them to be white and the rest of the background to be black
    getHistogram(0, counts, 2);
    return(counts[1]);
}

// Makes the combined tissue mask to calculate total tissue area. The image calculator is finding where, in the combined image it makes when comparing, there is any signal at all
function make_combined_mask()
{
    selectWindow("DAPI_mask");
    run("Duplicate...", "title=tissue_mask");
    imageCalculator("OR", "tissue_mask","RFP_mask");
    imageCalculator("OR", "tissue_mask","GFP_mask");
    run("Close-");
    getHistogram(0, counts, 2);
    return(counts[1]);
}

/* Trying to encompass all of the tissue instead of detected high points
function make_combined_mask()
{
	for (i=0; i<3; i++)
	{
		win = ChannelNames[i];
		selectWindow(win);

		run("Duplicate...", "title="+ChannelNames[i]+"_tissue_area");
		setOption("BlackBackground", true);
		run("Convert to Mask");
	}
	selectWindow("DAPI_tissue_area");
    run("Duplicate...", "title=tissue_mask");
    imageCalculator("OR", "tissue_mask","RFP_tissue_area");
    imageCalculator("OR", "tissue_mask","GFP_tissue_area");
    run("Close-");
    getHistogram(0, counts, 2);
    for (i = 0; i < 3; i++)
    {
    	close(ChannelNames[i]+"_tissue_area");
    }
    return(counts[1]);
}
*/

function get_dapi_count(InputImage, tissue_name)
{
	selectWindow(InputImage);
	run("Duplicate...", "title=copy");
	win = "copy";
	run("Subtract Background...", "rolling=50");
//	save_file(win, "Subtracted Background - "+tissue_name);
	run("Kuwahara Filter", "sampling=5");
//	save_file(win, "Kuwahara Filter - "+tissue_name);
	run("Auto Threshold", "method=Yen white");
//	save_file(win, "Yen Auto Threshold - "+tissue_name);
	imageCalculator("Min create", "copy", InputImage);
	selectWindow("Result of copy");
	win = "Result of copy";
//	save_file(win, "Image Calculator Min - "+tissue_name);
	run("Auto Local Threshold", "method=Median radius=15 parameter_1=0 parameter_2=0 white");
//	save_file(win, "Median Auto Local threshold - "+tissue_name);
	setOption("BlackBackground", true);
	run("Watershed");
	run("Open");
	save_file(win, "Watershed Opened - "+tissue_name);
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D], args=['input':'Result of copy', 'modelChoice':'Versatile (fluorescent nuclei)', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'0.05', 'nmsThresh':'0.4', 'outputType':'Both', 'nTiles':'1', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
			
	count = roiManager("count");
			
	close("Results");
	close("ROI Manager");
			
	win = "copy";
	close(win);

	win = "Result of copy";
	close(win);

	win = "Label Image";
	close(win);

	return count;
}

function save_file(win, name)
{
	if (isOpen(win))
	{
    	selectWindow(win);
    	run("Duplicate...", "title="+name);
    	file = OutputDirectory + name + ".tif";
		increment = 0;
    	while (File.exists(file))
		{
			increment++;
			file = OutputDirectory + name + "_" + increment + ".tif";
		}
    	saveAs("Tiff", file);
    	if (increment == 0)
    	{
	    	name = name + ".tif";
	    }
	    else
    	{
	    	name = name + "_" + increment + ".tif";
	    }
    	close(name);
    	selectWindow(win);
	}
}

// Parse the ear notch designation from the file name.
function ear_from_name(InputImage)
{
    str = InputImage.toUpperCase();
    i = str.indexOf("_");
    substr = str.substring(i+1);
    j = substr.indexOf("_");
    ear = str.substring(i+1, i+j);
	
    return ear;
}

// Parse the section of the tissue from the file name.
function section_from_name(InputImage)
{
    str = InputImage.toLowerCase();
    if (str.contains("beginning"))
    {
        return vPortion[1];
    }
    if (str.contains("mid"))
    {
        return vPortion[2];
    }
    if (str.contains("end"))
    {
        return vPortion[3];
    }

    return "";
}

// Parse the mouse number/name from the file name.
function mouse_from_name(InputImage)
{
    i = InputImage.indexOf(" ");
    if (i > 0)
    {
        str = InputImage.substring(0, i);
        str = replace(str,"\\.lif","");
        return str;
    }

    return "";

}

// Parse the input filename for the animal sex, returned as a string. Valid return values are from the global array vSex.
function sex_from_name(InputImage)
{
    str = InputImage.toUpperCase();
    i = str.indexOf("_");
    substr = str.substring(i+1);
    j = substr.indexOf("_");
    sex = str.substring(i+j, i+j+1);
	
    return sex;
}

// Parse the input file name for the tissue type.
function tissue_type_from_name(InputImage)
{
    str = InputImage.toLowerCase();
	for (i = 0; i < vTissue.length; i++)
	{
		tissueName = "";
		if(str.contains(vTissue[i]))
		{
			tissueName = vTissue[i];
			if(i==2)
			{
				tissueName = tissueName+"_node";
			}
			if(i==5)
			{
				tissueName = tissueName+"_muscle";
			}
			if(i==16)
			{
				tissueName = tissueName+"_gland";
			}
			if(i==18 || i==20)
			{
				tissueName = tissueName+"_intestine";
			}
			if(str.contains("20x"))
			{
				tissueName = tissueName+"_20x";
			}
			if(str.contains("high"))
			{
				tissueName = tissueName+"_highEXP";
			}
			return tissueName;
		}
	}
    start = InputImage.indexOf("[");
    substr = InputImage.substring(start);
	end = substr.indexOf("]");
	tissueName = InputImage.substring(start+1, end+start);

	return tissueName;
}

//Using some plugins, this processes and image and returns the count of areas that resemble DAPI stained nuclei
function stardist_watershed(win, sample_num, sTissue)
{
    // Running StarDist Function on Channel Image
	selectWindow(win);
	setOption("BlackBackground", true); //Important to tell the program that the background is black
	run("Watershed"); // Segments each blob using this plugin, highlights actual, or close to it, areas of DAPI signature
    run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D], args=['input':'"+win+"', 'modelChoice':'Versatile (fluorescent nuclei)', 'normalizeInput':'true', 'percentileBottom':'0.0', 'percentileTop':'99.8', 'probThresh':'0.479071', 'nmsThresh':'0.3', 'outputType':'Both', 'nTiles':'1', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
    getPixelSize(unit, pixelWidth, pixelHeight);
    X = getWidth();
    Y = getHeight();
    winSD = win+"_validate";
    nROI = roiManager("count");

    // measure all ROIs
    roiManager("deselect");
    roiManager("measure");

    // Grabbing Each Area To Calculate Area
    if (nROI > 0)
    {
    	A = newArray(nROI);
    	for (i=0; i<nROI; i++)
    	{
        	A[i] = getResult("Area", i);
	    }
	
	    Array.sort(A);
	    median = A[nROI/2];
	    sizeThresh = 2.5 * median;
	
	    selectWindow(win);
	    run("Select None");
	    run("Duplicate...", "title="+winSD);    // Duplicate The Original Image Rather Than New One
	    run("RGB Color");
	    count = nROI;

    	for (row=0; row<nROI; row++)
    	{
        	x = getResult("X", row)/pixelWidth;
        	y = getResult("Y", row)/pixelWidth;
        	a = getResult("Area", row);
        	setPixel(x, y, 0xfc21b3);
        	extra_count = (a / median) - 1;
        	x2 = x + 2;
        	while (extra_count > 1.5)
        	{
            	count++;
            	setPixel(x2, y, 0xfc21b3);
            	extra_count -= median;
            	x2 += 2;
	        }
    	}
    }

    run("Clear Results");    // Preventing All XY Coords To Be In Results Window
    close("Results");
    close("ROI Manager");
    return(nROI);
}

// Housekeeping stuff to finish the program
function clean_up(InputImage)
{
	//Returns the filename to name the Stats table
	fileName = replace(InputImage,"\\s.*","");
    fileName = replace(fileName,"\\.lif","");

	//Updates the table to show the user and saves it in the output directory
    Table.update();
    selectWindow("Stats");
    file = OutputDirectory+fileName+"_Stats.csv";
    results_name = fileName+"_Stats.csv";
    saveAs("Results", file);

    //Lets the user know where their files are located
    Dialog.createNonBlocking("SCGE.ijm - "+Version);
    Dialog.addMessage("Succesfully Completed The Script\nThe files can be found at:\n"+OutputDirectory);
    Dialog.addCheckbox("Keep images open", false);
    Dialog.addCheckbox("Keep results table open", false);
    
    Dialog.show();

    close_images = Dialog.getCheckbox();
    close_results = Dialog.getCheckbox();
    
    //Keeps open or closes images that were analyzed, as well as the results, Stats, table
    if(!close_images)
    {
    	setBatchMode(false);
    	close("*");
    }
    else
    {
    	setBatchMode("exit & display");
    }
    if(!close_results)
    {
    	close(results_name);
    }
}
