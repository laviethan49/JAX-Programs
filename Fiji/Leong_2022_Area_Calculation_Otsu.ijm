#@ File(label="Input File Name", value="298_Bankiewicz_Cas9_RNP_505.lif") input_file

widefield_file_process();

//Processes a .lif file from a widefield microscope. Splits the channels, chooses the red channel, enhances contrast (tries CLAHE and has user check), creates mask of "signal"
// lets user define ROI, measures "signal" by counting pixels at max intesinty (since it is a mask, this is all pixels that have been found to have signal)
// saves created images and table
function widefield_file_process()
{
	run("Bio-Formats Macro Extensions");
	Ext.setId(input_file);
	Ext.getSeriesCount(series_count);
	
	Dialog.createNonBlocking("Series Start/ End");
	Dialog.addMessage("Select which series to start and end at");
	Dialog.addNumber("Start: ", 1);
	Dialog.addNumber("End: ", series_count);
	Dialog.show();
	
	start_series = Dialog.getNumber();
	end_series = Dialog.getNumber();
	
	index_num = indexOf(input_file, ".lif") - 3;
	animal_num = input_file.substring(index_num, index_num+3);
	
	row = 0;
	project_title = "00298_Leong";
	path = "C:/Users/savile/Desktop/Ethan - Leong 2022 Image Analysis/Area Measurements (Widefield Images)/"+animal_num+"/";
	
	if(!File.exists(path))
	{
		File.makeDirectory(path);
	}
	create_empty_table();
	
	for(i = start_series; i <= end_series; i++)
	{
		series_analyzed=" series_"+i;
		run("Bio-Formats Importer", "open=["+input_file+"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
	
		getPixelSize(units, pixelWidth, pixelHeight);
		list = getList("image.titles");
		index_num = indexOf(list[0], "Merged") - 11;
		well_sect_ident = list[0].substring(index_num, index_num+6);

		red_channel = "";
		file_name = "298_Leong_Cas9_RNP_"+animal_num+"_"+well_sect_ident;//
		thresh_options = newArray("Otsu");
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
		selectWindow(red_channel);
		
		for(k = 0; k <= thresh_options.length-1; k++)
		{
			selectWindow(red_channel);
			run("Duplicate...", "title="+file_name+"_"+thresh_options[k]);
			run("Auto Threshold", "method="+thresh_options[k]+" white");
			run("Convert to Mask");
			
			quant_side = "Right";
			setTool("freehand");
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
				save_file(path+file_name+"_"+thresh_options[k]+"_processed_experimental.tif");
				Table.set("Project", row, project_title);
				Table.set("Animal Number", row, animal_num);
				Table.set("Well and Section", row, well_sect_ident);
				Table.set("Red Area", row, red_area);
				Table.set("Threshold Method", row, thresh_options[k]);
				Table.set("Side of Brain", row, "Experimental");
				Table.set("Units", row, units+" squared");
				Table.update();
				row += 1;
				close();
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