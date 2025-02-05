//#@ File(label="Input File Name") input_file
#@ File[] file_paths (label="select files or folders", style="both")

setBatchMode(true);
//split_images(input_file);
split_images_multiple_files(file_paths);

function split_images_multiple_files(file_names)
{
	run("Bio-Formats Macro Extensions");
	image_types = newArray("Tiff", "Gif", "Jpeg", "Raw Data", "AVI", "BMP", "PNG");
	
	Dialog.createNonBlocking("Image Splitter");
	Dialog.addChoice("Type of image to save as:", image_types);
	binary_choice = newArray("No", "Yes");
	Dialog.addRadioButtonGroup("Save each channel independently?", binary_choice, 1, 2, binary_choice[0]);
	Dialog.show();
	
	image_type = Dialog.getChoice();
	split_channels = Dialog.getRadioButton();
	for (k = 0;k < file_names.length;k++) {
        file_name = file_names[k];
    	spacer_index = lastIndexOf(file_name, "\\");
		name_of_file = substring(file_name, spacer_index+1);
		name_of_file = replace(name_of_file, ".lif", "");
		
		path = "C:/Users/Public/Documents/"+name_of_file+"/";
		
		if(!File.exists(path))
		{
			File.makeDirectory(path);
		}
		
		run("Bio-Formats Macro Extensions");
		Ext.setId(file_name);
		Ext.getSeriesCount(series_count);
		for(i = 1; i <= series_count; i++)
		{
			series_analyzed=" series_"+i;
			run("Bio-Formats Importer", "open=["+file_name+"] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
		
			if(split_channels == binary_choice[1])
			{
				run("Duplicate...", "duplicate");
				run("Split Channels");
			}
	
			list = getList("image.titles");
		
			for(j = 0; j <= list.length-1; j++)
			{
				selectWindow(list[j]);
				temp_name = replace(list[j], ".lif", "");
				temp_name = replace(temp_name, "/", " ");
				saveAs(image_type, path+temp_name);
			}
		
			close("*");
		}
	}
	
	waitForUser("The images have been saved at the Documents folder in the Public user");
}

function split_images(file_name)
{
	run("Bio-Formats Macro Extensions");
	Ext.setId(input_file);
	Ext.getSeriesCount(series_count);
	image_types = newArray("Tiff", "Gif", "Jpeg", "Raw Data", "AVI", "BMP", "PNG");
	
	Dialog.createNonBlocking("Series Start/ End");
	Dialog.addMessage("Select which series to start and end at");
	Dialog.addNumber("Start: ", 1);
	Dialog.addNumber("End: ", series_count);
	Dialog.addChoice("Type of image to save as:", image_types);
	binary_choice = newArray("No", "Yes");
	Dialog.addRadioButtonGroup("Save each channel independently?", binary_choice, 1, 2, binary_choice[0]);
	Dialog.show();
	
	start_series = Dialog.getNumber();
	end_series = Dialog.getNumber();
	image_type = Dialog.getChoice();
	split_channels = Dialog.getRadioButton();
	
	spacer_index = lastIndexOf(file_name, "\\");
	name_of_file = substring(file_name, spacer_index+1);
	name_of_file = replace(name_of_file, ".lif", "");
	
	path = "C:/Users/Public/Documents/"+name_of_file+"/";
	
	if(!File.exists(path))
	{
		File.makeDirectory(path);
	}
	
	for(i = start_series; i <= end_series; i++)
	{
		series_analyzed=" series_"+i;
		run("Bio-Formats Importer", "open=["+file_name+"] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);
		
		if(split_channels == binary_choice[1])
		{
			run("Duplicate...", "duplicate");
			run("Split Channels");
		}
	
		list = getList("image.titles");
		
		for(j = 0; j <= list.length-1; j++)
		{
			selectWindow(list[j]);
			temp_name = replace(list[j], ".lif", "");
			temp_name = replace(temp_name, "/", " ");
			saveAs(image_type, path+temp_name);
		}
		
		close("*");
	}
	
	waitForUser("The images have been saved at: "+path);
}