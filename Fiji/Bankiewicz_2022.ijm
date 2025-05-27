//#@ String(label="Animal Number") animal_num
//#@ String (choices={"Widefield", "Confocal"}, style="radioButtonHorizontal", label="Image type") image_type_choice
//#@ String(choices={"Right", "Left"}, style="radioButtonHorizontal", label="Area of Interest on Striatum") quant_side
//#@ File(label="Input File Name", value="298_Bankiewicz_Cas9_RNP_505.lif") input_file

//if(image_type_choice == "Widefield")
//{
	widefield_file_process();
//}
//else
//{
//	confocal_file_process();
//}

//Processes a .lif file from a widefield microscope. Splits the channels, chooses the red channel, enhances contrast (tries CLAHE and has user check), creates mask of "signal"
// lets user define ROI, measures "signal" by counting pixels at max intesinty (since it is a mask, this is all pixels that have been found to have signal)
// saves created images and table
function widefield_file_process()
{
	run("Bio-Formats Macro Extensions");
//	Ext.setId(input_file);
//	Ext.getSeriesCount(series_count);
	
//	Dialog.createNonBlocking("Series Start/ End");
//	Dialog.addMessage("Select which series to start and end at");
//	Dialog.addNumber("Start: ", 1);
//	Dialog.addNumber("End: ", series_count);
//	Dialog.show();
//	
//	start_series = Dialog.getNumber();
//	end_series = Dialog.getNumber();
//	
//	index_num = indexOf(input_file, ".lif") - 3;
//	animal_num = input_file.substring(index_num, index_num+3);

	animal_num = 509;
	
	row = 0;
	project_title = "00298_Bankiewicz";// Change is project is different, but the original program was for Bankiewicz
	path = "D:/Ethan - Bankiewicz 2022 Image Analysis/Area Measurements (Widefield Images)/"+animal_num+"/widefield/";
	
	if(!File.exists(path))
	{
		File.makeDirectory(path);
	}
	create_empty_table();
	
	list_one = getList("image.titles");
	start_series = 0;
	end_series = list_one.length-1;
	
	for(i = start_series; i <= end_series; i++)
	{
//		series_analyzed=" series_"+i;
//		run("Bio-Formats Importer", "open=["+input_file+"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
	
		getPixelSize(units, pixelWidth, pixelHeight);
//		list = getList("image.titles");
		list = list_one[i];
		index_num = indexOf(list, "Merged") - 11;
		well_sect_ident = list.substring(index_num, index_num+6);

		red_channel = "";
		file_name = "298_Bankiewicz_Cas9_RNP_"+animal_num+"_"+well_sect_ident+"_widefield";//
		thresh_options = newArray("Yen");//, "Otsu");
		call("ij.plugin.frame.ThresholdAdjuster.setMode", "B&W");
		setOption("BlackBackground", true);
	
		run("Split Channels");
	
		list = getList("image.titles");
		for(j = 0; j < list.length; j++)
		{
			ident = list[j].substring(0,2);
			if(ident == "C1")
				red_channel = list[j];
		}
	
		selectWindow(red_channel);
		run("Enhance Contrast...", "saturated=0.3");
		clahe_used = "No";
		selectWindow(red_channel);
		
		for(k = 0; k <= thresh_options.length-1; k++)
		{
			selectWindow(red_channel);
			run("Duplicate...", "title="+file_name+"_"+thresh_options[k]);
			run("Auto Threshold", "method="+thresh_options[k]+" white");
			run("Convert to Mask");
			
			Dialog.create("ROI Quality Assurance");
			Dialog.addRadioButtonGroup("Is the mask good?", newArray("Good", "Bad"), 2, 1, "Good");
			Dialog.show();
			choice = Dialog.getRadioButton();
			
			if(choice == "Bad")
			{
				close();
				run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=3 mask=*None* fast_(less_accurate)");
				run("Auto Threshold", "method="+thresh_options[k]+" white");
				run("Convert to Mask");
				clahe_used = "Yes";
			}
			
			quant_side = "Right";
			setTool("freehand");
			waitForUser("Draw ROI for right side, then click OK, or just click OK if threshold is not good.");
			if(selectionType() < 0)
			{
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident+" Skipped");
				Table.set("Red Area", row, "0");
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("CLAHE Used", row, clahe_used);
				Table.set("Side of Brain", row, "Experimental/ Right");
				Table.set("Units", row, units+" squared");
				Table.update();
				row += 1;
			}
			else
			{
				save_file(path+file_name+"_"+thresh_options[k]+".tif");
				run("Copy");
				Roi.getBounds(x, y, width, height);
				newImage(file_name+" Experimental", "8-bit black", width, height, 1);
				run("Paste");
				getHistogram(0, count, 2);
				red_area = count[1];
				red_area *= (pixelWidth * pixelHeight);
				save_file(path+file_name+"_"+thresh_options[k]+"_processed_experimental-right.tif");
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident);
				Table.set("Red Area", row, red_area);
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("CLAHE Used", row, clahe_used);
				Table.set("Side of Brain", row, "Experimental/ Right");
				Table.set("Units", row, units+" squared");
				Table.update();
				row += 1;
				close();
			}
			
			run("Select None");
			quant_side = "Left";
			setTool("freehand");
			waitForUser("Draw ROI for left side, then click OK, or just click OK if threshold is not good.");
			if(selectionType() < 0)
			{
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident+" Skipped");
				Table.set("Red Area", row, "0");
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("CLAHE Used", row, clahe_used);
				Table.set("Side of Brain", row, "Control/ Left");
				Table.set("Units", row, units+" squared");
				Table.update();
				row += 1;
			}
			else
			{
//				save_file(path+file_name+"_"+thresh_options[k]+"_experimental-left.tif");
				run("Copy");
				Roi.getBounds(x, y, width, height);
				newImage(file_name+" Control", "8-bit black", width, height, 1);
				run("Paste");
				getHistogram(0, count, 2);
				red_area = count[1];
				red_area *= (pixelWidth * pixelHeight);
				save_file(path+file_name+"_"+thresh_options[k]+"_processed_control-left.tif");
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident);
				Table.set("Red Area", row, red_area);
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("CLAHE Used", row, clahe_used);
				Table.set("Side of Brain", row, "Control/ Left");
				Table.set("Units", row, units+" squared");
				Table.update();
				row += 1;
				close();
			}
			close();
		}
		list = getList("image.titles");
		for(j = 0; j < list.length; j++)
		{
			ident = list[j].substring(0,1);
			if(ident == "C")
			{
				selected = list[j];
				selectWindow(selected);
				close();
			}				
		}
		
//		close("*");
	}
	
	selectWindow("Stats");
	file = path+project_title+"_"+animal_num+"_Stats_Yen.csv";
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

function confocal_file_process()
{}
//{
//	run("Bio-Formats Macro Extensions");
//	Ext.setId(input_file);
//	Ext.getSeriesCount(series_count);
//	
//	row = 0;
//	path = "D:/Ethan - Bankiewicz 2022 Image Analysis/"+animal_num+"/confocal/";
//	if(!File.exists(path))
//	{
//		File.makeDirectory(path);
//	}
//	create_empty_table();
//	
//	for(i = 1; i <= series_count; i++)
//	{
//		series_analyzed=" series_"+i;
//		run("Bio-Formats Importer", "open=["+input_file+"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
//	
//		getPixelSize(units, pixelWidth, pixelHeight);
//		list = getList("image.titles");
//		index_num = indexOf(list[0], ".lif") + 7;
//		title_length = list[0].length;
//		well_sect_ident = list[0].substring(index_num, title_length);
//		well_sect_ident = well_sect_ident.replace(" Merged", "");
//
//		red_channel = "";
//		green_channel = "";
//		file_name = "298_Bankiewicz_Cas9_RNP_"+animal_num+"_"+well_sect_ident+"_confocal";//
//		thresh_options = newArray("Otsu");//, "Yen");
//		call("ij.plugin.frame.ThresholdAdjuster.setMode", "B&W");
//		setOption("BlackBackground", true);
//	
//		run("Split Channels");
//	
//		list = getList("image.titles");
//		for(j = 0; j < list.length; j++)
//		{
//			ident = list[j].substring(0,2);
//			if(ident == "C1")
//				red_channel = list[j];
//			if(ident == "C2")
//				green_channel = list[j];
//		}
//	
////		selectWindow(red_channel);
////		setTool("freehand");
////		waitForUser("Draw ROI around striatum, then click OK.");
////		Roi.getBounds(x, y, width, height);
////		run("Copy");
////		newImage("red_cells", "8-bit black", width, height, 1);
////		run("Paste");
//////		save_file(path+"298_Bankiewicz_"+animal_num+"_"+well_sect_ident+"_red_cells_pre-threshold");
////		rename("red_cells");
////		run("Auto Threshold", "method=Yen white");
////		run("Despeckle");
////		run("Close-");
//////		run("Erode");
////		run("Fill Holes");
////		run("Watershed");
////		run("Analyze Particles...", "size=60-1000 circularity=0.150-1.00 show=Overlay display clear");
////		red_cells = nResults;
////		selectWindow(red_channel);
//		
//		selectWindow(green_channel);
////		run("Restore Selection");
//		run("Copy");
//		Roi.getBounds(x, y, width, height);
//		newImage("green_cells", "8-bit black", width, height, 1);
//		run("Paste");
//		run("Despeckle");
//		run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=3 mask=*None* fast_(less_accurate)");
//		run("Subtract Background...", "rolling=25");
//		save_file("C:/Users/savile/Desktop/"+getTitle()+" Green Channel");
////		path+"298_Bankiewicz_"+animal_num+"_"+well_sect_ident+"_green_cells_pre-threshold"
//		rename("green_cells");
//		run("Auto Threshold", "method=Yen white");
//		run("Despeckle");
////		run("Erode");
//		run("Close-");
////		run("Fill Holes");
////		run("Watershed");
//		save_file("C:/Users/savile/Desktop/"+getTitle()+" Green Channel Thresholded");
//		run("Analyze Particles...", "size=60-1000 circularity=0.150-1.00 show=Overlay display clear");
//		green_cells = nResults;
//		
//		imageCalculator("Min create", "green_cells","red_cells");
//		run("Analyze Particles...", "size=60-1000 circularity=0.150-1.00 show=Overlay display clear");
//		both_cells = nResults;
//		print("Both: "+both_cells+" Red: "+red_cells+" Green: "+green_cells);
//		save_file(path+"298_Bankiewicz_"+animal_num+"_"+well_sect_ident+"_colocalized_cells");
//		selectWindow("red_cells");
//		save_file(path+"298_Bankiewicz_"+animal_num+"_"+well_sect_ident+"_red_cells");
//		selectWindow("green_cells");
//		save_file(path+"298_Bankiewicz_"+animal_num+"_"+well_sect_ident+"_green_cells");
//		
//		selectWindow("Stats");
//		Table.set("Well and Section", row, well_sect_ident);
//		Table.set("Red Cells", row, red_cells);
//		Table.set("Green Cells", row, green_cells);
//		Table.set("Colocalized Cells", row, both_cells);
//		Table.set("Red over Green Percent", row, ((both_cells/green_cells)*100));
//		Table.set("Threshold Method", row, "Yen");
//		Table.update();
//		row += 1;
//			
//		close("*");
//	}
//	
//	selectWindow("Stats");
//	file = path+"298_Bankiewicz_"+animal_num+"_Stats.csv";
//	saveAs("Results", file);
//}