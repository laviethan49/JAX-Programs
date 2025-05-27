/*
 * Script for iterating over Bankiewicz 2022 imageas for:
 * drawing ROI around striatum
 * measure area
 * measure points of ROI for verification later if necessary
 * measure image title for animal number, slide, section for identification
 * report all measurements
 * save file of measurements
 * Ethan Saville 4/15/2022
 */
 
 //Input file, prompts user on start of program
#@ File(label="Input File Name") input_file

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
	project_title = "00298_Bankiewicz";
	path = "C:/Users/savile/Desktop/Bankiewicz Striatum/"+animal_num+"/";
	
	if(!File.exists(path))
	{
		File.makeDirectory(path);
	}
	create_empty_table();
	
	//Opens the specific series in the file, from start to end, to save on ram during processing
	for(i = start_series; i <= end_series; i++)
	{
		series_analyzed=" series_"+i;
		run("Bio-Formats Importer", "open=["+input_file+"] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
	
		//Returns the pixel size and units in the below variable names
		getPixelSize(units, pixelWidth, pixelHeight);
		list = getList("image.titles");
		//Returns the identifiers for the specific section being analyzed based on our naming scheme e.g. 298_Leong_sgAAV # 536_F.lif #536_brain_sl_C1/brain_sl_C1_se5 Merged
		index_num = lastIndexOf(list[0], ".lif");
		well_sect_ident = list[0].substring(index_num-9, index_num);
		//Removes the slash, as it would throw an error trying to make a new directory
		well_sect_ident = well_sect_ident.replace("/", "");

		red_channel = "";
		file_name = "00298_Bankiewicz_"+animal_num+"_"+well_sect_ident;
		file_name = replace(file_name, " ", "_");
		
//		run("Channels Tool...");
		Property.set("CompositeProjection", "Sum");
		Stack.setDisplayMode("composite");
			
		setTool("freehand");
		waitForUser("Draw ROI for a striatum");
		if(selectionType() < 0)
		{
			selectWindow("Stats");
			Table.set("Project", row, project_title);
			Table.set("Animal Number", row, animal_num);
			Table.set("Well and Section", row, well_sect_ident+" Skipped");
			Table.set("ROI Area", row, 0);
			Table.set("Units", row, units+" squared");
			Table.update;
			row += 1;
		}
		else
		{
			Roi.getCoordinates(xpoints, ypoints);
			run("Set Measurements...", "area redirect=None decimal=4");
			roiManager("Add");
			roiManager("Measure");
			roiManager("Save", path+project_title+"_"+animal_num+"_"+well_sect_ident+".roi");
			roiManager("Select", 0);
			roiManager("Delete");
			mArea = getResult("Area");
			selectWindow("Stats");
			Table.set("Project", row, project_title);
			Table.set("Animal Number", row, animal_num);
			Table.set("Well and Section", row, well_sect_ident);
			Table.set("ROI Area", row, mArea);
			Table.set("Units", row, units+" squared");
			Table.update;
			row += 1;
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

function array_to_string(array_input)
{
	length = array_input.length;
	output_string = "";
	for(i = 0; i < length;i++)
	{
		output_string = output_string + array_input[i] + ",";
	}
	return output_string;
}