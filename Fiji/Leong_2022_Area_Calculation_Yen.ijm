//Input file, prompts user on start of program
#@ File(label="Input File Name", value="298_Bankiewicz_Cas9_RNP_505.lif") input_file

//Calls the function for processing the images
widefield_file_process();

//Processes a .lif file from a widefield microscope. Splits the channels, chooses the red channel, enhances contrast, creates mask of signal
// lets user define ROI, measures signal by counting pixels at max intesinty (since it is a mask, this is all pixels that have been found to have signal)
// saves created images and table
function widefield_file_process()
{
	//Starts process to get amount of series in a given file
	run("Bio-Formats Macro Extensions");
	Ext.setId(input_file);
	Ext.getSeriesCount(series_count);
	
	//Asks user which series to start and end at
	Dialog.createNonBlocking("Series Start/ End");
	Dialog.addMessage("Select which series to start and end at");
	Dialog.addNumber("Start: ", 1);
	Dialog.addNumber("End: ", series_count);
	Dialog.show();
	
	start_series = Dialog.getNumber();
	end_series = Dialog.getNumber();
	
	//This gets the animal number based on the naming scheme we had used e.g. 00298_Leong_Group2_F_559.lif
	index_num = indexOf(input_file, ".lif") - 3;
	animal_num = input_file.substring(index_num, index_num+3);
	
	//Sets the initial row for the table, as well as the project title, and path for saving the files.
	row = 0;
	project_title = "00298_Zhou";
	path = "C:/Users/savile/Desktop/Zhou/"+animal_num+"/";
	
	if(!File.exists(path))
	{
		File.makeDirectory(path);
	}
	create_empty_table();
	
	//Opens the specific series in the file, from start to end, to save on ram during processing
	for(i = start_series; i <= end_series; i++)
	{
		series_analyzed=" series_"+i;
		run("Bio-Formats Importer", "open=["+input_file+"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
	
		//Returns the pixel size and units in the below variable names
		getPixelSize(units, pixelWidth, pixelHeight);
		list = getList("image.titles");
		//Returns the identifiers for the specific section being analyzed based on our naming scheme e.g. 298_Leong_sgAAV # 536_F.lif #536_brain_sl_C1/brain_sl_C1_se5 Merged
		index_num = indexOf(list[0], "-")+1;
		well_sect_ident = list[0].substring(index_num, index_num+7);
		//Removes the slash, as it would throw an error trying to make a new directory
		well_sect_ident = well_sect_ident.replace("/", "");

		red_channel = "";
		file_name = "00298_Zhou_Group_2_"+animal_num+"_"+well_sect_ident;
		file_name = replace(file_name, " ", "_");
		//Sets the auto-threshold method to be used, in this case, Yen
		//This is different from the auto threshold function, as this performs a "manual threshold" with the yen as the automatic selection, very weird
		thresh_options = newArray("Yen");
		//Settings for the threshold to see the tissue as white, and background as black
		call("ij.plugin.frame.ThresholdAdjuster.setMode", "B&W");
		setOption("BlackBackground", true);
	
		run("Split Channels");
	
		list = getList("image.titles");
		for(j = 0; j < list.length; j++)
		{
			ident = list[j].substring(0,2);
			//C1 is usually the red channel, if it is not, change this to 2 or 3 based on the red slice
			if(ident == "C1")
				red_channel = list[j];
		}
	
		selectWindow(red_channel);
		run("Enhance Contrast...", "saturated=0.3");
		
		for(k = 0; k <= thresh_options.length-1; k++)
		{
			selectWindow(red_channel);
			run("Duplicate...", "title="+file_name+"_"+thresh_options[k]);
			
			//This part gets the total tissue area of the section, using the triangle threshold method
			run("Duplicate...", "duplicate title=duplicate_"+file_name+"_"+thresh_options[k]);
			setAutoThreshold("Triangle dark no-reset");
			setOption("BlackBackground", true);
			run("Convert to Mask");
			run("Close-");
			
			//This performs the threshold on the tissue with the yen method, but does not convert it to a mask until after ROI selection for easily seeing the section
			selectWindow(file_name+"_"+thresh_options[k]);
			setAutoThreshold("Yen dark no-reset");
			setOption("BlackBackground", true);
			
			quant_side = "Right";
			setTool("freehand");
			//Make a selection for the side of the brain, experimental, or son't draw anything and it gets skipped
			waitForUser("Draw ROI for experimental side, then click OK, or just click OK if threshold is not good.");
			if(selectionType() < 0)
			{
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident+" Skipped");
				Table.set("Red Area", row, "0");
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("Side of Brain", row, "Experimental");
				Table.set("Units", row, units+" squared");
				Table.set("Total Area Possible", row, 0);
				Table.update();
				row += 1;
			}
			else
			{
				run("Convert to Mask");
				save_file(path+file_name+"_"+thresh_options[k]+".tif");
				run("Restore Selection");
				run("Copy");
				Roi.getBounds(x, y, width, height);
				newImage(file_name+" Experimental", "8-bit black", width, height, 1);
				run("Paste");
				getHistogram(0, count, 2);
				red_area = count[1];
				red_area *= (pixelWidth * pixelHeight);
				save_file(path+file_name+"_"+thresh_options[k]+"_processed_experimental.tif");
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident);
				Table.set("Red Area", row, red_area);
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("Side of Brain", row, "Experimental");
				Table.set("Units", row, units+" squared");
				close();
				
				selectWindow("duplicate_"+file_name+"_"+thresh_options[k]);
				run("Restore Selection");
				run("Cut");
				Roi.getBounds(x, y, width, height);
				newImage(file_name+" Experimental Total Area", "8-bit black", width, height, 1);
				run("Paste");
				getHistogram(0, count, 2);
				red_area = count[1];
				red_area *= (pixelWidth * pixelHeight);
				Table.set("Total Area Possible", row, red_area);
				Table.update();
				row += 1;
				close();
				
				selectWindow(file_name+"_"+thresh_options[k]+".tif");
				setBackgroundColor(0, 0, 0);
				run("Clear", "slice");
			}
			
			run("Select None");
			quant_side = "Left";
			setTool("freehand");
			waitForUser("Draw ROI for control side, then click OK, or just click OK if threshold is not good.");
			if(selectionType() < 0)
			{
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident+" Skipped");
				Table.set("Red Area", row, "0");
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("Side of Brain", row, "Control");
				Table.set("Units", row, units+" squared");
				Table.set("Total Area Possible", row, 0);
				Table.update();
				row += 1;
			}
			else
			{
				run("Copy");
				Roi.getBounds(x, y, width, height);
				newImage(file_name+" Control", "8-bit black", width, height, 1);
				run("Paste");
				getHistogram(0, count, 2);
				red_area = count[1];
				red_area *= (pixelWidth * pixelHeight);
				save_file(path+file_name+"_"+thresh_options[k]+"_processed_control.tif");
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident);
				Table.set("Red Area", row, red_area);
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("Side of Brain", row, "Control");
				Table.set("Units", row, units+" squared");
				close();
				
				selectWindow("duplicate_"+file_name+"_"+thresh_options[k]);
				run("Restore Selection");
				run("Cut");
				Roi.getBounds(x, y, width, height);
				newImage(file_name+" Experimental Total Area", "8-bit black", width, height, 1);
				run("Paste");
				getHistogram(0, count, 2);
				red_area = count[1];
				red_area *= (pixelWidth * pixelHeight);
				Table.set("Total Area Possible", row, red_area);
				Table.update();
				row += 1;
				close();
			}
		}
		
		close("*");
	}
	
	selectWindow("Stats");
	file = path+project_title+"_"+animal_num+"_Stats.csv";
	saveAs("Results", file);
}

function create_empty_table()
{
    Table.create("Stats");

    Table.setLocationAndSize(20, 20, 1080, 720) ;
    Table.update();
}

function save_file(name)
{
	file = name.replace(".tif", "");
	increment = 0;
    while (File.exists(file))
	{
		increment++;
		file = name.replace(".tif", "") + "_" + increment;
	}
    saveAs("Tiff", file);
}