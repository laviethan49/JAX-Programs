//Made by Ethan
#@ File(label="LIF Input File") input_file
//#@ String(label="Project Name") project_title
//#@ Integer(label="Animal Number") animal_num
//#@ String(label="Animal Sex") animal_sex

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

//row = 0;
//
//path = "C:/Users/Public/Documents/"+project_title+"/"+animal_num+"/";
//	
//if(!File.exists(path))
//{
//	File.makeDirectory(path);
//}

for(r = start_series; r <= end_series; r++)
{
series_analyzed=" series_"+r;
run("Bio-Formats Importer", "open=["+input_file+"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT"+series_analyzed);

master_list = getList("image.titles");

image_title = master_list[0].replace("Merged", "");
image_title = image_title.replace(" ", "");
image_title = image_title.replace(".lif", "");
image_title = image_title.replace("-", "");

path = "C:/Users/savile/Desktop/Stardist Testing/";
if(!File.exists(path))
{
	File.makeDirectory(path);
}
path = "C:/Users/savile/Desktop/Stardist Testing/"+image_title+"/";
if(!File.exists(path))
{
	File.makeDirectory(path);
}

prob_thresh = 0.5;

for(l = 1; l <= 2; l++)
{
	selectWindow(master_list[0]);
	run("Duplicate...", "duplicate");
	run("Split Channels");
	list = getList("image.titles");
	for(j = 0; j < list.length; j++)
	{
		ident = list[j].substring(0,2);
		if(ident == "C"+l)
			title = list[j];
	}

	selectWindow(title);
	
	title = getTitle();
	getPixelSize(units, pixelWidth, pixelHeight);
	rolling_radius = 12.5/((pixelWidth+pixelHeight)/2);
	run("Subtract Background...", "rolling="+rolling_radius);
	run("Median...", "radius=2");
	
	panels = 4;
	image_width = getWidth();
	image_height = getHeight();
	
	sections = sqrt(panels);
	
	panel_size = image_width/sections;
	
	iteration = 0;
	panel = 0;
	while(iteration < sections)
	{
		for(i = 0; i < sections; i++)
		{
			selectWindow(title);
			makeRectangle(iteration*panel_size, i*panel_size, panel_size, panel_size);
			run("Copy");
			Roi.getBounds(x, y, width, height);
			panel++;
			newImage(title+" Panel "+panel, "8-bit black", width, height, 1);
			run("Paste");
			run("Select None");
		}
		iteration++;
	}
	
	selectWindow(title);
	run("Select None");
	getPixelSize(units, pixelWidth, pixelHeight);
	distance = (1/pixelHeight);
	run("Set Scale...", "distance="+distance+" known=1 pixel=1.000 unit=micron global");
	
	for(i = 0;i < panels;i++)
	{
		title = title+" Panel "+(i+1);
		selectWindow(title);
		run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D], args=['input':'"+title+"', 'modelChoice':'Versatile (fluorescent nuclei)', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+prob_thresh+"', 'nmsThresh':'0.3', 'outputType':'Both', 'nTiles':'64', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
		
		selectWindow(title);
		
		to_delete = newArray();
		run("Clear Results");
		run("Set Measurements...", "area mean shape median display redirect=None decimal=4");
		roiManager("Show All without labels");
		roiManager("Measure");
		initial_measure = nResults;
		
		if(l==1)
		{
			cArea = 40;
			cMean = 50;
			cCirc = 0.7;
			cSol = 0.99;
		}
		if(l==2)
		{
			cArea = 25;
			cMean = 60;
			cCirc = 0.6;
			cSol = 0.99;
		}
		if(l==3)
		{
			cArea = 25;
			cMean = 60;
			cCirc = 0.6;
			cSol = 0.99;
		}
		
		
		for(j = 0; j < nResults; j++)
		{
			mArea = getResult("Area", j);
			mMean = getResult("Mean", j);
			mCirc = getResult("Circularity", j);
			mSol = getResult("Solidity", j);
			if((mMean < cMean || mCirc < cCirc || mArea < cArea) && l == 1)
			{
				to_delete = Array.concat(to_delete, j);
			}
			if((mArea < cArea || mCirc < cCirc) && l == 2)
			{
				to_delete = Array.concat(to_delete, j);
			}
			if((mArea < cArea || mCirc < cCirc) && l == 3)
			{
				to_delete = Array.concat(to_delete, j);
			}
		}
	
		saveAs("Tiff", path+title.substring(0, 2)+" Panel "+(i+1)+" "+prob_thresh+" initial "+initial_measure);

		if(to_delete.length > 0)
		{
			roiManager("select", to_delete);
			roiManager("delete");
			roiManager("deselect");
		}
		
		run("Clear Results");
		roiManager("Measure");
		final_measure = nResults;
		roiManager("Show All without labels");
		saveAs("Tiff", path+title.substring(0, 2)+" - Panel - "+(i+1)+" Probability - "+prob_thresh+" Final Count - "+final_measure+" Area Constraint - "+cArea+" Circularity - "+cCirc+" Mean Constraint - "+cMean);
		roiManager("deselect");
		roiManager("delete");
		close("ROI Manager");
		close("Label Image");
		title = substring(title, 0, title.length-8);
	}
	run("Close");
	selectWindow(master_list[0]);
	close("\\Others");
	close("Results");
}
	close("*");
}
/*
 * 
run("Clear Results");
total_ROIs = roiManager("count");

for(i = 0; i < total_ROIs; i++)
{
	roiManager("select", i);
	Roi.setStrokeColor("red");
	roiManager("measure");
	
	mArea = getResult("Area", i);
	mMean = getResult("Mean", i);
	mCirc = getResult("Circ.", i);
	mSol = getResult("Solidity", i);
	
	if(mArea < 10 || mCirc < 0.7)
	{
		roiManager("select", i);
		Roi.setStrokeColor("green");
	}
}
to_delete = newArray();
run("Clear Results");
run("Set Measurements...", "area mean shape median display redirect=None decimal=4");
roiManager("Show All without labels");
roiManager("Measure");
initial_measure = nResults;

cArea = 20;
cMean = 60;
cCirc = 0.8;
cSol = 0.99;

for(j = 0; j < nResults; j++)
{
	mArea = getResult("Area", j);
	mMean = getResult("Mean", j);
	mCirc = getResult("Circ.", j);
	mSol = getResult("Solidity", j);
	
	if(mArea < cArea || mCirc < cCirc)
	{
		to_delete = Array.concat(to_delete, j);
	}
}

if(to_delete.length > 0)
{
//	Array.print(to_delete);
	roiManager("select", to_delete);
//	roiManager("delete");
	Roi.setStrokeColor("green");
	roiManager("deselect");
}

run("Clear Results");
roiManager("Measure");
final_measure = nResults;
roiManager("Show All without labels");
 */