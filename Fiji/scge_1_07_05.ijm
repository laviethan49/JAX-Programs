row = 0;
vTissue = newArray("<select tissue>","brain","lymph","eye","diaphragm","gastrocnemius","heart","thymus","skin","lung","bladder","testes","epididymis","spleen","liver","kidney","adrenal","pancreas","small","stomach","large","ovary","uterus");

main();
//30 um area
function main()
{	
//	setBatchMode(true);// Issues with red channel
	start = getTime();
	run("Jax Omero Connect Personal");
	imageList = getList("image.titles");
	imageCount = imageList.length;
	if (imageCount <= 0)
	{
		Dialog.createNonBlocking("Testing Stuff");
		Dialog.addMessage("There are no images open.");
		Dialog.show();
		exit();
	}
	else
	{
		/*
		 * On each open image:
		 * 	duplicate
		 * 	apply kuwahara filter
		 * 	auto thershold (yen)
		 * 	imageCalculator (min) - copy first in arguments
		 * 	auto local threshold (median or nilback)
		 * 	watershed
		 * 	StarDist2D
		 * 	count
		*/
		OutputDirectory = check_dir("C:/Users/Public/Documents/"+mouse_from_name(imageList[0]));
		File.makeDirectory(OutputDirectory);

		getPixelSize(units, pixelWidth, pixelHeight);
		
		if (!isOpen("Stats"))
		{
			create_empty_table();
		}

        for(i = 0; i < imageCount; i++)
        {
        	start_tissue = getTime();
        	currentImage = imageList[i];
			animal = mouse_from_name(currentImage);
			tissue = tissue_type_from_name(currentImage);
        	selectWindow(currentImage);
        	run("Duplicate...", "duplicate title=areas");
        	run("Split Channels");
        	for(j=1;j<=3;j++)
        	{
        		name = "C" + toString(j) + "-areas";
        		selectWindow(name);
        		
        		run("Despeckle");
    			run("Multiply...", "value=16");
    			run("Enhance Local Contrast (CLAHE)", "blocksize=75 histogram=256 maximum=10 mask=*None* fast_(less_accurate)");
        	}
        	RFP_area = make_channel_mask("C1-areas", tissue);
        	RFP_area *= (pixelWidth * pixelHeight);
        	GFP_area = make_channel_mask("C2-areas", tissue);
        	GFP_area *= (pixelWidth * pixelHeight);
        	DAPI_area = make_channel_mask("C3-areas", tissue);
        	DAPI_area *= (pixelWidth * pixelHeight);

        	total_area = make_combined_mask(currentImage, tissue);
        	total_area *= (pixelWidth * pixelHeight);

    		DAPI_count = get_dapi_count("C3-areas", tissue);

    		close("C1-areas");
    		close("C2-areas");
    		close("C3-areas");
    		
    		RFP_percent = (RFP_area/total_area)*100;
    		GFP_percent = (GFP_area/total_area)*100;
    		DAPI_percent = (DAPI_area/total_area)*100;

    		finish_tissue = getTime();
    		time_tissue = finish_tissue - start_tissue;

			Table.set("Animal", row, animal);
			Table.set("Tissue", row, tissue);
			Table.set("DAPI_count", row, DAPI_count);
			Table.set("tissue_area", row, total_area);
    		Table.set("RFP_area", row, RFP_area);
    		Table.set("RFP_%_area", row, RFP_percent);
    		Table.set("GFP_area", row, GFP_area);
    		Table.set("GFP_%_area", row, GFP_percent);
    		Table.set("DAPI_area", row, DAPI_area);
    		Table.set("DAPI_%_area", row, DAPI_percent);
    		Table.set("Time For Process", row, time_tissue);
//    	    Table.set("units", row, units);
			Table.update();
			
			row += 1;
        }
		end = getTime();
		totalTime = end - start;
        clean_up(imageList, totalTime);
	}
}

// Function to make the mask for a given channel, highlighting where the tissue is
function make_channel_mask(win, tissue_type)
{
    selectWindow(win);
    mask_name = win + "_mask";
    run("Duplicate...", "title="+mask_name);
    selectWindow(mask_name);
    run("Auto Threshold", "method=Yen white"); // This is very important. It finds the highest contrasted areas and highlights them to be white and the rest of the background to be black
    run("Open");
    save_file(mask_name, mask_name + " - " + tissue_type);
    getHistogram(0, counts, 2);
    close(mask_name);
    return(counts[1]);
}

// Makes the combined tissue mask to calculate total tissue area. The image calculator is finding where, in the combined image it makes when comparing, there is any signal at all
function make_combined_mask(InputImage, tissue_type)
{
	selectWindow(InputImage);
	run("Duplicate...", "duplicate title=tmp");
	selectWindow("tmp");
    run("Split Channels");
    for (i=1; i<=3; i++)
    {
    	name = "C" + toString(i) + "-tmp";
       	selectWindow(name);
       	run("Despeckle");
       	run("Enhance Contrast", "saturated=0.35");
		run("Apply LUT");
		run("Enhance Local Contrast (CLAHE)", "blocksize=75 histogram=256 maximum=5 mask=*None* fast_(less_accurate)");
		run("Auto Threshold", "method=Yen white");
    }
    selectWindow("C3-tmp");
    run("Duplicate...", "title=tissue_mask");
    imageCalculator("OR", "tissue_mask","C1-tmp");
    imageCalculator("OR", "tissue_mask","C2-tmp");
    run("Close-");
    win = "tissue_mask";
    save_file(win, win + " - " + tissue_type);
    getHistogram(0, counts, 2);

	for(i=0;i<=3;i++)
	{
		name = "C" + toString(i) + "-tmp";
		close(name);
	}
	close("tmp");
	close("tissue_mask");
    return(counts[1]);
}

function get_dapi_count(InputImage, tissue_name)
{
	selectWindow(InputImage);
	run("Duplicate...", "title=copy");
	win = "copy";
	run("Subtract Background...", "rolling=50");
	save_file(win, "Subtracted Background - "+tissue_name);
	run("Kuwahara Filter", "sampling=5");
	save_file(win, "Kuwahara Filter - "+tissue_name);
	run("Auto Threshold", "method=Yen white");
	save_file(win, "Yen Auto Threshold - "+tissue_name);
	imageCalculator("Min create", "copy", InputImage);
	selectWindow("Result of copy");
	win = "Result of copy";
	save_file(win, "Image Calculator Min - "+tissue_name);
	run("Auto Local Threshold", "method=Median radius=15 parameter_1=0 parameter_2=0 white");
	save_file(win, "Median Auto Local threshold - "+tissue_name);
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

function create_empty_table()
{
    Table.create("Stats");

    Table.setLocationAndSize(20, 20, 1080, 720) ;
    Table.update();
}

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
    if (start != -1)
    {
    	substr = InputImage.substring(start);
		end = substr.indexOf("]");
		tissueName = InputImage.substring(start+1, end+start);
		
		return tissueName;
    }

	return str;
}

function clean_up(imageList, time)
{
	close("*");
    setBatchMode(false);
    Table.update();
    selectWindow("Stats");
    Table.sort("Tissue");
    Table.sort("Animal");
    file = OutputDirectory+mouse_from_name(imageList[0])+"_Stats.csv";
    saveAs("Results", file);
    Dialog.createNonBlocking("Succesfully Completed");
    Dialog.addMessage("Images processed succesfully, it took " + time/1000 + " seconds");
    Dialog.show();
}
